package com.cooked.backend.service;

import com.cooked.backend.dto.response.SystemStatusResponse;

/** Live health of the platform (admin System health). */
public interface SystemStatusService {

    SystemStatusResponse status();
}
