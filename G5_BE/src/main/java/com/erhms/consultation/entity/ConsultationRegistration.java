package com.erhms.consultation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "consultation_registrations")
public class ConsultationRegistration {

    @Id
    public UUID id;

    @Column(name = "full_name", nullable = false, length = 120)
    public String fullName;

    @Column(nullable = false, length = 20)
    public String phone;

    @Column(length = 190)
    public String email;

    @Column(name = "contact_time", nullable = false, length = 20)
    public String contactTime;

    @Column(length = 2000)
    public String description;

    @Column(name = "created_date", nullable = false)
    public Instant createdDate;

    @Column(name = "program_name", nullable = false, length = 255)
    public String programName;
}
