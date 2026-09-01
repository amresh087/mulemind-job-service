package com.mulemind.job.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.mulemind.job.entity.DocumentRecord;

@Repository
public interface DocumentRepository extends JpaRepository<DocumentRecord, UUID> {
    Optional<DocumentRecord> findByName(String name);
}
