package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.TestPushRequest;
import com.cooked.backend.entity.User;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.service.PushNotificationService;
import com.cooked.backend.service.TestPushService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TestPushServiceImpl implements TestPushService {

    private final UserRepository users;
    private final PushNotificationService push;

    @Override
    public void send(TestPushRequest r) {
        User u = users.findByEmail(r.email().trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new BadRequestException("No Cooked account with this email."));
        if (u.getFcmToken() == null || u.getFcmToken().isBlank()) {
            throw new BadRequestException("This account has no device registered for push: sign in to the app on a phone and allow notifications.");
        }
        push.sendPush(u.getFcmToken(), "[Test] " + r.title().trim(), r.body().trim(), Map.of("type", "TEST"));
    }
}
