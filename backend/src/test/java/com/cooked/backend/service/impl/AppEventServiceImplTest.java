package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.AppEventsRequest;
import com.cooked.backend.dto.request.SiteVisitRequest;
import com.cooked.backend.entity.ProductEventType;
import com.cooked.backend.repository.SiteVisitRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AppEventServiceImplTest {

    private final ProductEventWriter writer = mock(ProductEventWriter.class);
    private final SiteVisitRepository visits = mock(SiteVisitRepository.class);
    private final AppEventServiceImpl service = new AppEventServiceImpl(writer, visits);

    @Test
    void keepsOnlyAppTypesAndCapsPerWindow() {
        var req = new AppEventsRequest(List.of(
                new AppEventsRequest.Event("app_session", null, 120_000),
                new AppEventsRequest.Event("SCAN", null, null),         // server-measured: refused
                new AppEventsRequest.Event("NOPE", null, null)));
        assertEquals(1, service.record(req, "ana@x.com"));
        verify(writer, times(1)).write(argThat(e -> e.getType() == ProductEventType.APP_SESSION && e.getDurationMs() == 120_000), eq("ana@x.com"));

        List<AppEventsRequest.Event> many = new ArrayList<>();
        for (int i = 0; i < 50; i++) many.add(new AppEventsRequest.Event("RECIPE_VIEW", "r" + i, null));
        int kept = 0;
        for (int i = 0; i < 8; i++) kept += service.record(new AppEventsRequest(many), "bob@x.com");
        assertEquals(AppEventServiceImpl.APP_EVENTS_PER_WINDOW, kept);
    }

    @Test
    void visitsStoreAHashAndTheReferrerHost() {
        service.visit(new SiteVisitRequest("abcdef123456", "/blog/thieb", "https://www.Google.com/search?q=x"));
        verify(visits).save(argThat(v -> v.getVisitor().length() == 32 && !v.getVisitor().contains("abcdef") && "google.com".equals(v.getReferrer())));
        assertNull(AppEventServiceImpl.referrerHost("https://cookedapp.com/blog"));
        assertNull(AppEventServiceImpl.referrerHost(""));
        assertEquals("t.co", AppEventServiceImpl.referrerHost("t.co"));
    }
}
