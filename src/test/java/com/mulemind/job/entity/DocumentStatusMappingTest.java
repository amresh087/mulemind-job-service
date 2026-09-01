package com.mulemind.job.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class DocumentStatusMappingTest {

    @Test
    void documentRecordShouldReferenceJobStatusEntity() {
        JobStatus status = JobStatus.builder()
                .id(UUID.randomUUID())
                .code("Indexed")
                .description("Document indexed")
                .build();

        DocumentRecord record = DocumentRecord.builder()
                .id(UUID.randomUUID())
                .status(status)
                .build();

        assertEquals("Indexed", record.getStatus().getCode());
    }
}
