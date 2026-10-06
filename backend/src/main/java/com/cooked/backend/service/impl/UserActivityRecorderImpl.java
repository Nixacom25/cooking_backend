package com.cooked.backend.service.impl;

import com.cooked.backend.entity.UserActivityDay;
import com.cooked.backend.repository.UserActivityDayRepository;
import com.cooked.backend.service.UserActivityRecorder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserActivityRecorderImpl implements UserActivityRecorder {

    private final UserActivityDayRepository repository;

    /**
     * Own transaction so a concurrent duplicate (unique key) only rolls back
     * this insert, never the caller's work.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordToday(UUID userId) {
        LocalDate today = LocalDate.now();
        if (userId == null || repository.existsByUserIdAndDay(userId, today)) return;
        repository.save(UserActivityDay.builder().userId(userId).day(today).build());
    }
}
