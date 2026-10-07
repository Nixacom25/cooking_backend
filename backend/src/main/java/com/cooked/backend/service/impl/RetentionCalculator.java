package com.cooked.backend.service.impl;

import com.cooked.backend.dto.response.RetentionResponse;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/** Pure retention maths (no database): cohorts of users, their active days, a grouping. */
final class RetentionCalculator {

    static final int[] MARKS = {1, 7, 14, 30};
    private static final DateTimeFormatter WEEK = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);

    /** One signed-up user: id and signup day. */
    record Member(UUID id, LocalDate signup) {
    }

    private RetentionCalculator() {
    }

    /** Label of the Monday week a day belongs to, e.g. "Week of Oct 5". */
    static String weekOf(LocalDate day) {
        return "Week of " + day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).format(WEEK);
    }

    /**
     * @param members  users of the cohort window
     * @param active   active days per user
     * @param groupOf  label of a user's group
     * @param byWeek   true = keep chronological order (week groups), else biggest group first
     */
    static List<RetentionResponse.Group> groups(List<Member> members, Map<UUID, Set<LocalDate>> active, LocalDate today,
                                                Function<Member, String> groupOf, boolean byWeek) {
        Map<String, List<Member>> by = new LinkedHashMap<>();
        members.stream().sorted(Comparator.comparing(Member::signup))
                .forEach(m -> by.computeIfAbsent(groupOf.apply(m), k -> new ArrayList<>()).add(m));
        List<RetentionResponse.Group> out = new ArrayList<>();
        by.forEach((label, list) -> {
            Double[] pct = new Double[MARKS.length];
            for (int i = 0; i < MARKS.length; i++) pct[i] = rate(list, active, today, MARKS[i]);
            out.add(new RetentionResponse.Group(label, list.size(), pct[0], pct[1], pct[2], pct[3]));
        });
        if (!byWeek) out.sort(Comparator.comparingLong(RetentionResponse.Group::users).reversed());
        return out;
    }

    /** % of eligible members (signed up >= n days ago) active on day n after signup or later; null if none eligible. */
    static Double rate(List<Member> members, Map<UUID, Set<LocalDate>> active, LocalDate today, int n) {
        long eligible = 0;
        long kept = 0;
        for (Member m : members) {
            LocalDate mark = m.signup().plusDays(n);
            if (mark.isAfter(today)) continue;
            eligible++;
            Set<LocalDate> days = active.getOrDefault(m.id(), Set.of());
            if (days.stream().anyMatch(d -> !d.isBefore(mark))) kept++;
        }
        return eligible == 0 ? null : Math.round(1000.0 * kept / eligible) / 10.0;
    }
}
