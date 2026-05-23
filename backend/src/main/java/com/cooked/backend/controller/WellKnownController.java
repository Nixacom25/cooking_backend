package com.cooked.backend.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/.well-known")
public class WellKnownController {

    @Value("${app.deep-link.android.package-name:com.cookedapp.app}")
    private String androidPackageName;

    @Value("${app.deep-link.android.sha256:YOUR_ANDROID_SHA256}")
    private String androidSha256;

    @Value("${app.deep-link.ios.app-id:YOUR_APPLE_TEAM_ID.com.cookedapp.app}")
    private String iosAppId;

    @GetMapping(value = "/assetlinks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Map<String, Object>>> getAssetLinks() {
        Map<String, Object> target = Map.of(
                "namespace", "android_app",
                "package_name", androidPackageName,
                "sha256_cert_fingerprints", List.of(androidSha256)
        );

        Map<String, Object> relation = Map.of(
                "relation", List.of("delegate_permission/common.handle_all_urls"),
                "target", target
        );

        return ResponseEntity.ok(List.of(relation));
    }

    @GetMapping(value = "/apple-app-site-association", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> getAppleAppSiteAssociation() {
        Map<String, Object> details = Map.of(
                "appID", iosAppId,
                "paths", List.of("*")
        );

        Map<String, Object> applinks = Map.of(
                "apps", List.of(),
                "details", List.of(details)
        );

        return ResponseEntity.ok(Map.of("applinks", applinks));
    }
}
