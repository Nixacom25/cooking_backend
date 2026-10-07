package com.cooked.backend.aspect;

import com.cooked.backend.service.AutomationRunRecorder;
import com.cooked.backend.service.automation.AutomationCatalog;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ScheduledJobAspectTest {

    static class Jobs {
        @Scheduled(cron = "0 0 1 * * ?")
        public void nightly() {}

        @Scheduled(cron = "0 0 2 * * ?")
        public void broken() { throw new IllegalStateException("boom"); }

        @Scheduled(fixedDelay = 60000)
        public void poller() {}
    }

    @Test
    void recordsRunsAndRethrows() {
        AutomationRunRecorder recorder = mock(AutomationRunRecorder.class);
        AspectJProxyFactory f = new AspectJProxyFactory(new Jobs());
        f.setProxyTargetClass(true);
        f.addAspect(new ScheduledJobAspect(recorder));
        Jobs jobs = f.getProxy();

        jobs.nightly();
        verify(recorder).record(eq("Jobs.nightly"), eq(true), anyInt(), isNull());
        assertThrows(IllegalStateException.class, jobs::broken);
        verify(recorder).record(eq("Jobs.broken"), eq(false), anyInt(), eq("IllegalStateException: boom"));
        jobs.poller();
        verifyNoMoreInteractions(recorder);
    }

    /** Every scheduled automation in the catalog must point to a real @Scheduled method. */
    @Test
    void catalogJobsExist() throws Exception {
        for (AutomationCatalog.Automation a : AutomationCatalog.ALL) {
            if (a.job() == null) continue;
            String[] parts = a.job().split("\\.");
            Class<?> type = Class.forName(findPackage(parts[0]) + "." + parts[0]);
            Method m = Arrays.stream(type.getDeclaredMethods()).filter(x -> x.getName().equals(parts[1])).findFirst()
                    .orElseThrow(() -> new AssertionError("missing " + a.job()));
            assertNotNull(m.getAnnotation(Scheduled.class), a.job() + " must be @Scheduled");
        }
    }

    private static String findPackage(String simpleName) {
        for (String pkg : new String[] {"com.cooked.backend.service", "com.cooked.backend.service.impl"}) {
            try {
                Class.forName(pkg + "." + simpleName);
                return pkg;
            } catch (ClassNotFoundException ignored) { }
        }
        throw new AssertionError("class not found: " + simpleName);
    }
}
