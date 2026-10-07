package com.cooked.backend.controller;

import com.cooked.backend.dto.request.AskCookedRequest;
import com.cooked.backend.dto.response.AskCookedResponse;
import com.cooked.backend.service.AskCookedService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Ask Cooked. HTTP only: the work is in {@link AskCookedService}. */
@RestController
@RequestMapping("/api/admin/ask")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Ask Cooked", description = "Natural-language questions answered from read-only admin data")
public class AskCookedController {

    private final AskCookedService askCookedService;

    @Operation(summary = "Ask a question (≤ 500 chars, 30 per admin per hour)")
    @PostMapping
    public ResponseEntity<AskCookedResponse> ask(@Valid @RequestBody AskCookedRequest request, Authentication auth) {
        return ResponseEntity.ok(askCookedService.ask(request.getQuestion(), auth.getName()));
    }
}
