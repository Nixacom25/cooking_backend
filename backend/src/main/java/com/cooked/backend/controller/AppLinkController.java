package com.cooked.backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Universal Link / App Link targets used by transactional emails (Welcome,
 * new-device sign-in, billing issue, trial ending) so their buttons open the
 * app directly instead of the marketing site. These paths are declared in
 * WellKnownController's apple-app-site-association "paths" list and are
 * covered by assetlinks.json's domain-wide Android App Links grant, so on a
 * device with the app installed, iOS/Android intercepts the tap and launches
 * the app before this endpoint is ever requested. This handler only runs as
 * a graceful fallback (app not installed, or link opened on desktop/a
 * platform without the app) - it just sends the visitor to the marketing
 * site rather than a broken link.
 */
@RestController
@Tag(name = "App Links", description = "Universal Link / App Link fallback targets for email buttons")
public class AppLinkController {

    private static final String MARKETING_SITE = "https://cookedapp.com";

    @Operation(summary = "Open Cooked (Welcome email button fallback)")
    @GetMapping("/open")
    public ResponseEntity<Void> open() {
        return redirectTo(MARKETING_SITE);
    }

    @Operation(summary = "Manage Subscription (billing email button fallback)")
    @GetMapping("/manage-subscription")
    public ResponseEntity<Void> manageSubscription() {
        return redirectTo(MARKETING_SITE);
    }

    @Operation(summary = "Account Security (new sign-in email button fallback)")
    @GetMapping("/security")
    public ResponseEntity<Void> security() {
        return redirectTo(MARKETING_SITE);
    }

    private ResponseEntity<Void> redirectTo(String url) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, url)
                .build();
    }
}
