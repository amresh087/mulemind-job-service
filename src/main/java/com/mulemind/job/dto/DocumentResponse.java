package com.mulemind.job.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DocumentResponse {
    private UUID id;
    private String name;
    private String type;
    private String tenant;
    private String transactionTypeCode;
    private String version;
    private String status;
    private String contentType;
    private String objectName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
