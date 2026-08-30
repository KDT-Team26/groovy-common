package com.groovy.backend.outbox;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

	List<OutboxEvent> findTop50ByPublishedFalseOrderByIdAsc();
}
