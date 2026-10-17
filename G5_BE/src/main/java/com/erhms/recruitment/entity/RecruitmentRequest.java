package com.erhms.recruitment.entity;

import com.erhms.recruitment.enums.RequestStatus;
import com.erhms.recruitment.enums.WorkShift;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "recruitment_requests")
public class RecruitmentRequest {

    @Id
    public UUID id;

    @Column(name = "created_by", nullable = false)
    public Long createdBy;

    @Column(nullable = false, length = 160)
    public String title;

    @Column(nullable = false)
    public int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public WorkShift shift;

    @Column(nullable = false, length = 2000)
    public String skills;

    @Column(nullable = false, length = 200)
    public String location;

    @Column(name = "needed_by", nullable = false)
    public LocalDate neededBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    public RequestStatus status;

    @Column(name = "review_note", length = 1000)
    public String reviewNote;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    @Version
    public long version;
}
