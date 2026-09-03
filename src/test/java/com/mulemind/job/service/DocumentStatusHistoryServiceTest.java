package com.mulemind.job.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.UUID;

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

    @Test
    void shouldCreateHistoryEntryForEachStatusUpdate() {
        String suffix = UUID.randomUUID().toString();
        String createdStatus = "Created-" + suffix;
        String processingStatus = "Processing-" + suffix;
        String completedStatus = "Completed-" + suffix;
        DocumentRequest createRequest = new DocumentRequest();
        createRequest.setName("multiple-history-test.pdf");
        createRequest.setType("PDF");
        createRequest.setTenant("tenant-1");
        createRequest.setStatus(createdStatus);

        var created = documentService.create(createRequest);

        DocumentRequest processingRequest = new DocumentRequest();
        processingRequest.setStatus(processingStatus);
        documentService.updateJobStatus(created.getId(), processingRequest);

        DocumentRequest completedRequest = new DocumentRequest();
        completedRequest.setStatus(completedStatus);
        documentService.updateJobStatus(created.getId(), completedRequest);

        List<JobStatusHistory> history = jobStatusHistoryRepository.findByDocumentOrderByChangedAtAsc(
                documentService.getDocumentEntityById(created.getId()));

        assertEquals(3, history.size());
        assertEquals(createdStatus, history.get(0).getStatusCode());
        assertEquals(processingStatus, history.get(1).getStatusCode());
        assertEquals(completedStatus, history.get(2).getStatusCode());
    }
}
