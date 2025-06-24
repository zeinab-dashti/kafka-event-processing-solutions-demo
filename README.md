# Kafka Event-Processing Solutions Demo
A hands-on reference implementation showcasing four ways to build event-driven pipelines in Java/Spring:

1. **Spring Kafka** consumer → JPA sink
2. **Kafka Streams** real-time session aggregation
3. **Kafka Connect** JDBC sink & source connectors
4. **Apache Camel** multi-endpoint routing (optional)

---

## Features

- **Out-of-the-box JSON & Avro** serialization with Confluent Schema Registry
- **Spring Kafka** for simple consume-&-persist workflows
- **Kafka Streams** for stateful, session windowing
- **Kafka Connect** JDBC Sink/Source for zero-code integration with Postgres
- **Apache Camel** route for complex, multi-step enrichment & routing (disabled by default)

---

## Prerequisites

- Docker & Docker Compose (v1.27+)
- Java 17, Maven (if building locally)

---

## Getting Started

Build & Run

```
git clone https://github.com/zeinab-dashti/kafka-event-processing-solutions-demo.git
cd kafka-event-processing-solutions-demo
docker compose up --build -d
```

This will stand up:
* Zookeeper, Kafka, Schema Registry
* Postgres (_ordersdb_)
* Kafka Connect (JDBC sink & source)
* Spring Boot app (8080)


Wait for containers to become healthy
```
docker compose ps
```

If everything is configured correctly, you should see:
* Spring Kafka listener
  * Consumes every message from the _orders-topic_ and inserts it into a relational database’s _orders_ table.
* Kafka Streams topology
  * Processes _orders-topic_ events in real time, computes per-customer spending during active shopping sessions, and writes session summaries to the _order-session-aggregates-topic_ topic.
* JDBC Sink Connector
  * Automatically reads the summaries from _order-session-aggregates-topic_ topic and batch-flushes them into your target database table.
* (Optional) Apache Camel route
  * When enabled, consumes each order, enriches it via your Customer-Profile REST API, POSTS the enriched order to the Shipping-Service endpoint, and then sends a confirmation email via SMTP.

---

## Verifying Connectors

1. List available connector plugins
```
curl -s http://localhost:8083/connector-plugins | jq '
.[] | select(.class | test("Jdbc.*Connector"))'

 
{
   "class": "io.confluent.connect.jdbc.JdbcSinkConnector",
   "type": "sink",
   "version": "10.8.4"
}
{
   "class": "io.confluent.connect.jdbc.JdbcSourceConnector",
   "type": "source",
   "version": "10.8.4"
}
```

2. Check registered connector
```
curl http://localhost:8083/connectors

["order-session-aggregates-jdbc-sink"]%
```

If you see empty list then Post the connector:
```
curl -X POST http://localhost:8083/connectors \
  -H "Content-Type: application/json" \
  -d @connectors/order-session-aggregates-jdbc-sink.json
```

3. Inspect connector status
```
curl -s http://localhost:8083/connectors/order-session-aggregates-jdbc-sink/status | jq .

 
{
   "name": "order-session-aggregates-jdbc-sink",
   "connector": {
   "state": "RUNNING",
   "worker_id": "connect:8083"
},
"tasks": [
{
   "id": 0,
   "state": "RUNNING",
   "worker_id": "connect:8083"
}],
   "type": "sink"
}
```

If you instead get an error or see tasks in a FAILED state, grab the last fifty lines of the Connect logs and paste them here:
```
docker compose logs connect --tail=50
```

---

## Produce & Consume Messages

### Produce Sample Orders

The app’s ProducerMain emits sample Order events on startup. You can also publish custom events:
```
docker compose exec app \
  kafka-avro-console-producer \
  --broker-list kafka:9092 \
  --topic orders-topic \
  --property schema.registry.url=http://schema-registry:8081 \
  --property value.schema='{"type":"record","name":"Order", …}'
```

### Consume Raw Orders

```
docker compose exec schema-registry \
  kafka-avro-console-consumer \
  --bootstrap-server kafka:9092 \
  --topic orders-topic \
  --from-beginning \
  --max-messages 5 \
  --property schema.registry.url=http://schema-registry:8081
```

Generated log should have:
```
{"orderId":"d53aa850-bdcf-4f7a-8de0-8c815d4d7502","customerId":"cust-1","orderAmount":100,"orderDateTime":1750064965523}
{"orderId":"4afbc1c9-2d0b-4409-827a-3190ee224312","customerId":"cust-1","orderAmount":150,"orderDateTime":1750064968523}
{"orderId":"66e8ee9c-3dd7-4ba2-a480-5641274bf956","customerId":"cust-1","orderAmount":200,"orderDateTime":1750064975523}
```

---

## Inspect Aggregates & Database

* Streamed aggregates
```
docker compose exec schema-registry \
  kafka-avro-console-consumer \
  --bootstrap-server kafka:9092 \
  --topic order-session-aggregates-topic \
  --from-beginning \
  --max-messages 5 \
  --property schema.registry.url=http://schema-registry:8081
```

Generated log should have:
```
{"customerId":"cust-1","sessionStart":1750064965523,"sessionEnd":1750064975523,"totalAmount":450}
```

* Postgres table for Consumer and Streams output
```
docker compose exec postgres psql -U postgres -d ordersdb \
-c "SELECT * FROM order_session_aggregates;"

customerId |      sessionStart       |       sessionEnd        | totalAmount
------------+-------------------------+-------------------------+-------------
cust-1     | 2025-06-16 09:09:25.523 | 2025-06-16 09:09:35.523 |         450
(1 row)
```

```
docker compose exec postgres psql -U postgres -d ordersdb \
-c "SELECT * FROM orders;"

order_id                             | customer_id | order_amount |     order_date_time
-------------------------------------+-------------+--------------+-------------------------
0d6a3a1f-e3a6-4c07-884c-36c02e66e6fa | cust-1      |          100 | 2025-06-16 10:40:39.346
3f785342-ed75-4763-b578-d9111ddb1553 | cust-1      |          150 | 2025-06-16 10:40:42.346
2c04a8bb-3edb-48de-b85c-6c96227e6ec7 | cust-1      |          200 | 2025-06-16 10:40:49.346
(3 rows)
```

---

## Optional: Camel Route
By default, the Apache Camel integration is disabled.
