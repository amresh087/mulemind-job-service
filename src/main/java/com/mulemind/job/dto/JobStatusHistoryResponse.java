package com.mulemind.job.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class JobStatusHistoryResponse {
    private UUID id;
    private UUID documentId;
    private String statusCode;
    private String statusDescription;
    private LocalDateTime changedAt;
}
