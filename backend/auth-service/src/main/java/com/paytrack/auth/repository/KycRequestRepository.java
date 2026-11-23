package com.paytrack.auth.repository;

import com.paytrack.auth.entity.KycRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KycRequestRepository extends JpaRepository<KycRequest, String> {

    List<KycRequest> findByUserId(String userId);

    List<KycRequest> findByUserIdAndStatus(String userId, KycRequest.KycRequestStatus status);

    @Query("SELECT kr FROM KycRequest kr WHERE kr.userId = :userId ORDER BY kr.submittedAt DESC")
    Optional<KycRequest> findLatestByUserId(@Param("userId") String userId);

    @Query("SELECT kr FROM KycRequest kr WHERE kr.status = :status ORDER BY kr.submittedAt ASC")
    List<KycRequest> findByStatusOrderBySubmittedAtAsc(@Param("status") KycRequest.KycRequestStatus status);

    @Query("SELECT kr FROM KycRequest kr WHERE kr.status = :status ORDER BY kr.submittedAt ASC")
    List<KycRequest> findByStatus(@Param("status") KycRequest.KycRequestStatus status);
}