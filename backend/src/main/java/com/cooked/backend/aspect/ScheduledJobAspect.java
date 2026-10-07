package com.cooked.backend.aspect;

import com.cooked.backend.service.AutomationRunRecorder;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Records every run of a {@link Scheduled} job (outcome + duration) for the
 * Automations screen, without touching the jobs. Pollers that run more often
 * than every 10 minutes are skipped to keep the log small. The original
 * exception is always rethrown.
 */
@Aspect
@Component
public class ScheduledJobAspect {

    static final long MIN_INTERVAL_MS = 600_000;

    private final AutomationRunRecorder recorder;

    public ScheduledJobAspect(AutomationRunRecorder recorder) {
        this.recorder = recorder;
    }

    @Around("@annotation(scheduled)")
    public Object record(ProceedingJoinPoint pjp, Scheduled scheduled) throws Throwable {
        if (isFrequentPoller(scheduled)) return pjp.proceed();
        String job = jobName(pjp);
        long start = System.nanoTime();
        try {
            Object result = pjp.proceed();
            recorder.record(job, true, elapsed(start), null);
            return result;
        } catch (Throwable t) {
            recorder.record(job, false, elapsed(start), t.getClass().getSimpleName() + (t.getMessage() == null ? "" : ": " + t.getMessage()));
            throw t;
        }
    }

    static boolean isFrequentPoller(Scheduled s) {
        long every = s.fixedDelay() > 0 ? s.fixedDelay() : s.fixedRate();
        return every > 0 && every < MIN_INTERVAL_MS;
    }

    static String jobName(ProceedingJoinPoint pjp) {
        Class<?> type = AopUtils.getTargetClass(pjp.getTarget());
        return type.getSimpleName() + "." + ((MethodSignature) pjp.getSignature()).getMethod().getName();
    }

    private static int elapsed(long start) {
        return (int) ((System.nanoTime() - start) / 1_000_000);
    }
}
