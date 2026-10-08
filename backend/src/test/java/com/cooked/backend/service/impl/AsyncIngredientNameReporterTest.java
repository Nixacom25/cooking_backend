package com.cooked.backend.service.impl;

import com.cooked.backend.entity.IngredientNameSource;
import com.cooked.backend.service.UnmatchedIngredientService;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

class AsyncIngredientNameReporterTest {

    @Test
    void neverFailsTheCallerAndSkipsEmptyLists() {
        UnmatchedIngredientService queue = mock(UnmatchedIngredientService.class);
        AsyncIngredientNameReporter reporter = new AsyncIngredientNameReporter(queue);

        reporter.report(List.of(), IngredientNameSource.SCAN, "a@x.com");
        verifyNoInteractions(queue);

        when(queue.record(anyCollection(), any(), any())).thenThrow(new IllegalStateException("db down"));
        assertDoesNotThrow(() -> reporter.report(Arrays.asList("fufu", null), IngredientNameSource.IMPORT, "a@x.com"));
        verify(queue).record(Arrays.asList("fufu", null), IngredientNameSource.IMPORT, "a@x.com");
    }
}
