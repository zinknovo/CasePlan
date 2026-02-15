package com.caseplan.adapter.in.lambda;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.caseplan.adapter.in.web.controller.CasePlanController;
import com.caseplan.adapter.in.web.dto.CreateCasePlanRequest;
import com.caseplan.common.exception.WarningException;
import com.caseplan.domain.model.CasePlan;
import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import javax.validation.ConstraintViolation;
import javax.validation.Path;
import javax.validation.Validator;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CreateOrderHandlerTest {

    private APIGatewayProxyRequestEvent validRequest() {
        APIGatewayProxyRequestEvent req = new APIGatewayProxyRequestEvent();
        req.setBody("{\"clientFirstName\":\"John\",\"clientLastName\":\"Doe\",\"attorneyName\":\"Atty\",\"barNumber\":\"BAR1\",\"primaryCauseOfAction\":\"Contract\",\"remedySought\":\"Comp\"}");
        return req;
    }

    @Test
    public void handleRequest_emptyBody_returns400() {
        CasePlanController controller = mock(CasePlanController.class);
        Validator validator = mock(Validator.class);
        CreateOrderHandler handler = new CreateOrderHandler(controller, validator);

        APIGatewayProxyResponseEvent res = handler.handleRequest(new APIGatewayProxyRequestEvent(), null);
        assertEquals(Integer.valueOf(400), res.getStatusCode());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void handleRequest_validationFail_returns400() {
        CasePlanController controller = mock(CasePlanController.class);
        Validator validator = mock(Validator.class);
        ConstraintViolation violation = mock(ConstraintViolation.class);
        Path path = mock(Path.class);
        when(path.toString()).thenReturn("clientFirstName");
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn("is required");
        Set<ConstraintViolation<CreateCasePlanRequest>> set = Collections.singleton((ConstraintViolation<CreateCasePlanRequest>) violation);
        when(validator.validate(any(CreateCasePlanRequest.class))).thenReturn(set);

        CreateOrderHandler handler = new CreateOrderHandler(controller, validator);
        APIGatewayProxyResponseEvent res = handler.handleRequest(validRequest(), null);
        assertEquals(Integer.valueOf(400), res.getStatusCode());
        assertTrue(res.getBody().contains("validation failed"));
    }

    @Test
    public void handleRequest_successCasePlanBody_returns201() {
        CasePlanController controller = mock(CasePlanController.class);
        Validator validator = mock(Validator.class);
        when(validator.validate(any(CreateCasePlanRequest.class))).thenReturn(Collections.emptySet());

        CasePlan cp = new CasePlan();
        cp.setId(12L);
        doReturn((ResponseEntity<?>) ResponseEntity.status(HttpStatus.CREATED).body(cp))
                .when(controller).create(any(CreateCasePlanRequest.class));

        CreateOrderHandler handler = new CreateOrderHandler(controller, validator);
        APIGatewayProxyResponseEvent res = handler.handleRequest(validRequest(), null);
        assertEquals(Integer.valueOf(201), res.getStatusCode());
        assertTrue(res.getBody().contains("\"id\":12"));
    }

    @Test
    public void handleRequest_successWrappedMapBody_returns201() {
        CasePlanController controller = mock(CasePlanController.class);
        Validator validator = mock(Validator.class);
        when(validator.validate(any(CreateCasePlanRequest.class))).thenReturn(Collections.emptySet());

        Map<String, Object> data = new HashMap<>();
        data.put("id", 55);
        Map<String, Object> wrapped = new HashMap<>();
        wrapped.put("data", data);
        doReturn((ResponseEntity<?>) ResponseEntity.status(HttpStatus.CREATED).body(wrapped))
                .when(controller).create(any(CreateCasePlanRequest.class));

        CreateOrderHandler handler = new CreateOrderHandler(controller, validator);
        APIGatewayProxyResponseEvent res = handler.handleRequest(validRequest(), null);
        assertEquals(Integer.valueOf(201), res.getStatusCode());
        assertTrue(res.getBody().contains("\"id\":55"));
    }

    @Test
    public void handleRequest_baseAppException_returnsMappedStatus() {
        CasePlanController controller = mock(CasePlanController.class);
        Validator validator = mock(Validator.class);
        when(validator.validate(any(CreateCasePlanRequest.class))).thenReturn(Collections.emptySet());
        when(controller.create(any(CreateCasePlanRequest.class)))
                .thenThrow(new WarningException("TYPE", "msg", null));

        CreateOrderHandler handler = new CreateOrderHandler(controller, validator);
        APIGatewayProxyResponseEvent res = handler.handleRequest(validRequest(), null);
        assertEquals(Integer.valueOf(200), res.getStatusCode());
    }

    @Test
    public void handleRequest_createdButNoId_returns500() {
        CasePlanController controller = mock(CasePlanController.class);
        Validator validator = mock(Validator.class);
        when(validator.validate(any(CreateCasePlanRequest.class))).thenReturn(Collections.emptySet());
        doReturn((ResponseEntity<?>) ResponseEntity.status(HttpStatus.CREATED).body(new HashMap<>()))
                .when(controller).create(any(CreateCasePlanRequest.class));

        CreateOrderHandler handler = new CreateOrderHandler(controller, validator);
        APIGatewayProxyResponseEvent res = handler.handleRequest(validRequest(), null);
        assertEquals(Integer.valueOf(500), res.getStatusCode());
    }

    @Test
    public void handleRequest_invalidJson_returns500() {
        CasePlanController controller = mock(CasePlanController.class);
        Validator validator = mock(Validator.class);
        CreateOrderHandler handler = new CreateOrderHandler(controller, validator);

        APIGatewayProxyRequestEvent req = new APIGatewayProxyRequestEvent();
        req.setBody("{invalid");
        APIGatewayProxyResponseEvent res = handler.handleRequest(req, null);
        assertEquals(Integer.valueOf(500), res.getStatusCode());
    }

    @Test
    public void handleRequest_unparseableId_returns500() {
        CasePlanController controller = mock(CasePlanController.class);
        Validator validator = mock(Validator.class);
        when(validator.validate(any(CreateCasePlanRequest.class))).thenReturn(Collections.emptySet());
        Map<String, Object> body = new HashMap<>();
        body.put("id", "not-number");
        doReturn((ResponseEntity<?>) ResponseEntity.status(HttpStatus.CREATED).body(body))
                .when(controller).create(any(CreateCasePlanRequest.class));

        CreateOrderHandler handler = new CreateOrderHandler(controller, validator);
        APIGatewayProxyResponseEvent res = handler.handleRequest(validRequest(), null);
        assertEquals(Integer.valueOf(500), res.getStatusCode());
    }

    @Test
    public void handleRequest_nullEvent_returns400() {
        CasePlanController controller = mock(CasePlanController.class);
        Validator validator = mock(Validator.class);
        CreateOrderHandler handler = new CreateOrderHandler(controller, validator);
        APIGatewayProxyResponseEvent res = handler.handleRequest(null, null);
        assertEquals(Integer.valueOf(400), res.getStatusCode());
    }

    @Test
    public void handleRequest_nullResponseBody_returns500() {
        CasePlanController controller = mock(CasePlanController.class);
        Validator validator = mock(Validator.class);
        when(validator.validate(any(CreateCasePlanRequest.class))).thenReturn(Collections.emptySet());
        doReturn((ResponseEntity<?>) ResponseEntity.status(HttpStatus.CREATED).body(null))
                .when(controller).create(any(CreateCasePlanRequest.class));

        CreateOrderHandler handler = new CreateOrderHandler(controller, validator);
        APIGatewayProxyResponseEvent res = handler.handleRequest(validRequest(), null);
        assertEquals(Integer.valueOf(500), res.getStatusCode());
    }
}
