package com.cooked.backend.service;

import com.cooked.backend.dto.request.AmbassadorRequest;
import com.cooked.backend.dto.response.AmbassadorDetailResponse;
import com.cooked.backend.dto.response.AmbassadorResponse;
import com.cooked.backend.dto.response.AmbassadorsResponse;
import com.cooked.backend.entity.Ambassador;
import com.cooked.backend.entity.User;

import java.util.Optional;
import java.util.UUID;

/** Ambassador program: codes, attribution, commissions and payouts. */
public interface AmbassadorService {

    AmbassadorsResponse list(int days);

    AmbassadorDetailResponse detail(UUID id, int days);

    AmbassadorResponse create(AmbassadorRequest request);

    AmbassadorResponse update(UUID id, AmbassadorRequest request);

    /** Freezes and marks a finished month as paid. */
    AmbassadorDetailResponse markPaid(UUID id, String month, String adminEmail);

    /** Records a link visit; returns the active ambassador for that code, if any. */
    Optional<Ambassador> recordClick(String code);

    /** Attributes [user] to the ambassador owning [code] (first code wins); empty when the code is not an active ambassador code. */
    Optional<Ambassador> attachReferral(User user, String code);
}
