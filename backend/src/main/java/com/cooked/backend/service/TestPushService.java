package com.cooked.backend.service;

import com.cooked.backend.dto.request.TestPushRequest;

/** Sends a test push before a campaign goes out. */
public interface TestPushService {

    /** @throws com.cooked.backend.exception.BadRequestException when the account has no device registered for push */
    void send(TestPushRequest request);
}
