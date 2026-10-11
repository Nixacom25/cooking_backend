package com.cooked.backend.service.impl;

import com.cooked.backend.entity.ProductEvent;
import com.cooked.backend.repository.ProductEventRepository;
import com.cooked.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists product events off the request thread, in their own transaction:
 * recording never slows down nor fails the user's request.
 */
@Component
@RequiredArgsConstructor
public class ProductEventWriter {

    private static final Logger log = LoggerFactory.getLogger(ProductEventWriter.class);

    private final ProductEventRepository repository;
    private final UserRepository userRepository;

    /** Lets custom automations react to failed imports (optional: absent in unit tests). */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private org.springframework.context.ApplicationEventPublisher events;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(ProductEvent event, String userEmail) {
        try {
            if (userEmail != null) {
                userRepository.findByEmail(userEmail).ifPresent(u -> event.setUserId(u.getId()));
            }
            repository.save(event);
            if (events != null && event.getType() == com.cooked.backend.entity.ProductEventType.IMPORT && !event.isSuccess() && event.getUserId() != null) {
                events.publishEvent(new com.cooked.backend.service.AutomationTriggerEvent("IMPORT_FAILED", event.getUserId(), userEmail, event.getDetail()));
            }
        } catch (RuntimeException e) {
            log.warn("Could not record product event {}: {}", event.getType(), e.getMessage());
        }
    }
}
