package com.caseplan.adapter.in.lambda;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertTrue;

public class CloudWatchEmfTest {

    @Test
    public void emit_writesValidEmfJsonToStdout() {
        PrintStream oldOut = System.out;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        System.setOut(new PrintStream(bos));
        try {
            CloudWatchEmf.record()
                    .dimension("handler", "CreateOrder")
                    .count("OrderCreated", 1)
                    .millis("HandlerDuration", 12)
                    .property("note", "ok")
                    .property("flag", true)
                    .property("n", 7)
                    .property("obj", new Object())
                    .property("nullable", null)
                    .emit();
        } finally {
            System.setOut(oldOut);
        }

        String out = new String(bos.toByteArray(), StandardCharsets.UTF_8);
        assertTrue(out.contains("\"_aws\""));
        assertTrue(out.contains("\"CloudWatchMetrics\""));
        assertTrue(out.contains("\"Namespace\":\"CasePlan/Lambda\""));
        assertTrue(out.contains("\"handler\":\"CreateOrder\""));
        assertTrue(out.contains("\"OrderCreated\":1.0"));
        assertTrue(out.contains("\"HandlerDuration\":12.0"));
        assertTrue(out.contains("\"note\":\"ok\""));
    }
}
