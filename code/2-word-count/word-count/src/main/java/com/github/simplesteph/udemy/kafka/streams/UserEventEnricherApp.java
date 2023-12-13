package com.github.simplesteph.udemy.kafka.streams;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.GlobalKTable;
import org.apache.kafka.streams.kstream.KStream;

import java.util.Properties;

public class UserEventEnricherApp {

    public static void main(String[] args) {
        Properties config = new Properties();
        config.put(StreamsConfig.APPLICATION_ID_CONFIG, "user-event-enricher-app");
        config.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        config.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());

        StreamsBuilder builder = new StreamsBuilder();

        GlobalKTable<String, String> usersGlobal = builder.globalTable("user-table");
        KStream<String, String> userPurchases = builder.stream("user-purchases");

        KStream<String, String> join = userPurchases.join(
                usersGlobal,
                (key, value) -> key,
                (userPurchase, userInfo) -> "Purchase=" + userPurchase + ",UserInfo=[" + userInfo + "]"
        );
        join.to("user-inner-join");

        KStream<String, String> leftJoin = userPurchases.leftJoin(
                usersGlobal,
                (key, value) -> key,
                (userPurchase, userInfo) -> {
                    // as this is a left join, userInfo can be null
                    if (userInfo != null) {
                        return "Purchase=" + userPurchase + ",UserInfo=[" + userInfo + "]";
                    } else {
                        return "Purchase=" + userPurchase + ",UserInfo=null";
                    }
                }
        );
        leftJoin.to("user-left-join");


        KafkaStreams streams = new KafkaStreams(builder.build(), config);
        streams.cleanUp(); // only do this in dev - not in prod
        streams.start();

        Runtime.getRuntime().addShutdownHook(new Thread(streams::close));

    }
}
