package com.erhms.consultation.service;

import com.erhms.consultation.dto.ConsultationDtos.Input;
import com.erhms.consultation.dto.ConsultationDtos.Receipt;
import com.erhms.consultation.entity.ConsultationRegistration;
import com.erhms.consultation.repository.ConsultationRegistrationRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsultationService {

    private final ConsultationRegistrationRepository registrations;

    public ConsultationService(ConsultationRegistrationRepository registrations) {
        this.registrations = registrations;
    }

    @Transactional
    public Receipt register(Input input) {
        var registration = new ConsultationRegistration();
        registration.id = UUID.randomUUID();
        registration.fullName = input.fullName();
        registration.phone = input.phone();
        registration.email = input.email();
        registration.contactTime = input.contactTime();
        registration.description = input.description();
        registration.createdDate = Instant.now();
        registration.programName = input.programName();
        registrations.saveAndFlush(registration);
        return new Receipt(registration.id, registration.fullName, registration.createdDate);
    }
}
