package com.caseplan.adapter.out.queue;

import com.caseplan.application.port.out.QueuePort;
import org.junit.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class QueueConfigTest {

    @Test
    public void queuePort_providerSqs_returnsSqsAdapter() {
        QueueConfig config = new QueueConfig();
        ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);

        QueuePort port = config.queuePort("sqs", "https://sqs.example/queue", "us-east-2", provider);
        assertTrue(port instanceof SqsQueueAdapter);
    }

    @Test
    public void queuePort_providerRedis_returnsRedisAdapter() {
        QueueConfig config = new QueueConfig();
        ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mock(StringRedisTemplate.class));

        QueuePort port = config.queuePort("redis", "", "us-east-2", provider);
        assertTrue(port instanceof RedisQueueAdapter);
    }

    @Test(expected = IllegalStateException.class)
    public void queuePort_providerRedis_withoutTemplate_throws() {
        QueueConfig config = new QueueConfig();
        ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        config.queuePort("redis", "", "us-east-2", provider);
    }

    @Test
    public void queuePort_defaultProvider_returnsRedisAdapter() {
        QueueConfig config = new QueueConfig();
        ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mock(StringRedisTemplate.class));

        QueuePort port = config.queuePort(null, "", "us-east-2", provider);
        assertTrue(port instanceof RedisQueueAdapter);
    }
}
