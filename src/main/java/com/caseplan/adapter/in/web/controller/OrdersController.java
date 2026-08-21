package com.caseplan.adapter.in.web.controller;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.caseplan.adapter.in.lambda.CreateOrderHandler;
import com.caseplan.adapter.in.lambda.GetOrderStatusHandler;
import com.caseplan.adapter.in.lambda.GetOrdersHandler;
import com.caseplan.adapter.in.lambda.LambdaSpringContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;

/**
 * Local mirror of the API Gateway /orders routes.
 * <p>
 * In production these routes are served by the Lambda handlers behind API Gateway.
 * Locally (dev app + e2e) this controller translates HTTP requests into
 * APIGatewayProxyRequestEvents and runs the exact same handler code, so the
 * request path exercised by e2e tests is identical to the deployed one.
 */
@RestController
@RequestMapping("/orders")
public class OrdersController {

    private final CreateOrderHandler createOrderHandler;
    private final GetOrdersHandler getOrdersHandler;
    private final GetOrderStatusHandler getOrderStatusHandler;

    public OrdersController(ConfigurableApplicationContext applicationContext) {
        // Reuse the running web context instead of bootstrapping a second one.
        LambdaSpringContext.setContext(applicationContext);
        this.createOrderHandler = new CreateOrderHandler();
        this.getOrdersHandler = new GetOrdersHandler();
        this.getOrderStatusHandler = new GetOrderStatusHandler();
    }

    @GetMapping
    public ResponseEntity<String> listOrders() {
        APIGatewayProxyResponseEvent res = getOrdersHandler.handleRequest(new APIGatewayProxyRequestEvent(), null);
        return toResponse(res);
    }

    @PostMapping
    public ResponseEntity<String> createOrder(@RequestBody(required = false) String body) {
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
        event.setHttpMethod("POST");
        event.setBody(body);
        APIGatewayProxyResponseEvent res = createOrderHandler.handleRequest(event, null);
        return toResponse(res);
    }

    @GetMapping("/{id}")
    public ResponseEntity<String> getOrder(@PathVariable String id) {
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
        event.setPathParameters(Collections.singletonMap("id", id));
        APIGatewayProxyResponseEvent res = getOrderStatusHandler.handleRequest(event, null);
        return toResponse(res);
    }

    private ResponseEntity<String> toResponse(APIGatewayProxyResponseEvent res) {
        HttpHeaders headers = new HttpHeaders();
        Map<String, String> resHeaders = res.getHeaders();
        if (resHeaders != null) {
            resHeaders.forEach(headers::add);
        }
        return ResponseEntity.status(res.getStatusCode()).headers(headers).body(res.getBody());
    }
}
