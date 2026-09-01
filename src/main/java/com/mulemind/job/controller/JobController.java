package com.mulemind.job.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mulemind.job.dto.DocumentRequest;
import com.mulemind.job.dto.JobResponse;
import com.mulemind.job.dto.JobStatusHistoryResponse;
import com.mulemind.job.service.DocumentService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/jobs")
public class JobController {

    private final DocumentService documentService;

    @GetMapping
    public List<JobResponse> getAllJobs() {
        return documentService.getAllJobs();
    }

    @GetMapping("/{id}")
    public JobResponse getJobById(@PathVariable UUID id) {
        return documentService.getJobById(id);
    }

    @GetMapping("/{id}/status-history")
    public List<JobStatusHistoryResponse> getJobStatusHistory(@PathVariable UUID id) {
        return documentService.getStatusHistory(id);
    }

    @PutMapping("/{id}")
    public JobResponse updateJob(@PathVariable UUID id, @RequestBody DocumentRequest request) {
        return documentService.updateJob(id, request);
    }

    @PutMapping("/{id}/status")
    public JobResponse updateJobStatus(@PathVariable UUID id, @RequestBody Map<String, String> payload) {
        DocumentRequest request = new DocumentRequest();
        request.setStatus(payload.get("status"));
        return documentService.updateJobStatus(id, request);
    }
}
