package com.mulemind.job.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.mulemind.job.dto.DocumentRequest;
import com.mulemind.job.dto.DocumentResponse;
import com.mulemind.job.entity.DocumentRecord;
import com.mulemind.job.repository.DocumentRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentStorageService documentStorageService;
    private final DocumentRepository documentRepository;

    public DocumentResponse create(DocumentRequest request) {
        DocumentRecord record = DocumentRecord.builder()
                .id(UUID.randomUUID())
                .name(request.getName())
                .type(normalizeType(request.getType()))
                .tenant(request.getTenant())
                .transactionTypeCode(request.getTransactionTypeCode())
                .version(request.getVersion())
                .status(request.getStatus() != null ? request.getStatus() : "Indexed")
                .contentType(normalizeContentType(request.getContentType(), request.getName()))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        DocumentRecord saved = documentRepository.save(record);
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
                .transactionTypeCode(request.getTransactionTypeCode())
                .version(request.getVersion())
                .status(request.getStatus() != null ? request.getStatus() : "Indexed")
                .contentType(contentType)
                .objectName(storageKey)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        DocumentRecord saved = documentRepository.save(record);
        return toResponse(saved);
    }

    public List<DocumentResponse> getAll() {
        return documentRepository.findAll().stream().map(this::toResponse).toList();
    }

    public DocumentResponse getById(UUID id) {
        DocumentRecord record = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
        return toResponse(record);
    }

    public DocumentResponse update(UUID id, DocumentRequest request) {
        DocumentRecord existing = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));

        String updatedName = request.getName() != null ? request.getName() : existing.getName();
        String updatedType = normalizeType(request.getType() != null ? request.getType() : existing.getType());
        String updatedTenant = request.getTenant() != null ? request.getTenant() : existing.getTenant();
        String updatedVersion = request.getVersion() != null ? request.getVersion() : existing.getVersion();
        String updatedStatus = request.getStatus() != null ? request.getStatus() : existing.getStatus();
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
                .transactionTypeCode(request.getTransactionTypeCode() != null ? request.getTransactionTypeCode() : existing.getTransactionTypeCode())
                .version(updatedVersion)
                .status(updatedStatus)
                .contentType(updatedContentType)
                .objectName(newObjectName)
                .createdAt(existing.getCreatedAt())
                .updatedAt(LocalDateTime.now())
                .build();
        DocumentRecord saved = documentRepository.save(updated);
        return toResponse(saved);
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
                .status(record.getStatus())
                .contentType(record.getContentType())
                .objectName(record.getObjectName())
                .createdAt(record.getCreatedAt())
                .updatedAt(record.getUpdatedAt())
                .build();
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
