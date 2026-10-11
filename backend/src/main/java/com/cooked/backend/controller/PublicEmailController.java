package com.cooked.backend.controller;

import com.cooked.backend.service.EmailCampaignService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** Unsubscribe link of campaign emails (GET from the link, POST for one-click unsubscribe). */
@RestController
@RequestMapping("/public/email")
@RequiredArgsConstructor
@Tag(name = "Public email", description = "Marketing email unsubscribe")
public class PublicEmailController {

    private final EmailCampaignService campaigns;

    @RequestMapping(value = "/unsubscribe", method = {RequestMethod.GET, RequestMethod.POST}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> unsubscribe(@RequestParam("u") String userId, @RequestParam("t") String token) {
        boolean ok;
        try {
            ok = campaigns.unsubscribe(UUID.fromString(userId), token);
        } catch (IllegalArgumentException e) {
            ok = false;
        }
        String msg = ok
                ? "<h1>You're unsubscribed</h1><p>You won't get Cooked news and offers by email any more. Emails about your account (security, purchases) still arrive.</p>"
                : "<h1>This link doesn't work</h1><p>It may be incomplete. Use the link from the latest email, or write to contact@cookedapp.com.</p>";
        return ResponseEntity.status(ok ? 200 : 400).body("<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width\">"
                + "<title>Cooked</title></head><body style=\"font-family:Arial,sans-serif;max-width:520px;margin:60px auto;padding:0 20px;color:#141414\">"
                + "<div style=\"color:#C4161C;font-weight:700;font-size:22px;margin-bottom:24px\">Cooked</div>" + msg + "</body></html>");
    }
}
