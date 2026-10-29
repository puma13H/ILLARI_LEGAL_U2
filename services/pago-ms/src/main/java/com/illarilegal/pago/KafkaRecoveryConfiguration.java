package com.illarilegal.pago;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.config.*;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.*;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.config.TopicBuilder;
import org.apache.kafka.clients.admin.NewTopic;
import io.micrometer.core.instrument.MeterRegistry;

@Configuration
public class KafkaRecoveryConfiguration {
    @Bean public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String,String> kafka, MeterRegistry metrics) {
        for(String name:java.util.List.of("fallidos","reintentados","reprocesados")) metrics.counter("illari.kafka."+name,"servicio","pago");
        var recoverer=new DeadLetterPublishingRecoverer(kafka,(record,error)->new TopicPartition(record.topic()+".DLT",record.partition()));
        recoverer.setFailIfSendResultIsError(true);
        recoverer.setWaitForSendResultTimeout(java.time.Duration.ofSeconds(5));
        var backoff=new ExponentialBackOffWithMaxRetries(3);
        backoff.setInitialInterval(250); backoff.setMultiplier(2); backoff.setMaxInterval(2000);
        var handler=new DefaultErrorHandler((record,error)->{
            recoverer.accept(record,error);
            // Log neither rejected value nor exception messages (which may embed sensitive payloads).
            try(var scope=eventScope(record.value())) {
                org.slf4j.LoggerFactory.getLogger("ILLARI_OBS").warn("kafka_dlt topic={} partition={} offset={}",record.topic(),record.partition(),record.offset());
            }
            metrics.counter("illari.kafka.fallidos","servicio","pago").increment();
        },backoff);
        handler.addNotRetryableExceptions(IllegalArgumentException.class);
        handler.setAckAfterHandle(true);
        handler.setRetryListeners((record,error,attempt)-> {
            if(attempt>1) metrics.counter("illari.kafka.reintentados","servicio","pago").increment();
        });
        return handler;
    }
    private static Correlation eventScope(Object payload) {
        try {var e=EventEnvelope.parse(String.valueOf(payload));return new Correlation(e.path("traceId").asText(null),EventEnvelope.id(e));}
        catch(Exception ignored){return new Correlation(null,null);}
    }
    @Bean public ConcurrentKafkaListenerContainerFactory<String,String> kafkaListenerContainerFactory(
            ConsumerFactory<String,String> consumerFactory, DefaultErrorHandler handler,
            @Value("${spring.kafka.listener.auto-startup:true}") boolean auto) {
        var factory=new ConcurrentKafkaListenerContainerFactory<String,String>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(handler);
        factory.setAutoStartup(auto);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        return factory;
    }
    @Bean public ConcurrentKafkaListenerContainerFactory<String,String> deadLetterContainerFactory(ConsumerFactory<String,String> consumerFactory,
            @Value("${spring.kafka.listener.auto-startup:true}") boolean auto) {
        var factory=new ConcurrentKafkaListenerContainerFactory<String,String>();
        factory.setConsumerFactory(consumerFactory);
        factory.setAutoStartup(auto);
        factory.setCommonErrorHandler(new DefaultErrorHandler(new org.springframework.util.backoff.FixedBackOff(
                1000,org.springframework.util.backoff.FixedBackOff.UNLIMITED_ATTEMPTS)));
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        return factory;
    }
    @Bean public NewTopic businessInput(@Value("${illari.kafka.partitions:1}") int n) {
        return TopicBuilder.name("illari.orden.creada").partitions(n).replicas(1).build();
    }
    @Bean public NewTopic businessDlt(@Value("${illari.kafka.partitions:1}") int n) {
        return TopicBuilder.name("illari.orden.creada.DLT").partitions(n).replicas(1).build();
    }
}

