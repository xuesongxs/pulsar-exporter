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

package org.apache.pulsar.exporter.config;

import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "config")
@Data
public class PulsarConfig {

    private Logger logger = LoggerFactory.getLogger(PulsarConfig.class);

    @Value("${config.enableCollect}")
    private boolean enableCollect;

    @Value("${config.pulsar.zookeeper.zkStr}")
    private String zkStr;

    @Value("${config.pulsar.zookeeper.sessionTimeoutMs}")
    private int sessionTimeoutMs;

    @Value("${config.pulsar.broker.brokerZkPath}")
    private String brokerZkPath;

    @Value("${config.pulsar.broker.brokerHealthUri}")
    private String brokerHealthUri;

    @Value("${config.pulsar.broker.brokerBundleUri}")
    private String brokerBundleUri;

    @Value("${config.pulsar.bookie.bookieZkPath}")
    private String bookieZkPath;

    @Value("${config.pulsar.bookie.bookieDiskUri}")
    private String bookieDiskUri;
}
