package com.cooked.backend.service.impl;

import com.cooked.backend.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GiftServiceImplTest {

    @Test
    void normalize_acceptsCommonTypingVariants() {
        assertEquals("COOK-ABCD-EFGH-JKMN", GiftServiceImpl.normalize("COOK-ABCD-EFGH-JKMN"));
        assertEquals("COOK-ABCD-EFGH-JKMN", GiftServiceImpl.normalize(" cook abcd efgh jkmn "));
        assertEquals("COOK-ABCD-EFGH-JKMN", GiftServiceImpl.normalize("abcdefghjkmn"));
    }

    @Test
    void normalize_rejectsWrongLength() {
        assertThrows(BadRequestException.class, () -> GiftServiceImpl.normalize("COOK-ABCD"));
        assertThrows(BadRequestException.class, () -> GiftServiceImpl.normalize(null));
    }
}
