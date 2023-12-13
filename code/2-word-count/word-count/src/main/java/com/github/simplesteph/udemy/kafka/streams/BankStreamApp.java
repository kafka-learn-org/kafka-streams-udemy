package com.github.simplesteph.udemy.kafka.streams;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Produced;

import java.io.IOException;
import java.util.Properties;

public class BankStreamApp {
    public static void main(String[] args) throws IOException {
        Properties config = new Properties();
        config.put(StreamsConfig.APPLICATION_ID_CONFIG, "bank-application-2");
        config.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "127.0.0.1:9092");
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(StreamsConfig.PROCESSING_GUARANTEE_CONFIG, StreamsConfig.EXACTLY_ONCE_V2);
        config.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        config.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());



        StreamsBuilder builder = new StreamsBuilder();
        KStream<String, String> transactions = builder.stream("bank-transactions");

        ObjectMapper objectMapper = new ObjectMapper();

//        KTable<String, Long>
        KTable<String, String> bankSum = transactions
                .mapValues(transaction -> parseTransactionAmount(transaction, objectMapper))
                .peek((key, value) -> System.out.println("-------------------- " + key + "|" + value))
                .groupByKey(Grouped.valueSerde(Serdes.Integer()))
                .reduce(Integer::sum)
                .mapValues((readOnlyKey, value) -> {
                    return String.valueOf(value);
                });

        bankSum.toStream()
                .to("bank-transactions-sum", Produced.with(Serdes.String(), Serdes.String()));

        KafkaStreams streams = new KafkaStreams(builder.build(), config);
        streams.cleanUp();
        streams.start();

        Runtime.getRuntime().addShutdownHook(new Thread(streams::close));

    }

    private static int parseTransactionAmount(String transaction, ObjectMapper objectMapper) {
        try {
            JsonNode node = objectMapper.readTree(transaction);
            return node.get("amount").asInt();
        } catch (JsonProcessingException e) {
            System.err.println(e);
            return 0;
        }

    }
}
