package com.caseplan.application.service;

import com.caseplan.adapter.out.persistence.AttorneyRepo;
import com.caseplan.common.exception.BlockException;
import com.caseplan.common.exception.ValidationException;
import com.caseplan.domain.model.Attorney;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.Silent.class)
public class AttorneyServiceTest {

    @Mock
    private AttorneyRepo attorneyRepo;

    @InjectMocks
    private AttorneyService attorneyService;

    @Test
    public void create_newAttorney_returns201State() {
        when(attorneyRepo.findByBarNumber("BAR-1")).thenReturn(Optional.empty());
        when(attorneyRepo.findByName("Jane Smith")).thenReturn(Optional.empty());
        when(attorneyRepo.save(any(Attorney.class))).thenAnswer(new Answer<Attorney>() {
            @Override
            public Attorney answer(InvocationOnMock invocation) {
                Attorney attorney = invocation.getArgument(0);
                attorney.setId(1L);
                return attorney;
            }
        });

        CreateAttorneyResult result = attorneyService.create("Jane Smith", "BAR-1");

        assertTrue(result.isCreated());
        assertEquals(Long.valueOf(1L), result.getAttorney().getId());
    }

    @Test
    public void create_existingSameBarAndName_returns200State() {
        Attorney existing = new Attorney();
        existing.setId(2L);
        existing.setName("Jane Smith");
        existing.setBarNumber("BAR-2");
        when(attorneyRepo.findByBarNumber("BAR-2")).thenReturn(Optional.of(existing));

        CreateAttorneyResult result = attorneyService.create("Jane Smith", "BAR-2");

        assertFalse(result.isCreated());
        verify(attorneyRepo, never()).save(any(Attorney.class));
    }

    @Test(expected = BlockException.class)
    public void create_existingBarDifferentName_throws409() {
        Attorney existing = new Attorney();
        existing.setName("Other Name");
        existing.setBarNumber("BAR-9");
        when(attorneyRepo.findByBarNumber("BAR-9")).thenReturn(Optional.of(existing));

        attorneyService.create("Jane Smith", "BAR-9");
    }

    @Test
    public void create_existingNameAndBar_returns200State() {
        when(attorneyRepo.findByBarNumber("BAR-3")).thenReturn(Optional.empty());
        Attorney existing = new Attorney();
        existing.setId(3L);
        existing.setName("Jane Smith");
        existing.setBarNumber("BAR-3");
        when(attorneyRepo.findByName("Jane Smith")).thenReturn(Optional.of(existing));

        CreateAttorneyResult result = attorneyService.create("Jane Smith", "BAR-3");

        assertFalse(result.isCreated());
        assertEquals(Long.valueOf(3L), result.getAttorney().getId());
    }

    @Test(expected = BlockException.class)
    public void create_existingNameDifferentBar_throws409() {
        when(attorneyRepo.findByBarNumber("BAR-10")).thenReturn(Optional.empty());
        Attorney existing = new Attorney();
        existing.setName("Jane Smith");
        existing.setBarNumber("BAR-OLD");
        when(attorneyRepo.findByName("Jane Smith")).thenReturn(Optional.of(existing));

        attorneyService.create("Jane Smith", "BAR-10");
    }

    @Test(expected = ValidationException.class)
    public void create_nullName_throws400Validation() {
        attorneyService.create(null, "BAR-1");
    }

    @Test(expected = ValidationException.class)
    public void create_blankBar_throws400Validation() {
        attorneyService.create("Jane", "   ");
    }
}
