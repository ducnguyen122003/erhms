package com.erhms.consultation.repository;

import com.erhms.consultation.entity.ConsultationRegistration;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsultationRegistrationRepository extends JpaRepository<ConsultationRegistration, UUID> {
}
