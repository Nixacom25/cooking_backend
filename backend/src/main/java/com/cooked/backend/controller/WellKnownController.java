package com.cooked.backend.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller to serve .well-known files required for App Links (Android)
 * and Universal Links (iOS).
 */
@RestController
@RequestMapping("/.well-known")
public class WellKnownController {

    @Value("${app.android.sha256:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00}")
    private String androidSha256;

    @Value("${app.android.package:com.cookedapp.app}")
    private String androidPackageName;

    @Value("${app.ios.teamId:AP5DNHH44A}")
    private String iosTeamId;

    @Value("${app.ios.bundleId:com.cookedapp.app}")
    private String iosBundleId;

    @GetMapping(value = "/assetlinks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public String getAssetLinks() {
        return "[\n" +
                "  {\n" +
                "    \"relation\": [\"delegate_permission/common.handle_all_urls\"],\n" +
                "    \"target\": {\n" +
                "      \"namespace\": \"android_app\",\n" +
                "      \"package_name\": \"" + androidPackageName + "\",\n" +
                "      \"sha256_cert_fingerprints\": [\"" + androidSha256 + "\"]\n" +
                "    }\n" +
                "  }\n" +
                "]";
    }

    @GetMapping(value = "/apple-app-site-association", produces = MediaType.APPLICATION_JSON_VALUE)
    public String getAppleAppSiteAssociation() {
        return "{\n" +
                "  \"applinks\": {\n" +
                "    \"apps\": [],\n" +
                "    \"details\": [\n" +
                "      {\n" +
                "        \"appID\": \"" + iosTeamId + "." + iosBundleId + "\",\n" +
                "        \"paths\": [ \"/share/recipes/*\" ]\n" +
                "      }\n" +
                "    ]\n" +
                "  }\n" +
                "}";
    }
}
