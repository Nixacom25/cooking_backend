package com.cooked.backend.service.impl;

import com.cooked.backend.entity.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiLanguageInstructionTest {

    private static User userWithLanguage(String language) {
        User user = new User();
        user.setLanguage(language);
        return user;
    }

    @Test
    void englishOrUnknownLanguageLeavesPromptUnchanged() {
        assertThat(AiServiceImpl.languageInstruction(null)).isEmpty();
        assertThat(AiServiceImpl.languageInstruction(userWithLanguage(null))).isEmpty();
        assertThat(AiServiceImpl.languageInstruction(userWithLanguage("US English"))).isEmpty();
    }

    @Test
    void frenchAndSpanishAskForTranslatedValuesButEnglishKeys() {
        assertThat(AiServiceImpl.languageInstruction(userWithLanguage("FR Français")))
                .contains("French").contains("JSON keys");
        assertThat(AiServiceImpl.languageInstruction(userWithLanguage("ES Español")))
                .contains("Spanish");
    }
}
