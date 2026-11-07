package com.cooked.backend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;

class AppLinkControllerTest {

    @Test
    void manageSubscriptionPageLinksDirectlyToBothStores() {
        var response = new AppLinkController().manageSubscription();

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_HTML);
        assertThat(response.getBody())
                .contains("https://apps.apple.com/account/subscriptions")
                .contains("https://play.google.com/store/account/subscriptions?package=com.cookedapp.app")
                .contains("support@cookedapp.com");
    }
}
