package com.cooked.backend.service.impl;

import com.cooked.backend.entity.ProductEvent;
import com.cooked.backend.entity.ProductEventType;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.exception.PaymentRequiredException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductEventTrackerImplTest {

    @Mock private ProductEventWriter writer;
    @InjectMocks private ProductEventTrackerImpl tracker;

    @Test
    void successIsRecordedAndResultReturned() {
        List<String> out = tracker.track(ProductEventType.WEB_SEARCH, "a@b.c", "  Pizza  ", () -> List.of("x", "y"), List::size);
        assertEquals(2, out.size());
        ArgumentCaptor<ProductEvent> ev = ArgumentCaptor.forClass(ProductEvent.class);
        verify(writer).write(ev.capture(), eq("a@b.c"));
        assertTrue(ev.getValue().isSuccess());
        assertEquals(2, ev.getValue().getResultCount());
        assertEquals("Pizza", ev.getValue().getDetail());
        assertNotNull(ev.getValue().getDurationMs());
    }

    @Test
    void failureIsRecordedAndRethrownUnchanged() {
        BadRequestException boom = new BadRequestException("We couldn't extract a recipe");
        BadRequestException thrown = assertThrows(BadRequestException.class,
                () -> tracker.track(ProductEventType.IMPORT, "a@b.c", "site.com", () -> { throw boom; }, r -> 1));
        assertSame(boom, thrown);
        ArgumentCaptor<ProductEvent> ev = ArgumentCaptor.forClass(ProductEvent.class);
        verify(writer).write(ev.capture(), any());
        assertFalse(ev.getValue().isSuccess());
        assertEquals("We couldn't extract a recipe", ev.getValue().getFailureReason());
    }

    @Test
    void paywallRefusalIsNotCountedAsAFailure() {
        assertThrows(PaymentRequiredException.class,
                () -> tracker.track(ProductEventType.SCAN, "a@b.c", "photo", () -> { throw new PaymentRequiredException("no"); }, r -> 0));
        verifyNoInteractions(writer);
    }

    @Test
    void longDetailsAreShortened() {
        String s = ProductEventTrackerImpl.shorten("x".repeat(500));
        assertEquals(ProductEvent.DETAIL_MAX, s.length());
        assertNull(ProductEventTrackerImpl.shorten("   "));
    }
}
