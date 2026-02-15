package com.caseplan.application.service;

import com.caseplan.adapter.out.persistence.ClientRepo;
import com.caseplan.common.exception.BlockException;
import com.caseplan.common.exception.ValidationException;
import com.caseplan.domain.model.Client;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.Collections;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.Silent.class)
public class ClientServiceTest {

    @Mock
    private ClientRepo clientRepo;

    @InjectMocks
    private ClientService clientService;

    @Test
    public void create_newClient_returnsCreatedTrue() {
        when(clientRepo.findByIdNumber("ID-1")).thenReturn(Optional.empty());
        when(clientRepo.findByFirstNameAndLastName("John", "Doe")).thenReturn(Optional.empty());
        when(clientRepo.save(any(Client.class))).thenAnswer(new Answer<Client>() {
            @Override
            public Client answer(InvocationOnMock invocation) {
                Client client = invocation.getArgument(0);
                client.setId(1L);
                return client;
            }
        });

        CreateClientResult result = clientService.create("John", "Doe", "ID-1");

        assertTrue(result.isCreated());
        assertEquals(Long.valueOf(1L), result.getClient().getId());
        verify(clientRepo).save(any(Client.class));
    }

    @Test
    public void create_sameIdAndName_returnsExisting200() {
        Client existing = new Client();
        existing.setId(2L);
        existing.setFirstName("John");
        existing.setLastName("Doe");
        existing.setIdNumber("ID-2");

        when(clientRepo.findByIdNumber("ID-2")).thenReturn(Optional.of(existing));

        CreateClientResult result = clientService.create("John", "Doe", "ID-2");

        assertFalse(result.isCreated());
        assertEquals(Long.valueOf(2L), result.getClient().getId());
        verify(clientRepo, never()).save(any(Client.class));
    }

    @Test(expected = BlockException.class)
    public void create_sameIdDifferentName_throws409() {
        Client existing = new Client();
        existing.setFirstName("Jane");
        existing.setLastName("Smith");
        existing.setIdNumber("ID-3");
        when(clientRepo.findByIdNumber("ID-3")).thenReturn(Optional.of(existing));

        clientService.create("John", "Doe", "ID-3");
    }

    @Test(expected = BlockException.class)
    public void create_sameNameDifferentId_throws409() {
        Client existing = new Client();
        existing.setFirstName("John");
        existing.setLastName("Doe");
        existing.setIdNumber("OLD-ID");

        when(clientRepo.findByIdNumber("NEW-ID")).thenReturn(Optional.empty());
        when(clientRepo.findByFirstNameAndLastName("John", "Doe")).thenReturn(Optional.of(existing));

        clientService.create("John", "Doe", "NEW-ID");
    }

    @Test(expected = ValidationException.class)
    public void create_blankFirstName_throws400Validation() {
        clientService.create("   ", "Doe", "ID-1");
    }

    @Test
    public void create_existingNameWithoutId_upgradesIdAndReturns200() {
        Client existing = new Client();
        existing.setId(4L);
        existing.setFirstName("John");
        existing.setLastName("Doe");
        existing.setIdNumber(null);

        when(clientRepo.findByIdNumber("ID-4")).thenReturn(Optional.empty());
        when(clientRepo.findByFirstNameAndLastName("John", "Doe")).thenReturn(Optional.of(existing));
        when(clientRepo.save(existing)).thenReturn(existing);

        CreateClientResult result = clientService.create("John", "Doe", "ID-4");

        assertFalse(result.isCreated());
        assertEquals("ID-4", result.getClient().getIdNumber());
        verify(clientRepo).save(existing);
    }

    @Test
    public void create_existingNameSameId_returnsExistingWithoutSave() {
        Client existing = new Client();
        existing.setId(5L);
        existing.setFirstName("John");
        existing.setLastName("Doe");
        existing.setIdNumber("ID-5");

        when(clientRepo.findByIdNumber("ID-5")).thenReturn(Optional.empty());
        when(clientRepo.findByFirstNameAndLastName("John", "Doe")).thenReturn(Optional.of(existing));

        CreateClientResult result = clientService.create("John", "Doe", "ID-5");

        assertFalse(result.isCreated());
        assertEquals(Long.valueOf(5L), result.getClient().getId());
        verify(clientRepo, never()).save(any(Client.class));
    }

    @Test
    public void create_existingNameWithoutIncomingId_returnsExisting() {
        Client existing = new Client();
        existing.setId(6L);
        existing.setFirstName("John");
        existing.setLastName("Doe");
        existing.setIdNumber("EXISTING");

        when(clientRepo.findByFirstNameAndLastName("John", "Doe")).thenReturn(Optional.of(existing));

        CreateClientResult result = clientService.create("John", "Doe", null);

        assertFalse(result.isCreated());
        assertEquals(Long.valueOf(6L), result.getClient().getId());
    }

    @Test(expected = ValidationException.class)
    public void create_blankLastName_throws400Validation() {
        clientService.create("John", "   ", "ID-1");
    }

    @Test
    public void listPage_normalizesPagination() {
        when(clientRepo.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 1), 0));

        clientService.listPage(0, 0);

        verify(clientRepo).findAll(PageRequest.of(0, 1, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt")));
    }

    @Test
    public void getById_delegatesRepo() {
        Client existing = new Client();
        existing.setId(9L);
        when(clientRepo.findById(9L)).thenReturn(Optional.of(existing));

        Optional<Client> result = clientService.getById(9L);

        assertTrue(result.isPresent());
        assertEquals(Long.valueOf(9L), result.get().getId());
    }

    @Test
    public void update_notFound_returnsEmpty() {
        when(clientRepo.findById(12L)).thenReturn(Optional.empty());

        Optional<Client> result = clientService.update(12L, "A", "B", "ID");

        assertFalse(result.isPresent());
    }

    @Test
    public void update_success_returnsUpdatedClient() {
        Client current = new Client();
        current.setId(13L);
        current.setFirstName("Old");
        current.setLastName("Name");
        current.setIdNumber(null);
        when(clientRepo.findById(13L)).thenReturn(Optional.of(current));
        when(clientRepo.findByIdNumber("NEW")).thenReturn(Optional.empty());
        when(clientRepo.findByFirstNameAndLastName("New", "Name")).thenReturn(Optional.of(current));
        when(clientRepo.save(current)).thenReturn(current);

        Optional<Client> result = clientService.update(13L, "New", "Name", "NEW");

        assertTrue(result.isPresent());
        assertEquals("NEW", result.get().getIdNumber());
    }

    @Test
    public void update_sameIdForExistingIdNumber_allowed() {
        Client current = new Client();
        current.setId(30L);
        current.setFirstName("Old");
        current.setLastName("Name");
        current.setIdNumber("ID-30");

        when(clientRepo.findById(30L)).thenReturn(Optional.of(current));
        when(clientRepo.findByIdNumber("ID-30")).thenReturn(Optional.of(current));
        when(clientRepo.findByFirstNameAndLastName("Old", "Name")).thenReturn(Optional.of(current));
        when(clientRepo.save(current)).thenReturn(current);

        Optional<Client> result = clientService.update(30L, "Old", "Name", "ID-30");

        assertTrue(result.isPresent());
        assertEquals("ID-30", result.get().getIdNumber());
    }

    @Test(expected = ValidationException.class)
    public void update_blankLastName_throws400Validation() {
        Client current = new Client();
        current.setId(31L);
        when(clientRepo.findById(31L)).thenReturn(Optional.of(current));

        clientService.update(31L, "John", "   ", "ID-31");
    }

    @Test(expected = BlockException.class)
    public void update_idConflict_throws409() {
        Client current = new Client();
        current.setId(20L);
        when(clientRepo.findById(20L)).thenReturn(Optional.of(current));
        Client other = new Client();
        other.setId(21L);
        when(clientRepo.findByIdNumber("ID-X")).thenReturn(Optional.of(other));

        clientService.update(20L, "A", "B", "ID-X");
    }

    @Test(expected = BlockException.class)
    public void update_nameConflict_throws409() {
        Client current = new Client();
        current.setId(22L);
        when(clientRepo.findById(22L)).thenReturn(Optional.of(current));

        Client otherByName = new Client();
        otherByName.setId(23L);
        otherByName.setFirstName("Same");
        otherByName.setLastName("Name");
        when(clientRepo.findByFirstNameAndLastName("Same", "Name")).thenReturn(Optional.of(otherByName));

        clientService.update(22L, "Same", "Name", null);
    }

    @Test
    public void update_withoutIdNumber_skipsIdLookupAndSaves() {
        Client current = new Client();
        current.setId(24L);
        when(clientRepo.findById(24L)).thenReturn(Optional.of(current));
        when(clientRepo.findByFirstNameAndLastName("A", "B")).thenReturn(Optional.of(current));
        when(clientRepo.save(current)).thenReturn(current);

        Optional<Client> result = clientService.update(24L, "A", "B", "   ");

        assertTrue(result.isPresent());
        assertEquals("A", result.get().getFirstName());
        verify(clientRepo, never()).findByIdNumber(any());
    }

    @Test
    public void delete_existing_returnsTrue() {
        when(clientRepo.existsById(7L)).thenReturn(true);

        boolean result = clientService.delete(7L);

        assertTrue(result);
        verify(clientRepo).deleteById(7L);
    }

    @Test
    public void delete_notExisting_returnsFalse() {
        when(clientRepo.existsById(anyLong())).thenReturn(false);

        boolean result = clientService.delete(7L);

        assertFalse(result);
    }
}
