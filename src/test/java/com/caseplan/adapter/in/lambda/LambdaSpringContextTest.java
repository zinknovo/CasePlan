package com.caseplan.adapter.in.lambda;

import org.junit.Test;
import org.springframework.context.ConfigurableApplicationContext;

import java.lang.reflect.Field;

import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;

public class LambdaSpringContextTest {

    @Test
    public void getContext_whenPreSet_returnsExistingContext() throws Exception {
        ConfigurableApplicationContext fake = mock(ConfigurableApplicationContext.class);
        Field field = LambdaSpringContext.class.getDeclaredField("context");
        field.setAccessible(true);
        Object old = field.get(null);
        try {
            field.set(null, fake);
            assertSame(fake, LambdaSpringContext.getContext());
        } finally {
            field.set(null, old);
        }
    }
}
