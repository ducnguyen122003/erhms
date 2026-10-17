package com.erhms.recruitment.repository;

import com.erhms.recruitment.entity.RecruitmentRequest;
import com.erhms.recruitment.enums.RequestStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecruitmentRepository extends JpaRepository<RecruitmentRequest, UUID> {

    @Query("""
            select r from RecruitmentRequest r
            where (:userId is null or r.createdBy = :userId)
                and (:status is null or r.status = :status)
                and lower(r.title) like lower(concat('%', :q, '%'))
            """)
    Page<RecruitmentRequest> search(
            @Param("userId") Long userId,
            @Param("status") RequestStatus status,
            @Param("q") String q,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RecruitmentRequest r where r.id = :id")
    Optional<RecruitmentRequest> lockById(@Param("id") UUID id);
}
