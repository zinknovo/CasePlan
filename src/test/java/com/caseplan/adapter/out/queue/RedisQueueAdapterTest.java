package com.caseplan.adapter.out.queue;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class RedisQueueAdapterTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ListOperations<String, String> listOps;

    private RedisQueueAdapter adapter;

    @Before
    public void setup() {
        when(redisTemplate.opsForList()).thenReturn(listOps);
        adapter = new RedisQueueAdapter(redisTemplate);
    }

    @Test
    public void enqueue_pushesToRedisList() {
        adapter.enqueue("42");

        verify(listOps).rightPush("caseplan:pending", "42");
    }
}
