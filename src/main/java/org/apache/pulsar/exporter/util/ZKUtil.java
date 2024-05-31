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

package org.apache.pulsar.exporter.util;

import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.api.BackgroundCallback;
import org.apache.curator.framework.api.CuratorEvent;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.data.Stat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ZKUtil {

    /**
     * @param zkStr
     * @return
     */
    public static CuratorFramework clientConnect(String zkStr, int sessionTimeoutMs) {
        RetryPolicy retryPolicy = new ExponentialBackoffRetry(100, 3);
        CuratorFramework client = CuratorFrameworkFactory.builder()
                .connectString(zkStr)
                .sessionTimeoutMs(sessionTimeoutMs)
                .connectionTimeoutMs(sessionTimeoutMs)
                .retryPolicy(retryPolicy)
                .build();
        return client;
    }

    /**
     * createNode
     * @param client
     * @param path
     * @param data
     * @throws Exception
     */
    public static void createNode(CuratorFramework client, String path, String data) throws Exception {
        client.create()
                .withMode(CreateMode.PERSISTENT)
                .forPath(path, data.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * syncCreateNode
     * @param client
     * @param path
     * @param data
     */
    public static void syncCreateNode(CuratorFramework client, String path, String data) {
        try {
            client.create()
                    .creatingParentsIfNeeded()
                    .withMode(CreateMode.PERSISTENT)
                    .forPath(path, data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * asyncCreateNode
     * @param client
     * @param path
     * @param data
     * @throws Exception
     */
    public static void asyncCreateNode(CuratorFramework client, String path, String data) throws Exception {
        client.create()
                .creatingParentsIfNeeded()
                .withMode(CreateMode.PERSISTENT)
                .inBackground(new BackgroundCallback() {
                    @Override
                    public void processResult(CuratorFramework client, CuratorEvent event) throws Exception {
                        System.out.println("asyncCreateNode=========" + event.getName() + ":" + event.getPath());
                    }
                })
                .forPath(path, data.getBytes(StandardCharsets.UTF_8));
        TimeUnit.MILLISECONDS.sleep(100);
    }

    /**
     * getNodeData
     * @param client
     * @param path
     * @return
     * @throws Exception
     */
    public static String getNodeData(CuratorFramework client, String path) throws Exception {
        byte[] data = client.getData().storingStatIn(new Stat()).forPath(path);
        return new String(data, StandardCharsets.UTF_8);
    }

    /**
     * getNodeChildren
     * @param client
     * @param path
     * @return
     * @throws Exception
     */
    public static List<String> getNodeChildren(CuratorFramework client, String path) throws Exception {
        List<String> children = client.getChildren().forPath(path);
        return children;
    }

    /**
     *
     * @param client
     * @param path
     * @param data
     */
    public static void updateNodeData(CuratorFramework client, String path, String data) throws Exception {
        Stat stat = client.setData()
                //.withVersion(-1) //可以根据需要使用
                .forPath(path, data.getBytes(StandardCharsets.UTF_8));
        System.out.println("updateNodeData path, stat：" + stat);
    }

    /**
     * @param client
     * @param path
     */
    public static void deleteNode(CuratorFramework client, String path) throws Exception {
        client.delete().forPath(path);
    }

    /**
     * 级联删除该节点以及子孙节点
     *
     * @param client
     * @param path
     */
    public static void deleteChildrenIfNeeded(CuratorFramework client, String path) throws Exception {
        client.delete()
                .guaranteed()
                .deletingChildrenIfNeeded()
                .forPath(path);
    }

    /**
     * @param client
     * @param nodePath
     * @throws Exception
     */
    public static boolean checkNodeExists(CuratorFramework client, String nodePath) throws Exception {
        Stat stat = client.checkExists().forPath(nodePath);
        return null != stat;
    }

}
