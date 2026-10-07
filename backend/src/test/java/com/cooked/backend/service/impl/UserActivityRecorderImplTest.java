package com.cooked.backend.service.impl;

import com.cooked.backend.repository.UserActivityDayRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserActivityRecorderImplTest {

    @Mock private UserActivityDayRepository repository;
    @InjectMocks private UserActivityRecorderImpl recorder;

    @Test
    void recordsOncePerDay() {
        UUID id = UUID.randomUUID();
        when(repository.existsByUserIdAndDay(eq(id), any())).thenReturn(false, true);
        recorder.recordToday(id);
        recorder.recordToday(id);
        verify(repository, times(1)).save(any());
    }

    @Test
    void ignoresMissingUser() {
        recorder.recordToday(null);
        verifyNoInteractions(repository);
    }
}
