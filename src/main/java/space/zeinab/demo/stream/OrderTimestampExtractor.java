package space.zeinab.demo.stream;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.streams.processor.TimestampExtractor;
import space.zeinab.demo.avro.Order;

import java.time.ZoneId;

public class OrderTimestampExtractor implements TimestampExtractor {
    @Override
    public long extract(ConsumerRecord<Object, Object> record, long partitionTime) {
        if (record.value() instanceof Order) {
            return ((Order) record.value())
                    .getOrderDateTime()
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli();
        }
        return record.timestamp();
    }
}