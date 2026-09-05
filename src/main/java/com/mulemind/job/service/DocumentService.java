package com.mulemind.job.service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.mulemind.job.dto.DocumentKafkaEvent;
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
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${spring.kafka.topics.mulemind-events}")
    private String documentEventsTopic;


    /**
     * Creates a new document record based on the provided request.
     * @param request
     * @return
     */
    public DocumentResponse create(DocumentRequest request) {
        DocumentRecord record = DocumentRecord.builder()
                .id(UUID.randomUUID())
                .name(request.getName())
                .type(normalizeType(request.getType()))
                .tenant(request.getTenant())
                .status(createStatus(request.getStatus(), request.getDescription()))
                .contentType(normalizeContentType(request.getContentType(), request.getName()))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        DocumentRecord saved = documentRepository.save(record);
        recordStatusHistory(saved, saved.getStatus());
        return toResponse(saved);
    }

    /**
     * Creates a new document record with an associated file.
     * @param request
     * @param file
     * @return
     */

    public DocumentResponse createWithFile(DocumentRequest request, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File must be provided for upload");
        }

        UUID documentId = UUID.randomUUID();
        String contentType = normalizeContentType(request.getContentType(), file.getOriginalFilename());
        String objectName = buildObjectName(documentId,
                request.getTenant(),normalizeType(request.getType()),file.getOriginalFilename());

        String storageKey;
        try {
            storageKey = documentStorageService.storeFile(documentId, objectName, contentType,
                    file.getInputStream(), file.getSize());
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to store uploaded file", ex);
        }

        String initialStatus = storageKey!= null && !storageKey.isBlank()? "UPLOADED":"CREATED";

        DocumentRecord record = DocumentRecord.builder()
                .id(documentId)
                .name(request.getName() != null ? request.getName() : file.getOriginalFilename())
                .type(normalizeType(request.getType()))
                .tenant(request.getTenant())
                .status(createStatus(initialStatus, request.getDescription()))
                .contentType(contentType)
                .objectName(storageKey)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        DocumentRecord saved = documentRepository.save(record);
        recordStatusHistory(saved, saved.getStatus());
        // Publish document event
        publishDocumentEvent(saved);

        return toResponse(saved);
    }

    /**
     * Retrieves all documents.
     * @return
     */
    public List<DocumentResponse> getAll() {
        return documentRepository.findAll().stream().map(this::toResponse).toList();
    }

    /**
     * Retrieves all jobs.
     * @return
     */
    public List<JobResponse> getAllJobs() {
        return documentRepository.findAll().stream().map(this::toJobResponse).toList();
    }

    /**
     * Retrieves a document by its ID.
     * @param id
     * @return
     */
    public DocumentResponse getById(UUID id) {
        DocumentRecord record = getDocumentEntityById(id);
        return toResponse(record);
    }

    /**
     * Retrieves a job by its ID.
     * @param id
     * @return
     */
    public JobResponse getJobById(UUID id) {
        return toJobResponse(getDocumentEntityById(id));
    }

    /**
     * Retrieves the DocumentRecord entity by its ID.
     * @param id
     * @return
     */
    public DocumentRecord getDocumentEntityById(UUID id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
    }

    /**
     * Updates a document by its ID.
     * @param id
     * @param request
     * @return
     */
    public DocumentResponse update(UUID id, DocumentRequest request) {
        DocumentRecord existing = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));

        String updatedName = request.getName() != null ? request.getName() : existing.getName();
        String updatedType = normalizeType(request.getType() != null ? request.getType() : existing.getType());
        String updatedTenant = request.getTenant() != null ? request.getTenant() : existing.getTenant();
        JobStatus updatedStatus = resolveStatus(request.getStatus() != null ? request.getStatus() : statusCode(existing.getStatus()), request.getDescription());
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

    /**
     * Updates a job by its ID.
     * @param id
     * @param request
     * @return
     */
    public JobResponse updateJob(UUID id, DocumentRequest request) {
        return toJobResponse(update(id, request));
    }

    /**
     * Updates the job status for a document by its ID.
     * @param id
     * @param request
     * @return
     */
    public JobResponse updateJobStatus(UUID id, DocumentRequest request) {
        DocumentRecord existing = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
        String previousStatusCode = statusCode(existing.getStatus());

        String newStatusCode = request.getStatus();
        String newDescription = request.getDescription();
        if (newStatusCode == null || newStatusCode.isBlank()) {
            throw new IllegalArgumentException("Status cannot be blank");
        }

        JobStatus newStatus = null;
        if(existing!=null && existing.getStatus()!=null){
            newStatus = resolveStatus(newStatusCode, newDescription);
        }else{
            newStatus = resolveStatus(newStatusCode, newDescription);
        }


        if (!Objects.equals(previousStatusCode, newStatus.getCode())) {
            existing.setStatus(newStatus);
            existing.setUpdatedAt(LocalDateTime.now());
            DocumentRecord saved = documentRepository.save(existing);
            recordStatusHistory(saved, saved.getStatus());
            return toJobResponse(saved);
        }

        return toJobResponse(existing);
    }

    /**
     * Deletes a document by its ID.
     * @param id
     */
    public void delete(UUID id) {
        DocumentRecord existing = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));

        List<JobStatusHistory> historyEntries = jobStatusHistoryRepository.findByDocumentOrderByChangedAtAsc(existing);
        if (!historyEntries.isEmpty()) {
            jobStatusHistoryRepository.deleteAll(historyEntries);
        }

        if (existing.getObjectName() != null && !existing.getObjectName().isBlank()) {
            documentStorageService.deleteDocumentFiles(id, existing.getObjectName());
        }

        documentRepository.delete(existing);
    }

    /**
     * Converts a DocumentRecord entity to a DocumentResponse DTO.
     * @param record
     * @return
     */
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

    /**
     * Converts a DocumentRecord entity to a JobResponse DTO.
     * @param record
     * @return
     */
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

    /**
     * Converts a DocumentResponse DTO to a JobResponse DTO.
     * @param response
     * @return
     */
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

    /**
     * Resolves the JobStatus entity based on the provided status code.
     * If the status code is not found, a new JobStatus entity is created and saved.
     * @param statusCode
     * @return
     */
    private JobStatus resolveStatus(String statusCode, String description) {
        String normalized = statusCode == null || statusCode.isBlank() ? "Created" : statusCode.trim();
        String normalizedDescription = description == null || description.isBlank() ? "Created" : description.trim();
        return jobStatusRepository.findFirstByCodeOrderByCreatedAtAsc(normalized)
                .orElseGet(() -> {
                    LocalDateTime now = LocalDateTime.now();
                    JobStatus newStatus = JobStatus.builder()
                            .code(normalized)
                            .description(normalizedDescription)
                            .createdAt(now)
                            .updatedAt(now)
                            .build();
                    return jobStatusRepository.save(newStatus);
                });
    }

    private JobStatus createStatus(String statusCode, String description) {
        return resolveStatus(statusCode, description);
    }

    /**
     * Records the status history for a document.
     * @param document
     * @param status
     */
    private void recordStatusHistory(DocumentRecord document, JobStatus status) {
        if (document == null || status == null) {
            return;
        }

        JobStatusHistory history = JobStatusHistory.builder()
        .document(document)
        .status(status)
        .statusCode(status.getCode())
        .statusDescription(status.getDescription())
        .changedAt(LocalDateTime.now())
        .build();

        jobStatusHistoryRepository.save(history);
    }

    /**
     * Publishes the document event to Kafka after a document is created or updated.
     *
     * @param document the persisted document
     */
    private void publishDocumentEvent(DocumentRecord document) {
        if (document == null) {
            return;
        }

        DocumentKafkaEvent event = DocumentKafkaEvent.builder()
                .id(document.getId())
                .name(document.getName())
                .type(document.getType())
                .tenant(document.getTenant())
                .transactionTypeCode(document.getTransactionTypeCode())
                .version(document.getVersion())
                .status(document.getStatus() != null ? document.getStatus().getCode() : null)
                .contentType(document.getContentType())
                .objectName(document.getObjectName())
                .createdAt(document.getCreatedAt())
                .updatedAt(document.getUpdatedAt())
                .build();

        kafkaTemplate.send(documentEventsTopic, document.getId().toString(), event);
    }

    /**
     * Retrieves the status history for a document by its ID.
     * @param documentId
     * @return
     */
    public List<JobStatusHistoryResponse> getStatusHistory(UUID documentId) {
        DocumentRecord document = getDocumentEntityById(documentId);
        return jobStatusHistoryRepository.findByDocumentOrderByChangedAtAsc(document)
                .stream()
                .map(this::toHistoryResponse)
                .toList();
    }

    /**
     * Converts a JobStatusHistory entity to a JobStatusHistoryResponse DTO.
     * @param history
     * @return
     */
    private JobStatusHistoryResponse toHistoryResponse(JobStatusHistory history) {
        return JobStatusHistoryResponse.builder()
                .id(history.getId())
                .documentId(history.getDocument() != null ? history.getDocument().getId() : null)
                .statusCode(history.getStatusCode() != null
                    ? history.getStatusCode()
                    : history.getStatus() != null ? history.getStatus().getCode() : null)
                .statusDescription(history.getStatusDescription() != null
                    ? history.getStatusDescription()
                    : history.getStatus() != null ? history.getStatus().getDescription() : null)
                .changedAt(history.getChangedAt())
                .build();
    }

    /**
     * Retrieves the status code from a JobStatus entity.
     * @param status
     * @return
     */
    private String statusCode(JobStatus status) {
        return status == null ? null : status.getCode();
    }

    /**
     * Builds a safe object name for storage based on the document ID, tenant, type, and original file name.
     * @param documentId
     * @param tenant
     * @param type
     * @param originalFileName
     * @return
     */
    private String buildObjectName(UUID documentId, String tenant, String type, String originalFileName) {
        String safeTenant = tenant == null ? "tenant" : tenant.replaceAll("[^a-zA-Z0-9_.-]", "_");
        String safeType = type == null ? "PDF" : type.replaceAll("[^a-zA-Z0-9_.-]", "_");
        String safeFileName = originalFileName == null ? "file" : originalFileName.replaceAll("[^a-zA-Z0-9_.-]", "_");
        return String.format("tenant-%s/%s/%s/%s", safeTenant, safeType, documentId, safeFileName);
    }

    /**
     * Normalizes the document type. If null or blank, defaults to "PDF".
     * @param type
     * @return
     */
    private String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            return "PDF";
        }
        return type.trim().toUpperCase();
    }

    /**
     * Normalizes the content type based on the file name.
     * @param contentType
     * @param name
     * @return
     */
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
