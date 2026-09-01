package com.mulemind.job.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.mulemind.job.dto.DocumentRequest;
import com.mulemind.job.dto.DocumentResponse;
import com.mulemind.job.dto.JobResponse;
import com.mulemind.job.dto.JobStatusHistoryResponse;
import com.mulemind.job.entity.DocumentRecord;
import com.mulemind.job.entity.JobStatus;
import com.mulemind.job.entity.JobStatusHistory;
import com.mulemind.job.repository.DocumentRepository;
import com.mulemind.job.repository.JobStatusHistoryRepository;
import com.mulemind.job.repository.JobStatusRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentStorageService documentStorageService;
    private final DocumentRepository documentRepository;
    private final JobStatusRepository jobStatusRepository;
    private final JobStatusHistoryRepository jobStatusHistoryRepository;

    public DocumentResponse create(DocumentRequest request) {
        DocumentRecord record = DocumentRecord.builder()
                .id(UUID.randomUUID())
                .name(request.getName())
                .type(normalizeType(request.getType()))
                .tenant(request.getTenant())
                .status(resolveStatus(request.getStatus()))
                .contentType(normalizeContentType(request.getContentType(), request.getName()))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        DocumentRecord saved = documentRepository.save(record);
        recordStatusHistory(saved, saved.getStatus());
        return toResponse(saved);
    }

    public DocumentResponse createWithFile(DocumentRequest request, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File must be provided for upload");
        }

        UUID documentId = UUID.randomUUID();
        String contentType = normalizeContentType(request.getContentType(), file.getOriginalFilename());
        String objectName = buildObjectName(documentId,
                request.getTenant(),
                normalizeType(request.getType()),
                file.getOriginalFilename());

        String storageKey;
        try {
            storageKey = documentStorageService.storeFile(documentId, objectName, contentType,
                    file.getInputStream(), file.getSize());
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to store uploaded file", ex);
        }

        DocumentRecord record = DocumentRecord.builder()
                .id(documentId)
                .name(request.getName() != null ? request.getName() : file.getOriginalFilename())
                .type(normalizeType(request.getType()))
                .tenant(request.getTenant())
                .status(resolveStatus(request.getStatus()))
                .contentType(contentType)
                .objectName(storageKey)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        DocumentRecord saved = documentRepository.save(record);
        recordStatusHistory(saved, saved.getStatus());
        return toResponse(saved);
    }

    public List<DocumentResponse> getAll() {
        return documentRepository.findAll().stream().map(this::toResponse).toList();
    }

    public List<JobResponse> getAllJobs() {
        return documentRepository.findAll().stream().map(this::toJobResponse).toList();
    }

    public DocumentResponse getById(UUID id) {
        DocumentRecord record = getDocumentEntityById(id);
        return toResponse(record);
    }

    public JobResponse getJobById(UUID id) {
        return toJobResponse(getDocumentEntityById(id));
    }

    public DocumentRecord getDocumentEntityById(UUID id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
    }

    public DocumentResponse update(UUID id, DocumentRequest request) {
        DocumentRecord existing = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));

        String updatedName = request.getName() != null ? request.getName() : existing.getName();
        String updatedType = normalizeType(request.getType() != null ? request.getType() : existing.getType());
        String updatedTenant = request.getTenant() != null ? request.getTenant() : existing.getTenant();
        JobStatus updatedStatus = resolveStatus(request.getStatus() != null ? request.getStatus() : statusCode(existing.getStatus()));
        String updatedContentType = normalizeContentType(request.getContentType(), updatedName);

        String newObjectName = existing.getObjectName();
        if (existing.getObjectName() != null && !existing.getObjectName().isBlank()) {
            String currentFileName = existing.getObjectName().substring(existing.getObjectName().lastIndexOf('/') + 1);
            String targetFileName = updatedName == null ? currentFileName : updatedName.replaceAll("[^a-zA-Z0-9_.-]", "_");
            String targetObjectName = buildObjectName(existing.getId(), updatedTenant, updatedType, targetFileName);
            if (!existing.getObjectName().equals(targetObjectName)) {
                documentStorageService.renameFile(existing.getObjectName(), targetObjectName);
                newObjectName = targetObjectName;
            }
        }

        DocumentRecord updated = DocumentRecord.builder()
                .id(existing.getId())
                .name(updatedName)
                .type(updatedType)
                .tenant(updatedTenant)
                .status(updatedStatus)
                .contentType(updatedContentType)
                .objectName(newObjectName)
                .createdAt(existing.getCreatedAt())
                .updatedAt(LocalDateTime.now())
                .build();
        DocumentRecord saved = documentRepository.save(updated);
        if (!statusCode(existing.getStatus()).equals(statusCode(saved.getStatus()))) {
            recordStatusHistory(saved, saved.getStatus());
        }
        return toResponse(saved);
    }

    public JobResponse updateJob(UUID id, DocumentRequest request) {
        return toJobResponse(update(id, request));
    }

    public JobResponse updateJobStatus(UUID id, DocumentRequest request) {
        DocumentRecord existing = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));

        String newStatusCode = request.getStatus();
        if (newStatusCode == null || newStatusCode.isBlank()) {
            throw new IllegalArgumentException("Status cannot be blank");
        }

        JobStatus newStatus = resolveStatus(newStatusCode);
        if (!statusCode(existing.getStatus()).equals(newStatus.getCode())) {
            existing.setStatus(newStatus);
            existing.setUpdatedAt(LocalDateTime.now());
            DocumentRecord saved = documentRepository.save(existing);
            recordStatusHistory(saved, saved.getStatus());
            return toJobResponse(saved);
        }

        return toJobResponse(existing);
    }

    public void delete(UUID id) {
        DocumentRecord existing = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));

        if (existing.getObjectName() != null && !existing.getObjectName().isBlank()) {
            documentStorageService.deleteFile(existing.getObjectName());
        }

        documentRepository.deleteById(id);
    }

    private DocumentResponse toResponse(DocumentRecord record) {
        return DocumentResponse.builder()
                .id(record.getId())
                .name(record.getName())
                .type(record.getType())
                .tenant(record.getTenant())
                .transactionTypeCode(record.getTransactionTypeCode())
                .version(record.getVersion())
                .status(record.getStatus() != null ? record.getStatus().getCode() : null)
                .contentType(record.getContentType())
                .objectName(record.getObjectName())
                .createdAt(record.getCreatedAt())
                .updatedAt(record.getUpdatedAt())
                .build();
    }

    private JobResponse toJobResponse(DocumentRecord record) {
        return JobResponse.builder()
                .id(record.getId())
                .name(record.getName())
                .type(record.getType())
                .tenant(record.getTenant())
                .transactionTypeCode(record.getTransactionTypeCode())
                .version(record.getVersion())
                .status(record.getStatus() != null ? record.getStatus().getCode() : null)
                .createdAt(record.getCreatedAt())
                .updatedAt(record.getUpdatedAt())
                .build();
    }

    private JobResponse toJobResponse(DocumentResponse response) {
        return JobResponse.builder()
                .id(response.getId())
                .name(response.getName())
                .type(response.getType())
                .tenant(response.getTenant())
                .transactionTypeCode(response.getTransactionTypeCode())
                .version(response.getVersion())
                .status(response.getStatus())
                .createdAt(response.getCreatedAt())
                .updatedAt(response.getUpdatedAt())
                .build();
    }

    private JobStatus resolveStatus(String statusCode) {
        String normalized = statusCode == null || statusCode.isBlank() ? "Indexed" : statusCode.trim();
        return jobStatusRepository.findByCode(normalized)
                .orElseGet(() -> {
                    LocalDateTime now = LocalDateTime.now();
                    JobStatus newStatus = JobStatus.builder()
                            .code(normalized)
                            .description(normalized)
                            .createdAt(now)
                            .updatedAt(now)
                            .build();
                    return jobStatusRepository.save(newStatus);
                });
    }

    private void recordStatusHistory(DocumentRecord document, JobStatus status) {
        if (document == null || status == null) {
            return;
        }

        JobStatusHistory history = JobStatusHistory.builder()
                .document(document)
                .status(status)
                .changedAt(LocalDateTime.now())
                .build();

        jobStatusHistoryRepository.save(history);
    }

    public List<JobStatusHistoryResponse> getStatusHistory(UUID documentId) {
        DocumentRecord document = getDocumentEntityById(documentId);
        return jobStatusHistoryRepository.findByDocumentOrderByChangedAtAsc(document)
                .stream()
                .map(this::toHistoryResponse)
                .toList();
    }

    private JobStatusHistoryResponse toHistoryResponse(JobStatusHistory history) {
        return JobStatusHistoryResponse.builder()
                .id(history.getId())
                .documentId(history.getDocument() != null ? history.getDocument().getId() : null)
                .statusCode(history.getStatus() != null ? history.getStatus().getCode() : null)
                .statusDescription(history.getStatus() != null ? history.getStatus().getDescription() : null)
                .changedAt(history.getChangedAt())
                .build();
    }

    private String statusCode(JobStatus status) {
        return status == null ? null : status.getCode();
    }

    private String buildObjectName(UUID documentId, String tenant, String type, String originalFileName) {
        String safeTenant = tenant == null ? "tenant" : tenant.replaceAll("[^a-zA-Z0-9_.-]", "_");
        String safeType = type == null ? "PDF" : type.replaceAll("[^a-zA-Z0-9_.-]", "_");
        String safeFileName = originalFileName == null ? "file" : originalFileName.replaceAll("[^a-zA-Z0-9_.-]", "_");
        return String.format("tenant-%s/%s/%s", safeTenant, safeType, safeFileName);
    }

    private String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            return "PDF";
        }
        return type.trim().toUpperCase();
    }

    private String normalizeContentType(String contentType, String name) {
        if (contentType != null && !contentType.isBlank()) {
            return contentType;
        }
        if (name != null && name.toLowerCase().endsWith(".xml")) {
            return "application/xml";
        }
        if (name != null && name.toLowerCase().endsWith(".txt")) {
            return "text/plain";
        }
        return "application/pdf";
    }
}
