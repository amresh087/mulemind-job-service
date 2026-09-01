package com.mulemind.job.dto;

import lombok.Data;

@Data
public class DocumentRequest {
    private String name;
    private String type;
    private String tenant;
    private String status;
    private String contentType;
}
