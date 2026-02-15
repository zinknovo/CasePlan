package com.caseplan.adapter.out.queue;

import com.caseplan.application.port.out.QueuePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.time.Duration;

@Configuration
public class QueueConfig {

    @Bean
    public QueuePort queuePort(
            @Value("${queue.provider:redis}") String provider,
            @Value("${queue.sqs.queue-url:}") String sqsQueueUrl,
            @Value("${queue.sqs.region:us-east-2}") String sqsRegion,
            ObjectProvider<StringRedisTemplate> redisTemplateProvider) {

        switch (provider == null ? "" : provider.trim().toLowerCase()) {
            case "sqs":
                SqsClient sqsClient = SqsClient.builder()
                        .region(Region.of(sqsRegion))
                        .httpClientBuilder(UrlConnectionHttpClient.builder())
                        .overrideConfiguration(ClientOverrideConfiguration.builder()
                                .apiCallTimeout(Duration.ofSeconds(8))
                                .apiCallAttemptTimeout(Duration.ofSeconds(5))
                                .build())
                        .build();
                return new SqsQueueAdapter(sqsClient, sqsQueueUrl);
            case "redis":
            default:
                StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
                if (redisTemplate == null) {
                    throw new IllegalStateException("Redis queue provider selected but StringRedisTemplate is unavailable");
                }
                return new RedisQueueAdapter(redisTemplate);
        }
    }
}
