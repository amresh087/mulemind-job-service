package com.mulemind.job.config;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

import com.mulemind.job.dto.DocumentRequest;
import com.mulemind.job.repository.DocumentRepository;
import com.mulemind.job.service.DocumentService;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
@Component
@RequiredArgsConstructor
public class DocumentDataInitializer {

    private final DocumentRepository documentRepository;
    private final DocumentService documentService;

    @PostConstruct
    public void seedDocuments() {
        List<DocumentSeed> seeds = new ArrayList<>();
        seeds.add(new DocumentSeed("generated-idoc-content.xml", "XML", "Levi", "TRX001", "v1", "Indexed", "application/xml"));
        seeds.add(new DocumentSeed("orders.edi.txt", "TXT", "Levi", "TRX002", "v1", "Indexed", "text/plain"));
        seeds.add(new DocumentSeed("merged-xslt-templet.xml", "XML", "Levi", "TRX003", "v1", "Indexed", "application/xml"));

        for (DocumentSeed seed : seeds) {
            if (documentRepository.findByName(seed.name).isEmpty()) {
                DocumentRequest request = new DocumentRequest();
                request.setName(seed.name);
                request.setType(seed.type);
                request.setTenant(seed.tenant);
                request.setTransactionTypeCode(seed.transactionTypeCode);
                request.setVersion(seed.version);
                request.setStatus(seed.status);
                request.setContentType(seed.contentType);
                documentService.create(request);
            }
        }
    }

    private static class DocumentSeed {
        private final String name;
        private final String type;
        private final String tenant;
        private final String transactionTypeCode;
        private final String version;
        private final String status;
        private final String contentType;

        DocumentSeed(String name, String type, String tenant, String transactionTypeCode, String version, String status, String contentType) {
            this.name = name;
            this.type = type;
            this.tenant = tenant;
            this.transactionTypeCode = transactionTypeCode;
            this.version = version;
            this.status = status;
            this.contentType = contentType;
        }
    }
}
