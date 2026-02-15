package com.caseplan.adapter.in.web.controller;

import com.caseplan.adapter.in.web.dto.CreateAttorneyRequest;
import com.caseplan.application.service.AttorneyService;
import com.caseplan.application.service.CreateAttorneyResult;
import com.caseplan.common.exception.BlockException;
import com.caseplan.domain.model.Attorney;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.Silent.class)
public class AttorneyControllerTest {

    @Mock
    private AttorneyService attorneyService;

    @InjectMocks
    private AttorneyController controller;

    @Test
    public void create_new_returns201() {
        CreateAttorneyRequest request = new CreateAttorneyRequest();
        request.setName("Jane");
        request.setBarNumber("BAR-1");

        Attorney attorney = new Attorney();
        attorney.setId(1L);
        when(attorneyService.create("Jane", "BAR-1")).thenReturn(new CreateAttorneyResult(attorney, true));

        ResponseEntity<?> response = controller.create(request);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    @Test
    public void create_duplicate_returns200() {
        CreateAttorneyRequest request = new CreateAttorneyRequest();
        request.setName("Jane");
        request.setBarNumber("BAR-1");

        Attorney attorney = new Attorney();
        when(attorneyService.create("Jane", "BAR-1")).thenReturn(new CreateAttorneyResult(attorney, false));

        ResponseEntity<?> response = controller.create(request);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test(expected = BlockException.class)
    public void create_conflict_propagates() {
        CreateAttorneyRequest request = new CreateAttorneyRequest();
        request.setName("Jane");
        request.setBarNumber("BAR-1");
        when(attorneyService.create("Jane", "BAR-1"))
                .thenThrow(new BlockException("ATTORNEY_BAR_CONFLICT", "conflict", null));

        controller.create(request);
    }
}
