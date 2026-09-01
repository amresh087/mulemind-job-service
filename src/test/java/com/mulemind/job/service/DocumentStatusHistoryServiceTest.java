package com.mulemind.job.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.mulemind.job.dto.DocumentRequest;
import com.mulemind.job.entity.JobStatusHistory;
import com.mulemind.job.repository.JobStatusHistoryRepository;

@SpringBootTest
class DocumentStatusHistoryServiceTest {

    @Autowired
    private DocumentService documentService;

    @Autowired
    private JobStatusHistoryRepository jobStatusHistoryRepository;

    @Test
    void shouldPersistStatusHistoryWhenCreatingDocument() {
        DocumentRequest request = new DocumentRequest();
        request.setName("history-test.pdf");
        request.setType("PDF");
        request.setTenant("tenant-1");
        request.setStatus("Indexed");

        var created = documentService.create(request);

        List<JobStatusHistory> history = jobStatusHistoryRepository.findByDocumentOrderByChangedAtAsc(
                documentService.getDocumentEntityById(created.getId()));

        assertNotNull(history);
        assertEquals(1, history.size());
        assertEquals("Indexed", history.get(0).getStatus().getCode());
    }

    @Test
    void shouldDeleteDocumentAndItsStatusHistory() {
        DocumentRequest request = new DocumentRequest();
        request.setName("delete-history-test.pdf");
        request.setType("PDF");
        request.setTenant("tenant-1");
        request.setStatus("Indexed");

        var created = documentService.create(request);
        documentService.delete(created.getId());

        assertThrows(IllegalArgumentException.class, () -> documentService.getDocumentEntityById(created.getId()));
    }
}
