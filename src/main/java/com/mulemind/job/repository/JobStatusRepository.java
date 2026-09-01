package com.mulemind.job.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.mulemind.job.entity.JobStatus;

@Repository
public interface JobStatusRepository extends JpaRepository<JobStatus, UUID> {
    Optional<JobStatus> findByCode(String code);
}
