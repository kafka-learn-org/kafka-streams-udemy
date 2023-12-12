package com.github.simplesteph.udemy.kafka.streams;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Named;
import org.apache.kafka.streams.kstream.Produced;

import java.io.IOException;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

public class ColorCountApp {
    public static void main(String[] args) throws IOException {
        Properties config = new Properties();
        config.put(StreamsConfig.APPLICATION_ID_CONFIG, "colors-application-5");
        config.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "127.0.0.1:9092");
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        config.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());

        Set<String> colors = new HashSet<>();
        colors.add("blue");
        colors.add("green");
        colors.add("red");

//        stephane,blue
//        john,green
//        stephane,red
//        alice,red

        //-stephane,blue
//        -john,green
//        -stephane,red
//        -alice,red

        StreamsBuilder builder = new StreamsBuilder();
        KStream<String, String> textLines = builder.stream("colors-input");

//        KTable<String, Long>
        KTable<String, String> colorCount = textLines
                .map(((key, value) -> KeyValue.pair(value.split(",")[0], value.split(",")[1])))
                .filter((key, value) -> colors.contains(value))
                .toTable();

        colorCount.toStream()
                .selectKey((key, color) -> color)
                .groupByKey()
                .count();

        String outputTopic = "colors-output";
        colorCount.toStream()
                .to(outputTopic, Produced.with(Serdes.String(), Serdes.String()));

        KafkaStreams streams = new KafkaStreams(builder.build(), config);
        streams.start();

        // shutdown hook to correctly close the streams application
        Runtime.getRuntime().addShutdownHook(new Thread(streams::close));

    }
}
