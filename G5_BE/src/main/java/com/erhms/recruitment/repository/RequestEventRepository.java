package com.erhms.recruitment.repository;

import com.erhms.recruitment.entity.RequestEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequestEventRepository extends JpaRepository<RequestEvent, UUID> {

    List<RequestEvent> findByRequestIdOrderByOccurredAtAsc(UUID requestId);
}
