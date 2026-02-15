package com.caseplan.adapter.out.queue;

import com.caseplan.application.port.out.QueuePort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;

@RequiredArgsConstructor
public class RedisQueueAdapter implements QueuePort {

    private final StringRedisTemplate redisTemplate;

    private static final String QUEUE_KEY = "caseplan:pending";

    @Override
    public void enqueue(String id) {
        redisTemplate.opsForList().rightPush(QUEUE_KEY, id);
    }
}
