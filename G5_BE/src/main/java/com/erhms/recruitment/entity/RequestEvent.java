package com.erhms.recruitment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "request_events")
public class RequestEvent {

    @Id
    public UUID id;

    @Column(name = "request_id", nullable = false)
    public UUID requestId;

    @Column(name = "actor_id", nullable = false)
    public Long actorId;

    @Column(nullable = false, length = 40)
    public String action;

    @Column(length = 1000)
    public String note;

    @Column(name = "occurred_at", nullable = false)
    public Instant occurredAt;
}
