package space.zeinab.demo.stream;

import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaStreams;
import space.zeinab.demo.avro.Order;
import space.zeinab.demo.avro.SessionSummary;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;

@Configuration
@EnableKafkaStreams
public class OrderStreamsConfig {

    @Value("${app.topic.name}")
    private String ordersTopic;

    @Value("${app.topic.session-aggregates}")
    private String aggregatesTopic;

    @Value("${spring.kafka.properties.schema.registry.url}")
    private String schemaRegistryUrl;

    @Value("${stream.session.inactivity-gap.minutes}")
    private long inactivityGap;

    @Value("${stream.session.grace-period.minutes}")
    private long gracePeriod;

    @Bean
    public KStream<String, Order> kStream(StreamsBuilder builder) {
        Map<String, String> serdeConfig = Collections.singletonMap(
                "schema.registry.url", schemaRegistryUrl
        );
        SpecificAvroSerde<Order> orderSerde = new SpecificAvroSerde<>();
        orderSerde.configure(serdeConfig, false);
        SpecificAvroSerde<SessionSummary> summarySerde = new SpecificAvroSerde<>();
        summarySerde.configure(serdeConfig, false);

        KStream<String, Order> orders = builder.stream(
                ordersTopic,
                Consumed.with(Serdes.String(), orderSerde)
                        .withTimestampExtractor(new OrderTimestampExtractor())
        );

        KGroupedStream<String, Order> byCustomer = orders.groupBy(
                (String key, Order value) -> value.getCustomerId().toString(),
                Grouped.<String, Order>with(Serdes.String(), orderSerde)
        );

        SessionWindowedKStream<String, Order> sessions =
                byCustomer.windowedBy(
                        SessionWindows.ofInactivityGapAndGrace(
                                Duration.ofMinutes(5),
                                Duration.ofMinutes(1))
                );

        KTable<Windowed<String>, Long> totals = sessions.aggregate(
                // 1) initializer
                () -> 0L,

                // 2) aggregator
                (String custId, Order o, Long runningTotal) ->
                        runningTotal + o.getOrderAmount(),

                // 3) merger
                (String custId, Long aggOne, Long aggTwo) ->
                        aggOne + aggTwo,

                // 4) materialized
                Materialized.with(Serdes.String(), Serdes.Long())
        );


        KStream<String, SessionSummary> summaryStream = totals.toStream()
                .map((Windowed<String> windowedKey, Long total) -> {
                    Instant start = Instant.ofEpochMilli(windowedKey.window().start());
                    Instant end = Instant.ofEpochMilli(windowedKey.window().end());

                    SessionSummary summary = SessionSummary.newBuilder()
                            .setCustomerId(windowedKey.key())
                            .setSessionStart(start)
                            .setSessionEnd(end)
                            .setTotalAmount(total)
                            .build();

                    return KeyValue.pair(windowedKey.key(), summary);
                });

        summaryStream.to(aggregatesTopic, Produced.with(Serdes.String(), summarySerde));

        return orders;
    }
}
