package com.cooked.backend.controller;

import com.cooked.backend.dto.request.CreatorApplicationRequest;
import com.cooked.backend.service.AmbassadorService;
import com.cooked.backend.service.CreatorApplicationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;

/** Public endpoints of the creator programs: applications (create only) and ambassador links. */
@RestController
@RequiredArgsConstructor
@Tag(name = "Public creators", description = "Website applications and ambassador links")
public class PublicCreatorController {

    private final CreatorApplicationService applications;
    private final AmbassadorService ambassadors;

    @Value("${site.url:https://cookedapp.com}")
    private String siteUrl;

    @PostMapping("/public/creator-applications")
    public ResponseEntity<Map<String, Boolean>> apply(@Valid @RequestBody CreatorApplicationRequest request, HttpServletRequest http) {
        applications.submit(request, clientKey(http));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("received", true));
    }

    /** Ambassador link: counts the visit, then opens the website with the code (?ref=CODE). */
    @GetMapping("/r/{code}")
    public ResponseEntity<Void> link(@PathVariable String code) {
        String target = ambassadors.recordClick(code)
                .map(a -> siteUrl + "/?ref=" + a.getCode())
                .orElse(siteUrl);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(target)).build();
    }

    private static String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String[] hops = forwarded.split(",");
            return hops[hops.length - 1].trim();
        }
        return request.getRemoteAddr();
    }
}
