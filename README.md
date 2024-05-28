# Apache Pulsar Exporter for Prometheus
Each component of Apache Pulsar has its own metrics in /metrics, but some metrics have outputs in RestAPI or JMX, but not in /metrics, Apache Pulsar Exporter is designed to convert metrics from RestAPI or JMX to prometheus format metrics. Apache Pulsar Exporter is a metric used to extend pulsar and supports custom pulsar metrics. Using pulsar exporter requires independent deployment of a Prometheus job.

Configuration
---

This image is configurable using different properties, see ``application.properties`` for a configuration example.

| name                               | Default        | Description                                          |
|------------------------------------|----------------|------------------------------------------------------|
| `config.pulsar.zookeeper.zkStr`         | 127.0.0.1:2181 | Zookeeper address  for  pulsar cluster               |
| `config.pulsar.zookeeper.sessionTimeoutMs` | 30000          | Zookeeper session timeout                                                    |
| `server.port`                      | 8080           | Address to listen on for web interface and telemetry |


Build
-------

### Build Binary

```shell
mvn clean install
```

### Build Docker Image

```shell
mvn package -Dmaven.test.skip=true docker:build
```

Run
---

### Run Binary

```shell
java -jar target/pulsar-exporter-0.0.1-exec.jar
```

Metrics
-------

Documents about exposed Prometheus metrics.

**Metrics output example**
```txt
broker_health{broker="127.0.0.1:8080",} 1.0
broker_bundles{broker="127.0.0.1:8080",} 5.0
bookie_disk_total{bookie="127.0.0.1:9000",} 160978440192
bookie_disk_used{bookie="127.0.0.1:9000",} 8800108544
bookie_disk_free{bookie="127.0.0.1:9000",} 152178331648
```

Grafana Dashboard
-------
Add these custom metrics to the Pulsar dashboard, and you can also add custom dashboards.
