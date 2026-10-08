package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.TestPushRequest;
import com.cooked.backend.entity.User;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.service.PushNotificationService;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class TestPushServiceImplTest {

    private final UserRepository users = mock(UserRepository.class);
    private final PushNotificationService push = mock(PushNotificationService.class);
    private final TestPushServiceImpl service = new TestPushServiceImpl(users, push);

    @Test
    void sendsToTheAccountDeviceOrExplainsWhy() {
        User withDevice = User.builder().email("boss@cooked.app").build();
        withDevice.setFcmToken("tok");
        when(users.findByEmail("boss@cooked.app")).thenReturn(Optional.of(withDevice));
        service.send(new TestPushRequest(" Boss@Cooked.app ", "Hello", "Body"));
        verify(push).sendPush("tok", "[Test] Hello", "Body", Map.of("type", "TEST"));

        when(users.findByEmail("nodevice@x.com")).thenReturn(Optional.of(User.builder().email("nodevice@x.com").build()));
        assertThrows(BadRequestException.class, () -> service.send(new TestPushRequest("nodevice@x.com", "t", "b")));
        when(users.findByEmail("ghost@x.com")).thenReturn(Optional.empty());
        assertThrows(BadRequestException.class, () -> service.send(new TestPushRequest("ghost@x.com", "t", "b")));
    }
}
