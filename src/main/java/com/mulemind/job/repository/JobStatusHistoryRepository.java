package com.mulemind.job.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.mulemind.job.entity.DocumentRecord;
import com.mulemind.job.entity.JobStatusHistory;

@Repository
public interface JobStatusHistoryRepository extends JpaRepository<JobStatusHistory, UUID> {
    @Query("select h from JobStatusHistory h join fetch h.document d join fetch h.status s where d = :document order by h.changedAt asc")
    List<JobStatusHistory> findByDocumentOrderByChangedAtAsc(@Param("document") DocumentRecord document);
}
