package com.cooked.backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;
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
 * site, except for subscription management, which offers direct store links.
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
    public ResponseEntity<String> manageSubscription() {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body("""
                        <!doctype html>
                        <html lang="en">
                        <head>
                          <meta charset="utf-8">
                          <meta name="viewport" content="width=device-width, initial-scale=1">
                          <title>Manage your Cooked subscription</title>
                          <style>
                            body{font:16px/1.5 system-ui,sans-serif;max-width:560px;margin:12vh auto;padding:24px;color:#242424}
                            h1{font-size:1.6rem}a{display:block;margin:16px 0;padding:14px 18px;border-radius:12px;background:#c83e3e;color:white;text-align:center;text-decoration:none;font-weight:600}
                            p,small{color:#555}small a{display:inline;padding:0;background:none;color:#a52f2f}
                          </style>
                        </head>
                        <body>
                          <h1>Manage or cancel your Cooked subscription</h1>
                          <p>Subscriptions are cancelled through the store used to start them. Sign in with the same Apple or Google account used for the purchase.</p>
                          <a href="https://apps.apple.com/account/subscriptions">Open Apple subscription settings</a>
                          <a href="https://play.google.com/store/account/subscriptions?package=com.cookedapp.app">Open Google Play subscriptions</a>
                          <p>If Cooked is not listed, check the other store account or contact <a href="mailto:support@cookedapp.com">support@cookedapp.com</a> with your purchase receipt.</p>
                        </body>
                        </html>
                        """);
    }

    @Operation(summary = "Account Security (new sign-in email button fallback)")
    @GetMapping("/security")
    public ResponseEntity<Void> security() {
        return redirectTo(MARKETING_SITE);
    }

    @Operation(summary = "Redeem a gift (gift email button fallback)")
    @GetMapping("/redeem")
    public ResponseEntity<Void> redeem(@RequestParam(required = false) String code) {
        return redirectTo(UriComponentsBuilder.fromUriString(MARKETING_SITE + "/redeem")
                .queryParamIfPresent("code", java.util.Optional.ofNullable(code))
                .build().encode().toUriString());
    }

    @Operation(summary = "Gift Cooked (share link fallback)")
    @GetMapping("/gift")
    public ResponseEntity<Void> gift() {
        return redirectTo(MARKETING_SITE + "/gift");
    }

    private ResponseEntity<Void> redirectTo(String url) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, url)
                .build();
    }
}
