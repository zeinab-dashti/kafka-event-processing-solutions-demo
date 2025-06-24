package space.zeinab.demo.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import space.zeinab.demo.Repository.OrderRepository;
import space.zeinab.demo.avro.Order;
import space.zeinab.demo.model.OrderEntity;

import java.time.LocalDateTime;
import java.time.ZoneId;


@Component
@RequiredArgsConstructor
@Slf4j
public class OrderConsumer {

    private final OrderRepository orderRepository;

    @KafkaListener(
            id = "order-listener", // this is needed for lookup in the ProducerMain class
            topics = "${app.topic.name}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void listen(Order avroOrder) {
        log.info("Received order : {}", avroOrder);

        var entity = new OrderEntity(
                avroOrder.getOrderId().toString(),
                avroOrder.getCustomerId().toString(),
                avroOrder.getOrderAmount(),
                LocalDateTime.ofInstant(avroOrder.getOrderDateTime(), ZoneId.systemDefault())
        );
        orderRepository.save(entity);

        log.info("Persisted order {} into Postgres", avroOrder.getOrderId());
    }

}