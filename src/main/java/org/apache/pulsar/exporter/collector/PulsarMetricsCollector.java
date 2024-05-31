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

package org.apache.pulsar.exporter.collector;

import io.prometheus.client.Collector;
import io.prometheus.client.GaugeMetricFamily;
import org.apache.pulsar.exporter.metrics.BookieDiskMetric;
import org.apache.pulsar.exporter.metrics.BrokerBundleMetric;
import org.apache.pulsar.exporter.metrics.BrokerHealthMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PulsarMetricsCollector extends Collector {

    private final static Logger logger = LoggerFactory.getLogger(PulsarMetricsCollector.class);

    private List<BrokerHealthMetric> brokerHealthMetrics = new ArrayList<>();

    private List<BrokerBundleMetric> brokerBundleMetrics = new ArrayList<>();

    private List<BookieDiskMetric> bookieDiskMetrics = new ArrayList<>();

    @Override
    public List<MetricFamilySamples> collect() {
        List<MetricFamilySamples> mfs = collectMetrics();
        return mfs;
    }

    private List<MetricFamilySamples> collectMetrics() {
        logger.info("collectMetrics brokerHealthMetrics: {}", brokerHealthMetrics.toString());
        List<MetricFamilySamples> mfs = new ArrayList<>();

        List<String> brokerLabelNames = Arrays.asList("broker");
        // broker health metrics
        for (BrokerHealthMetric brokerHealthMetric : brokerHealthMetrics) {
            List<String> labelValues = Arrays.asList(brokerHealthMetric.getBroker());
            GaugeMetricFamily health = new GaugeMetricFamily("broker_health", "broker_health", brokerLabelNames);
            health.addMetric(labelValues, brokerHealthMetric.getHealth());
            mfs.add(health);
        }

        // broker bunlde metrics
        for (BrokerBundleMetric brokerBundleMetric : brokerBundleMetrics) {
            List<String> labelValues = Arrays.asList(brokerBundleMetric.getBroker());
            GaugeMetricFamily bundles = new GaugeMetricFamily("broker_bundles", "broker_bundles", brokerLabelNames);
            bundles.addMetric(labelValues, brokerBundleMetric.getBundles());
            mfs.add(bundles);
        }

        // bookie metrics
        List<String> bookieLabelNames = Arrays.asList("bookie");
        for (BookieDiskMetric bookieDiskMetric : bookieDiskMetrics) {
            List<String> labelValues = Arrays.asList(bookieDiskMetric.getBookie());
            GaugeMetricFamily total = new GaugeMetricFamily("bookie_disk_total", "bookie_disk_total", bookieLabelNames);
            total.addMetric(labelValues, bookieDiskMetric.getTotal());
            mfs.add(total);

            GaugeMetricFamily used = new GaugeMetricFamily("bookie_disk_used", "bookie_disk_used", bookieLabelNames);
            used.addMetric(labelValues, bookieDiskMetric.getUsed());
            mfs.add(used);

            GaugeMetricFamily free = new GaugeMetricFamily("bookie_disk_free", "bookie_disk_free", bookieLabelNames);
            free.addMetric(labelValues, bookieDiskMetric.getFree());
            mfs.add(free);
        }

        return mfs;
    }

    public void addBrokerHealthMetric(List<BrokerHealthMetric> brokerHealthMetrics) {
        this.brokerHealthMetrics.clear();
        this.brokerHealthMetrics.addAll(brokerHealthMetrics);
    }

    public void addBrokerBundleMetric(List<BrokerBundleMetric> brokerBundleMetrics) {
        this.brokerBundleMetrics.clear();
        this.brokerBundleMetrics.addAll(brokerBundleMetrics);
    }

    public void addBookieDiskMetrics(List<BookieDiskMetric> bookieDiskMetrics) {
        this.bookieDiskMetrics.clear();
        this.bookieDiskMetrics.addAll(bookieDiskMetrics);
    }

}