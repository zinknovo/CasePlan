package com.caseplan.adapter.in.lambda;

import com.caseplan.App;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Shared Spring context for Lambda handlers.
 */
public final class LambdaSpringContext {

    private static volatile ConfigurableApplicationContext context;

    private LambdaSpringContext() {
    }

    public static ConfigurableApplicationContext getContext() {
        if (context == null) {
            synchronized (LambdaSpringContext.class) {
                if (context == null) {
                    context = new SpringApplicationBuilder(App.class)
                            .web(WebApplicationType.NONE)
                            .properties(
                                    "caseplan.consumer.enabled=false",
                                    "spring.data.redis.repositories.enabled=false",
                                    "spring.main.lazy-initialization=true"
                            )
                            .run();
                }
            }
        }
        return context;
    }
}
