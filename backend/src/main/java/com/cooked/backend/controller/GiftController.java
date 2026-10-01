package com.cooked.backend.controller;

import com.cooked.backend.dto.response.GiftCodeResponse;
import com.cooked.backend.dto.response.GiftRedeemResponse;
import com.cooked.backend.service.GiftService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Gift cards. Purchases happen in the app (Apple / Google in-app purchase);
 * codes are only ever created by the RevenueCat webhook, never by the client.
 */
@RestController
@RequestMapping("/gifts")
@RequiredArgsConstructor
@Tag(name = "Gifts", description = "Gift codes bought in-app and redeemed by friends")
public class GiftController {

    private final GiftService giftService;

    @Operation(summary = "Gift codes I bought")
    @GetMapping("/mine")
    public ResponseEntity<List<GiftCodeResponse>> getMyGifts(Authentication auth) {
        return ResponseEntity.ok(giftService.getMyGifts(auth.getName()));
    }

    @Operation(summary = "Gift plans sold on the website (server-side prices)")
    @GetMapping("/plans")
    public ResponseEntity<List<Map<String, Object>>> plans() {
        return ResponseEntity.ok(giftService.webPlans());
    }

    @Operation(summary = "Start a website gift purchase (Stripe Checkout)")
    @PostMapping("/checkout")
    public ResponseEntity<Map<String, String>> checkout(@RequestBody Map<String, String> body,
                                                        jakarta.servlet.http.HttpServletRequest request) {
        String url = giftService.startWebCheckout(
                body.get("plan"),
                body.get("purchaserEmail"),
                body.get("recipientEmail"),
                body.get("senderName"),
                clientKey(request));
        return ResponseEntity.ok(Map.of("url", url));
    }

    private static String clientKey(jakarta.servlet.http.HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        // Last hop = the address our proxy (Render) saw; the first entries
        // are client-controlled and could be spoofed to dodge rate limits.
        if (forwarded != null && !forwarded.isBlank()) {
            String[] hops = forwarded.split(",");
            return hops[hops.length - 1].trim();
        }
        return request.getRemoteAddr();
    }

    @Operation(summary = "Redeem a gift code on my account")
    @PostMapping("/redeem")
    public ResponseEntity<GiftRedeemResponse> redeem(Authentication auth, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(giftService.redeem(auth.getName(), body.get("code")));
    }
}
