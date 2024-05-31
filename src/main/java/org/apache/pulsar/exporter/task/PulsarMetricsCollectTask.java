/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.pulsar.exporter.task;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.bookkeeper.client.BookKeeperAdmin;
import org.apache.bookkeeper.conf.ClientConfiguration;
import org.apache.bookkeeper.discover.BookieServiceInfo;
import org.apache.bookkeeper.net.BookieId;
import org.apache.commons.lang3.StringUtils;
import org.apache.curator.framework.CuratorFramework;
import org.apache.pulsar.exporter.config.CollectClientMetricExecutorConfig;
import org.apache.pulsar.exporter.config.PulsarConfig;
import org.apache.pulsar.exporter.entity.PulsarTopicStats;
import org.apache.pulsar.exporter.metrics.BookieDiskMetric;
import org.apache.pulsar.exporter.metrics.BrokerBundleMetric;
import org.apache.pulsar.exporter.metrics.BrokerHealthMetric;
import org.apache.pulsar.exporter.service.PulsarMetricsService;
import org.apache.pulsar.exporter.util.HttpUtil;
import org.apache.pulsar.exporter.util.JsonUtil;
import org.apache.pulsar.exporter.util.ZKUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class PulsarMetricsCollectTask {
    private final static Logger logger = LoggerFactory.getLogger(PulsarMetricsCollectTask.class);

    @Resource
    private PulsarConfig config;
    @Resource
    @Qualifier("collectClientMetricExecutor")
    private ExecutorService collectClientMetricExecutor;
    @Resource
    private PulsarMetricsService metricsService;

    private List<String> brokerList = new ArrayList<>();

    private List<String> brokerHealthUrlList = new ArrayList<>();

    private List<String> brokerBundleUrlList = new ArrayList<>();

    private List<String> bookieDiskUrlList = new ArrayList<>();

    private BlockingQueue<Runnable> collectClientTaskBlockQueue;

    private final Pattern pattern = Pattern.compile(" \\d+");;

    @Bean(name = "collectClientMetricExecutor")
    private ExecutorService collectClientMetricExecutor(CollectClientMetricExecutorConfig collectClientMetricExecutorConfig) {
        collectClientTaskBlockQueue = new LinkedBlockingDeque<Runnable>(collectClientMetricExecutorConfig.getQueueSize());
        ExecutorService executorService = new ClientMetricCollectorFixedThreadPoolExecutor(
                collectClientMetricExecutorConfig.getCorePoolSize(),
                collectClientMetricExecutorConfig.getMaximumPoolSize(),
                collectClientMetricExecutorConfig.getKeepAliveTime(),
                TimeUnit.MILLISECONDS,
                this.collectClientTaskBlockQueue,
                new ThreadFactory() {
                    private final AtomicLong threadIndex = new AtomicLong(0);

                    @Override
                    public Thread newThread(Runnable r) {
                        return new Thread(r, "collectClientMetricThread_" + this.threadIndex.incrementAndGet());
                    }
                },
                new ThreadPoolExecutor.DiscardOldestPolicy()
        );
        return executorService;
    }

    @PostConstruct
    public void init() throws Exception {
        brokerList.clear();
        brokerBundleUrlList.clear();
        brokerHealthUrlList.clear();
        bookieDiskUrlList.clear();

        CuratorFramework client = ZKUtil.clientConnect(config.getZkStr(), config.getSessionTimeoutMs());
        client.start();
        List<String> brokerList = ZKUtil.getNodeChildren(client, config.getBrokerZkPath());
        if (brokerList != null && !brokerList.isEmpty()) {
            for (String broker : brokerList) {
                String brokerHealthUrl = "http://" + broker + "/" + config.getBrokerHealthUri();
                brokerHealthUrlList.add(brokerHealthUrl);
                brokerBundleUrlList.add("http://" + broker + "/" + config.getBrokerBundleUri());
            }
        }

        List<String> bookieIdList = ZKUtil.getNodeChildren(client, config.getBookieZkPath());
        List<String> bookieList = new ArrayList<>();
        if (bookieIdList != null && !bookieIdList.isEmpty()) {
            BookKeeperAdmin bkAdmin = new BookKeeperAdmin(new ClientConfiguration().setZkServers(config.getZkStr()).setZkTimeout(config.getSessionTimeoutMs()));
            for (String bookieId : bookieIdList) {
                BookieServiceInfo bookieServiceInfo = bkAdmin.getBookieServiceInfo(BookieId.parse(bookieId));
                BookieServiceInfo.Endpoint endpoint = bookieServiceInfo.getEndpoints().stream()
                        .filter(e -> Objects.equals(e.getProtocol(), "http"))
                        .findFirst()
                        .get();
                String bookieHost = bookieId.substring(0, bookieId.indexOf(":"));
                String bookieDiskUrl = "http://" + bookieHost + ":" + endpoint.getPort() + "/" + config.getBookieDiskUri();
                bookieList.add(bookieHost + ":" + endpoint.getPort());
                bookieDiskUrlList.add(bookieDiskUrl);
            }
        }

        logger.info("brokerList: {}", brokerList.toString());
        logger.info("bookieList: {}", bookieList.toString());
        logger.info("brokerHealthUrlList: {}", brokerHealthUrlList.toString());
        logger.info("brokerBundleUrlList: {}", brokerBundleUrlList.toString());
        logger.info("bookieDiskUrlList: {}", bookieDiskUrlList.toString());
    }

    @Scheduled(cron = "${task.collectBrokerStats.cron}")
    public void collectBrokerStats() {
        if (!config.isEnableCollect()) {
            return;
        }
        logger.info("pulsar collection task starting....");
        long start = System.currentTimeMillis();

        // 遍历访问所有的broker服务节点
        // 取broker健康检查指标
        List<BrokerHealthMetric> brokerHealthMetrics = new ArrayList<>();
        for (String brokerHealthUrl : brokerHealthUrlList) {
            String brokerHealth = HttpUtil.getMetrics(brokerHealthUrl);
            int flag = 0;
            if ("ok".equals(brokerHealth)) {
                flag = 1;
            }
            BrokerHealthMetric brokerHealthMetric = new BrokerHealthMetric();
            String urlPrefix = "http://";
            int startIndex = brokerHealthUrl.indexOf(urlPrefix);
            int endIndex = brokerHealthUrl.indexOf("/admin");

            String brokerId = brokerHealthUrl.substring(startIndex + urlPrefix.length(), endIndex);
            brokerHealthMetric.setBroker(brokerId);
            brokerHealthMetric.setHealth(flag);
            brokerHealthMetrics.add(brokerHealthMetric);
            logger.info("brokerHealth: {}", brokerHealth);
            logger.info("brokerHealthMetric: {}", brokerHealthMetric);
        }
        metricsService.getCollector().addBrokerHealthMetric(brokerHealthMetrics);

        // 取broker bundles指标
        List<BrokerBundleMetric> brokerBundleMetrics = new ArrayList<>();
        for (String brokerBundleUrl : brokerBundleUrlList) {
            String bundles = HttpUtil.getMetrics(brokerBundleUrl);
            logger.info("brokerBundleUrl: {}, bundles: {}", brokerBundleUrl, bundles);
            Set<String> bundleNames = new HashSet<>();
            if (StringUtils.isNotBlank(bundles)) {
                Gson gson = new Gson();
                HashMap<String, HashMap<String, HashMap<String, HashMap<String, PulsarTopicStats>>>> brokerStatsTopicEntity = gson.fromJson(bundles,
                        new TypeToken<HashMap<String, HashMap<String, HashMap<String, HashMap<String, PulsarTopicStats>>>>>() {
                        }.getType());
                brokerStatsTopicEntity.forEach((namespace, namespaceStats) -> {
                    namespaceStats.forEach((bundle, bundleStats) -> {
                        logger.info("namespace:{}, bundle: {}, bundleStats: {}", namespace, bundle, bundleStats);
                        bundleNames.add(bundle);
                    });
                });
            }
            BrokerBundleMetric brokerBundleMetric = new BrokerBundleMetric();
            String urlPrefix = "http://";
            int startIndex = brokerBundleUrl.indexOf(urlPrefix);
            int endIndex = brokerBundleUrl.indexOf("/admin");

            String brokerId = brokerBundleUrl.substring(startIndex + urlPrefix.length(), endIndex);
            brokerBundleMetric.setBroker(brokerId);
            brokerBundleMetric.setBundles(bundleNames.size());
            brokerBundleMetrics.add(brokerBundleMetric);
            logger.info("brokerBundleMetric: {}", brokerBundleMetric);
        }
        metricsService.getCollector().addBrokerBundleMetric(brokerBundleMetrics);

        // 遍历访问所有的bookie服务节点, 只要有一个bookie服务返回成功就可以获取到所有的bookie节点的磁盘使用信息
        // 取bookie磁盘使用率指标
        List<BookieDiskMetric> bookieDiskMetrics = new ArrayList<>();
        for (String bookieDiskUrl : bookieDiskUrlList) {
            String listBookieInfo = HttpUtil.getMetrics(bookieDiskUrl);
            if (StringUtils.isNotEmpty(listBookieInfo)) {
                Gson gson = new Gson();
                Map<String, String> listBookies = gson.fromJson(listBookieInfo, new TypeToken<Map<String, String>>() {}.getType());
                for (Map.Entry<String, String> entry : listBookies.entrySet()) {
                    String bookieId = entry.getKey();
                    if (bookieId.contains("ClusterInfo")) {
                        continue;
                    }
                    Matcher matcher = pattern.matcher(listBookies.get(entry.getKey()));
                    List<String> storageSizeList = new ArrayList<>();
                    while (matcher.find()) {
                        String res = matcher.group();
                        storageSizeList.add(res.trim());
                    }
                    BookieDiskMetric bookieDiskMetric = new BookieDiskMetric();
                    bookieDiskMetric.setBookie(bookieDiskUrl.substring("http://".length(), bookieDiskUrl.indexOf("/api")));
                    bookieDiskMetric.setFree(Long.valueOf(storageSizeList.get(0)));
                    bookieDiskMetric.setTotal(Long.valueOf(storageSizeList.get(1)));
                    bookieDiskMetric.setUsed(bookieDiskMetric.getTotal() - bookieDiskMetric.getFree());
                    bookieDiskMetrics.add(bookieDiskMetric);
                }
                logger.info("bookieDiskMetrics: {}", bookieDiskMetrics);
                metricsService.getCollector().addBookieDiskMetrics(bookieDiskMetrics);
                break;
            }
        }

        logger.info("pulsar collection task finished...." + (System.currentTimeMillis() - start));
    }

}
