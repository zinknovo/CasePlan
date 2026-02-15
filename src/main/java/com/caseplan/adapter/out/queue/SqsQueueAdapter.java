package com.caseplan.adapter.out.queue;

import com.caseplan.application.port.out.QueuePort;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
public class SqsQueueAdapter implements QueuePort {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SqsClient sqsClient;
    private final String queueUrl;

    @Override
    public void enqueue(String id) {
        Map<String, Object> body = new HashMap<>();
        body.put("planId", id);
        try {
            String messageBody = MAPPER.writeValueAsString(body);
            sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(messageBody)
                    .build());
        } catch (Exception e) {
            throw new RuntimeException("Failed to send SQS message for plan " + id, e);
        }
    }
}
