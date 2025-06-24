package space.zeinab.demo.producer;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import space.zeinab.demo.avro.Order;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ProducerMain implements SmartLifecycle {

    private final KafkaTemplate<String, Order> kafkaTemplate;
    private final KafkaListenerEndpointRegistry registry;

    @Value("${app.topic.name}")
    private String topicName;

    private boolean isRunning = false;

    @Override
    public void start() {
        var container = registry.getListenerContainer("order-listener");

        if (container != null) {
            try {
                // Wait for full consumer readiness (thread + assignment)
                while (!container.isRunning() || container.getAssignedPartitions().isEmpty()) {
                    Thread.sleep(200);
                }

                System.out.println("Kafka listener is fully ready. Now sending events.");

                List<Order> samples = List.of(
                        Order.newBuilder()
                                .setOrderId(UUID.randomUUID().toString())
                                .setCustomerId("cust-1")
                                .setOrderAmount(100L)
                                .setOrderDateTime(Instant.now())
                                .build(),
                        Order.newBuilder()
                                .setOrderId(UUID.randomUUID().toString())
                                .setCustomerId("cust-1")
                                .setOrderAmount(150L)
                                .setOrderDateTime(Instant.now().plusSeconds(3))
                                .build(),
                        Order.newBuilder()
                                .setOrderId(UUID.randomUUID().toString())
                                .setCustomerId("cust-1")
                                .setOrderAmount(200L)
                                .setOrderDateTime(Instant.now().plusSeconds(10))
                                .build()
                );

                samples.forEach(order ->
                        kafkaTemplate.send(topicName, order.getOrderId().toString(), order)
                                .whenComplete((result, ex) -> {
                                    if (ex != null) {
                                        System.err.println("Failed to send: " + order);
                                        ex.printStackTrace();
                                    } else {
                                        System.out.println("Sent: " + order);
                                    }
                                })
                );

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        isRunning = true;
    }

    @Override
    public void stop() {
        isRunning = false;
    }

    @Override
    public boolean isRunning() {
        return isRunning;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }
}
