package space.zeinab.demo.camel;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import space.zeinab.demo.avro.Order;
import space.zeinab.demo.model.OrderEntity;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Component
@ConditionalOnProperty(name = "camel.enabled", havingValue = "true", matchIfMissing = false)
public class OrderIntegrationRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        // 1) Consume Avro‐encoded Order from Kafka
        from("kafka:{{app.topic.name}}"
                + "?brokers={{spring.kafka.bootstrap-servers}}"
                + "&groupId={{spring.kafka.consumer.group-id}}"
                + "&valueDeserializer=io.confluent.kafka.serializers.KafkaAvroDeserializer"
                + "&schemaRegistryUrl={{spring.kafka.properties.schema.registry.url}}"
                + "&autoOffsetReset=earliest")
                .routeId("order-end-to-end")

                // 2) Map Avro Order → JPA entity and persist
                .process(new Processor() {
                    public void process(Exchange ex) {
                        Order avro = ex.getIn().getBody(Order.class);
                        OrderEntity entity = new OrderEntity(
                                avro.getOrderId().toString(),
                                avro.getCustomerId().toString(),
                                avro.getOrderAmount(),
                                LocalDateTime.ofInstant(avro.getOrderDateTime(), ZoneId.systemDefault())
                        );
                        ex.getIn().setBody(entity);
                    }
                })
                .to("jpa://space.zeinab.demo.model.OrderEntity?usePersist=true&flushOnSend=true")

                // 3) Enrich: call CRM and merge via AggregationStrategy
                .enrich()
                .simple("http://crm-service/api/customers/${body.customerId}")
                .aggregationStrategy(new AggregationStrategy() {
                    public Exchange aggregate(Exchange original, Exchange resource) {
                        OrderEntity order = original.getIn().getBody(OrderEntity.class);
                        CustomerProfile profile = resource.getIn().getBody(CustomerProfile.class);
                        order.setCustomerName(profile.getName());
                        order.setCustomerTier(profile.getTier());
                        order.setCustomerEmail(profile.getEmail());
                        original.getIn().setBody(order);
                        return original;
                    }
                })

                // 4) Ship: POST enriched order as JSON
                .marshal().json()
                .to("http://shipping-service/api/shipments?httpMethod=POST")

                // 5) Email confirmation
                .setHeader("subject", constant("Order Confirmation"))
                .setHeader("To", simple("${body.customerEmail}"))
                .setHeader("From", constant("noreply@yourcompany.com"))
                .setBody(simple(
                        "Hi ${body.customerName},\n\n" +
                                "Your order ${body.orderId} for amount ${body.orderAmount} has been processed.\n" +
                                "Thank you for shopping with us!"
                ))
                .to("smtp://{{mail.smtp.server}}?username={{mail.username}}&password={{mail.password}}")

                .log("Order ${body.orderId} processed end-to-end");
    }
}
