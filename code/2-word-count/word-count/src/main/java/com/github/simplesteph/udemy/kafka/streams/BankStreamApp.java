package com.github.simplesteph.udemy.kafka.streams;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Produced;

import java.io.IOException;
import java.time.Instant;
import java.util.Properties;

public class BankStreamApp {
    public static void main(String[] args) throws IOException {
        Properties config = new Properties();
        config.put(StreamsConfig.APPLICATION_ID_CONFIG, "bank-application-3");
        config.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "127.0.0.1:9092");
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(StreamsConfig.PROCESSING_GUARANTEE_CONFIG, StreamsConfig.EXACTLY_ONCE_V2);
        config.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        config.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());

        StreamsBuilder builder = new StreamsBuilder();
        KStream<String, String> transactions = builder.stream("bank-transactions");


        ObjectNode initial = JsonNodeFactory.instance.objectNode();
        initial.put("count", 0);
        initial.put("balance", 0);
        initial.put("time", Instant.MIN.toString());

        ObjectMapper objectMapper = new ObjectMapper();

//        KTable<String, Long>
        KTable<String, String> bankSum = transactions
                //.mapValues(transaction -> parseJson(transaction, objectMapper))
//                .peek((key, value) -> System.out.println("-------------------- " + key + "|" + value))
                .groupByKey()
                .aggregate(
                        () -> initial.toString(),
                        ((key, transaction, balance) -> newBalance(transaction, balance, objectMapper))
                )
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

    private static ObjectNode parseJson(String transaction, ObjectMapper objectMapper) {
        try {
            JsonNode node = objectMapper.readTree(transaction);
            return node.deepCopy();
        } catch (JsonProcessingException e) {
            System.err.println(e);
            return JsonNodeFactory.instance.objectNode();
        }
    }

    private static String newBalance(String transaction1, String balance1, ObjectMapper objectMapper) {
        ObjectNode transaction = parseJson(transaction1, objectMapper);
        ObjectNode balance = parseJson(balance1, objectMapper);

        ObjectNode newBalance = JsonNodeFactory.instance.objectNode();
        newBalance.put("count", balance.get("count").asInt() + 1);
        newBalance.put("balance", balance.get("balance").asInt() + transaction.get("amount").asInt());
        long balanceTime = Instant.now().toEpochMilli(); //Instant.parse(balance.get("time").asText()).toEpochMilli();
        long transactionTime =  Instant.now().toEpochMilli(); // Instant.parse(transaction.get("time").asText()).toEpochMilli();
        Instant newTime = Instant.ofEpochMilli(Math.max(balanceTime, transactionTime));
        newBalance.put("time", newTime.toString());

        return newBalance.toString();
    }
}
