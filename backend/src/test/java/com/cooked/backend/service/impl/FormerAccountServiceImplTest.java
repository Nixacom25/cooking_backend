package com.cooked.backend.service.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FormerAccountServiceImplTest {

    @Test
    void sameEmailSameHashWhateverTheCaseOrAlias() {
        String h = FormerAccountServiceImpl.hash("Awa.Diop@gmail.com");
        assertEquals(64, h.length());
        assertEquals(h, FormerAccountServiceImpl.hash("  awadiop@GMAIL.com "));
        assertEquals(h, FormerAccountServiceImpl.hash("awa.diop+promo@googlemail.com"));
        assertNotEquals(h, FormerAccountServiceImpl.hash("awa.diop@yahoo.fr"));
        assertNull(FormerAccountServiceImpl.hash(null));
    }

    @Test
    void storesNoReadableEmail() {
        assertFalse(FormerAccountServiceImpl.hash("awa@mail.com").contains("awa"));
    }
}
