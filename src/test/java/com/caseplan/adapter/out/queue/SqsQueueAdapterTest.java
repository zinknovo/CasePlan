package com.caseplan.adapter.out.queue;

import org.junit.Test;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class SqsQueueAdapterTest {

    @Test
    public void enqueue_sendsPlanIdJson() {
        SqsClient sqsClient = mock(SqsClient.class);
        SqsQueueAdapter adapter = new SqsQueueAdapter(sqsClient, "https://sqs.example/queue");

        adapter.enqueue("123");

        verify(sqsClient).sendMessage(any(SendMessageRequest.class));
    }

    @Test
    public void enqueue_sendFailure_wrapsRuntimeException() {
        SqsClient sqsClient = mock(SqsClient.class);
        doThrow(new RuntimeException("network")).when(sqsClient).sendMessage(any(SendMessageRequest.class));
        SqsQueueAdapter adapter = new SqsQueueAdapter(sqsClient, "https://sqs.example/queue");

        try {
            adapter.enqueue("1");
        } catch (RuntimeException e) {
            assertTrue(e.getMessage().contains("Failed to send SQS message"));
            return;
        }
        throw new AssertionError("expected RuntimeException");
    }
}
