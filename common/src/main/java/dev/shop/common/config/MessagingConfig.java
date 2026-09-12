package dev.shop.common.config;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.*;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
@Configuration
@EnableScheduling
@ConditionalOnProperty(name="commerce.messaging.enabled",havingValue="true")
public class MessagingConfig {
    @Bean KafkaAdmin.NewTopics topics() {
        return new KafkaAdmin.NewTopics(TopicBuilder.name("commerce.events").partitions(6).replicas(1).config("retention.ms","604800000").build());
    }
    @Bean DefaultErrorHandler errorHandler(KafkaTemplate<String,String> kafka,MeterRegistry meters) {
        var recoverer=new DeadLetterPublishingRecoverer(kafka,(record,ex)->new TopicPartition(record.topic()+"."+org.springframework.kafka.support.KafkaUtils.getConsumerGroupId()+".DLT",record.partition()));
        recoverer.setFailIfSendResultIsError(true);
        var backoff=new ExponentialBackOffWithMaxRetries(4); backoff.setInitialInterval(1000); backoff.setMultiplier(2); backoff.setMaxInterval(8000);
        var handler=new DefaultErrorHandler(recoverer,backoff);
        handler.setRetryListeners((record,ex,attempt)->meters.counter("commerce.kafka.consumer.failures").increment());
        return handler;
    }
    @Bean KafkaAdmin.NewTopics deadLetters(org.springframework.core.env.Environment environment) {
        String group=environment.getRequiredProperty("spring.application.name");
        return new KafkaAdmin.NewTopics(TopicBuilder.name("commerce.events."+group+".DLT").partitions(6).replicas(1).config("retention.ms","2592000000").build());
    }
}
