package com.cooked.backend.service;

import com.cooked.backend.dto.request.CreatorApplicationRequest;
import com.cooked.backend.dto.response.CreatorApplicationResponse;
import com.cooked.backend.dto.response.PagedResponse;
import com.cooked.backend.entity.CreatorApplicationStatus;

import java.util.Map;
import java.util.UUID;

/** Creator / ambassador applications from the website, reviewed in the backoffice. */
public interface CreatorApplicationService {

    /** Public: store an application (rate limited per client). */
    void submit(CreatorApplicationRequest request, String clientKey);

    PagedResponse<CreatorApplicationResponse> list(CreatorApplicationStatus status, int page, int size);

    Map<String, Long> counts();

    /** Moves an application; APPROVED ambassador applications create the ambassador. Emails the applicant (except REVIEWING). */
    CreatorApplicationResponse decide(UUID id, CreatorApplicationStatus status, String message, String adminEmail);
}
