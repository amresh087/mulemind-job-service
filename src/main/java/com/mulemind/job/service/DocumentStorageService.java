package com.mulemind.job.service;

import java.io.InputStream;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.minio.BucketExistsArgs;
import io.minio.CopyObjectArgs;
import io.minio.CopySource;
import io.minio.MakeBucketArgs;
import io.minio.ListObjectsArgs;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.MinioClient;
import io.minio.Result;
import io.minio.messages.Item;

@Service
public class DocumentStorageService {

    private final MinioClient minioClient;
    private final String bucketName;

    public DocumentStorageService(MinioClient minioClient,
                                  @Value("${minio.bucket}") String bucketName) {
        this.minioClient = minioClient;
        this.bucketName = bucketName;
        createBucketIfNeeded();
    }

    private void createBucketIfNeeded() {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to initialize MinIO bucket", ex);
        }
    }

    public String storeFile(UUID documentId, String objectName, String contentType, InputStream data, long size) {
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .stream(data, size, -1)
                    .contentType(contentType)
                    .build());
            return objectName;
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to store file in MinIO", ex);
        }
    }

    public void deleteFile(String objectName) {
        if (objectName == null || objectName.isBlank()) {
            return;
        }
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .build());
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to delete file from MinIO", ex);
        }
    }

    public void deleteDocumentFiles(UUID documentId, String objectName) {
        if (documentId == null || objectName == null || objectName.isBlank()) {
            return;
        }

        int tenantSeparator = objectName.indexOf('/');
        String tenantPrefix = tenantSeparator > 0 ? objectName.substring(0, tenantSeparator) + "/" : "";
        String documentPath = "/" + documentId + "/";
        String documentFolder = "/" + documentId;

        try {
            for (Result<Item> itemResult : minioClient.listObjects(ListObjectsArgs.builder()
                    .bucket(bucketName)
                    .prefix(tenantPrefix)
                    .recursive(true)
                    .build())) {
                String candidate = itemResult.get().objectName();
                if (candidate.contains(documentPath) || candidate.endsWith(documentFolder)) {
                    minioClient.removeObject(RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(candidate)
                            .build());
                }
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to delete document files from MinIO", ex);
        }
    }

    public void renameFile(String sourceObjectName, String targetObjectName) {
        if (sourceObjectName == null || sourceObjectName.isBlank() || sourceObjectName.equals(targetObjectName)) {
            return;
        }
        try {
            minioClient.copyObject(CopyObjectArgs.builder()
                    .bucket(bucketName)
                    .object(targetObjectName)
                    .source(CopySource.builder()
                            .bucket(bucketName)
                            .object(sourceObjectName)
                            .build())
                    .build());
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucketName)
                    .object(sourceObjectName)
                    .build());
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to rename file in MinIO", ex);
        }
    }
}
