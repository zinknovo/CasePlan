package com.caseplan.adapter.in.web.controller;

import com.caseplan.adapter.in.intake.AdapterFactory;
import com.caseplan.adapter.in.intake.adapter.BaseIntakeAdapter;
import com.caseplan.adapter.in.intake.adapter.JsonAAdapter;
import com.caseplan.adapter.in.intake.model.*;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class IntakeControllerTest {

    @Mock private AdapterFactory adapterFactory;

    private IntakeController controller;

    @Before
    public void setup() {
        controller = new IntakeController(adapterFactory);
    }

    @Test
    public void intake_validSource_returns201() {
        // Use a real adapter since process() is final and can't be mocked
        JsonAAdapter realAdapter = new JsonAAdapter();
        String validJson = "{\"fname\":\"John\",\"lname\":\"Doe\",\"bar_num\":\"BAR1\",\"case_num\":\"123456\"}";

        when(adapterFactory.getAdapter("jsonA")).thenReturn(Optional.of(realAdapter));

        ResponseEntity<NormCaseOrder> response = controller.intake("jsonA", validJson);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("John", response.getBody().getClient().getFirstName());
    }

    @Test
    public void intake_unknownSource_returns404() {
        when(adapterFactory.getAdapter("unknown")).thenReturn(Optional.empty());

        ResponseEntity<NormCaseOrder> response = controller.intake("unknown", "raw data");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }
}
