package com.cooked.backend.repository;

import com.cooked.backend.entity.OnboardingStepView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Onboarding funnel aggregates. The cohort is every install whose first screen (step 0) was seen in
 * [from, to), optionally on one platform ('' = all).
 */
public interface OnboardingStepViewRepository extends JpaRepository<OnboardingStepView, UUID> {

    boolean existsByInstallIdAndStep(String installId, int step);

    String COHORT = "select s.installId from OnboardingStepView s where s.step = 0 and s.createdAt >= :from and s.createdAt < :to"
            + " and (:platform = '' or s.platform = :platform)";

    interface StepCount {
        int getStep();
        long getUsers();
    }

    interface StepName {
        int getStep();
        String getScreen();
    }

    interface Duration {
        LocalDateTime getStartedAt();
        LocalDateTime getFinishedAt();
    }

    /** Users of the cohort who saw each screen. */
    @Query("select v.step as step, count(v) as users from OnboardingStepView v where v.installId in (" + COHORT + ")"
            + " group by v.step")
    List<StepCount> reachedByStep(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to, @Param("platform") String platform);

    /** Users of the cohort per furthest screen seen (where they stopped). */
    @Query("select t.maxStep as step, count(t.maxStep) as users from (select max(v.step) as maxStep from OnboardingStepView v"
            + " where v.installId in (" + COHORT + ") group by v.installId) t group by t.maxStep")
    List<StepCount> leftByStep(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to, @Param("platform") String platform);

    /** Latest name reported for each screen (names can change between app versions). */
    @Query("select v.step as step, max(v.screen) as screen from OnboardingStepView v group by v.step")
    List<StepName> screenNames();

    /** First and last screen times of the cohort's installs that reached the last screen. */
    @Query("select s.createdAt as startedAt, e.createdAt as finishedAt from OnboardingStepView s, OnboardingStepView e"
            + " where e.installId = s.installId and s.step = 0 and e.step = :lastStep"
            + " and s.createdAt >= :from and s.createdAt < :to and (:platform = '' or s.platform = :platform)")
    List<Duration> finishTimes(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to, @Param("platform") String platform,
                               @Param("lastStep") int lastStep);
}
