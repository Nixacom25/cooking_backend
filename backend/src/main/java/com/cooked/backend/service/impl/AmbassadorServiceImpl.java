package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.AmbassadorRequest;
import com.cooked.backend.dto.response.AcquisitionResponse;
import com.cooked.backend.dto.response.AmbassadorDetailResponse;
import com.cooked.backend.dto.response.AmbassadorResponse;
import com.cooked.backend.dto.response.AmbassadorsResponse;
import com.cooked.backend.entity.*;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.exception.ResourceNotFoundException;
import com.cooked.backend.repository.*;
import com.cooked.backend.repository.AmbassadorStatsRepository.IdCount;
import com.cooked.backend.repository.AmbassadorStatsRepository.IdRevenue;
import com.cooked.backend.service.AmbassadorService;
import com.cooked.backend.service.WorkspaceSettingsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;

/**
 * Attribution: a user is credited to the ambassador whose code they entered in
 * onboarding (first code wins). Revenue = their successful payments after that
 * date; commission = revenue × the ambassador's rate. Months become DUE once
 * finished and stay so until an admin marks them paid (amounts then frozen).
 */
@Service
@Transactional(readOnly = true)
public class AmbassadorServiceImpl implements AmbassadorService {

    static final int MAX_DAYS = 90;
    static final int PAYOUT_MONTHS = 6;
    private static final List<SubscriptionStatus> TRIAL = List.of(SubscriptionStatus.TRIAL);
    private static final List<SubscriptionStatus> PAID = List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.PREMIUM);
    private static final LocalDateTime EPOCH = LocalDateTime.of(2020, 1, 1, 0, 0);

    private final AmbassadorRepository ambassadors;
    private final AmbassadorClickRepository clicks;
    private final AmbassadorPayoutRepository payouts;
    private final AmbassadorStatsRepository stats;
    private final UserRepository users;
    private final WorkspaceSettingsService settings;
    private final String linkBase;

    public AmbassadorServiceImpl(AmbassadorRepository ambassadors, AmbassadorClickRepository clicks, AmbassadorPayoutRepository payouts,
                                 AmbassadorStatsRepository stats, UserRepository users, WorkspaceSettingsService settings,
                                 @Value("${ambassador.link-base:https://cooked-backend-latest.onrender.com/r/}") String linkBase) {
        this.ambassadors = ambassadors;
        this.clicks = clicks;
        this.payouts = payouts;
        this.stats = stats;
        this.users = users;
        this.settings = settings;
        this.linkBase = linkBase.endsWith("/") ? linkBase : linkBase + "/";
    }

    // ---- reads -------------------------------------------------------------

    @Override
    public AmbassadorsResponse list(int days) {
        int d = clamp(days);
        LocalDateTime to = LocalDate.now().plusDays(1).atStartOfDay();
        LocalDateTime from = to.minusDays(d);
        Window cur = window(from, to);
        Window prev = window(from.minusDays(d), from);
        List<AmbassadorResponse> rows = ambassadors.findAllByOrderByCreatedAtDesc().stream().map(a -> row(a, cur)).toList();
        double pending = ambassadors.findAll().stream().mapToDouble(this::pendingPayout).sum();
        return AmbassadorsResponse.builder()
                .days(d)
                .totals(totals(cur, ambassadors.findAll()))
                .totalsPrev(totals(prev, ambassadors.findAll()))
                .pendingPayout(round(pending))
                .defaultCommissionPercent(defaultCommission())
                .ambassadors(rows)
                .build();
    }

    @Override
    public AmbassadorDetailResponse detail(UUID id, int days) {
        Ambassador a = find(id);
        int d = clamp(days);
        LocalDateTime to = LocalDate.now().plusDays(1).atStartOfDay();
        LocalDateTime from = to.minusDays(d);
        return AmbassadorDetailResponse.builder()
                .ambassador(row(a, window(from, to)))
                .dailyClicks(series(from.toLocalDate(), d, stats.clicksDaily(id, from, to)))
                .dailyClicksPrev(series(from.minusDays(d).toLocalDate(), d, stats.clicksDaily(id, from.minusDays(d), from)))
                .payouts(payoutRows(a))
                .build();
    }

    // ---- writes ------------------------------------------------------------

    @Override
    @Transactional
    public AmbassadorResponse create(AmbassadorRequest r) {
        Ambassador a = Ambassador.builder().status(r.getStatus() == null ? AmbassadorStatus.ACTIVE : r.getStatus()).build();
        String wanted = r.getCode() == null || r.getCode().isBlank() ? null : r.getCode().trim().toUpperCase(Locale.ROOT);
        if (wanted != null && ambassadors.existsByCodeIgnoreCase(wanted)) throw new BadRequestException("Code " + wanted + " is already taken.");
        a.setCode(wanted != null ? wanted : uniqueCode(r.getName()));
        apply(a, r);
        a = ambassadors.save(a);
        LocalDateTime to = LocalDateTime.now().plusDays(1);
        return row(a, window(to.minusDays(30), to));
    }

    @Override
    @Transactional
    public AmbassadorResponse update(UUID id, AmbassadorRequest r) {
        Ambassador a = find(id);
        if (r.getCode() != null && !r.getCode().isBlank() && !r.getCode().trim().equalsIgnoreCase(a.getCode())) {
            throw new BadRequestException("A code can't be changed once shared — create a new ambassador instead.");
        }
        apply(a, r);
        if (r.getStatus() != null) a.setStatus(r.getStatus());
        a = ambassadors.save(a);
        LocalDateTime to = LocalDateTime.now().plusDays(1);
        return row(a, window(to.minusDays(30), to));
    }

    @Override
    @Transactional
    public AmbassadorDetailResponse markPaid(UUID id, String month, String adminEmail) {
        Ambassador a = find(id);
        YearMonth ym;
        try {
            ym = YearMonth.parse(month);
        } catch (RuntimeException e) {
            throw new BadRequestException("Month must look like 2026-10");
        }
        if (!ym.isBefore(YearMonth.now())) throw new BadRequestException("Only finished months can be paid.");
        if (payouts.findByAmbassadorIdAndMonth(id, ym.toString()).isPresent()) throw new BadRequestException("This month is already marked as paid.");
        AmbassadorDetailResponse.Payout due = payoutRows(a).stream().filter(p -> p.getMonth().equals(ym.toString())).findFirst()
                .orElseThrow(() -> new BadRequestException("Nothing to pay for " + ym));
        payouts.save(AmbassadorPayout.builder().ambassadorId(id).month(ym.toString())
                .revenue(BigDecimal.valueOf(due.getRevenue())).commission(BigDecimal.valueOf(due.getCommission()))
                .paidSubscribers(due.getPaidSubscribers()).paidBy(adminEmail).paidAt(LocalDateTime.now()).build());
        return detail(id, 30);
    }

    @Override
    @Transactional
    public Optional<Ambassador> recordClick(String code) {
        Optional<Ambassador> a = activeByCode(code);
        a.ifPresent(x -> clicks.save(AmbassadorClick.builder().ambassadorId(x.getId()).build()));
        return a;
    }

    @Override
    @Transactional
    public Optional<Ambassador> attachReferral(User user, String code) {
        Optional<Ambassador> a = activeByCode(code);
        if (a.isEmpty()) return Optional.empty();
        if (user.getReferredByAmbassadorId() != null) {
            throw new BadRequestException(user.getReferredByAmbassadorId().equals(a.get().getId())
                    ? "This code is already applied to your account." : "A referral code is already applied to your account.");
        }
        user.setReferredByAmbassadorId(a.get().getId());
        user.setReferredAt(LocalDateTime.now());
        users.save(user);
        return a;
    }

    // ---- computation -------------------------------------------------------

    /** Every aggregate of one period, keyed by ambassador id. */
    record Window(Map<UUID, Long> clicks, Map<UUID, Long> signups, Map<UUID, Long> trials, Map<UUID, Long> paid, Map<UUID, BigDecimal> revenue) {}

    private Window window(LocalDateTime from, LocalDateTime to) {
        Map<UUID, BigDecimal> revenue = new HashMap<>();
        for (IdRevenue r : stats.revenueBetween(from, to)) revenue.put(r.getId(), r.getAmount() == null ? BigDecimal.ZERO : r.getAmount());
        return new Window(counts(stats.clicksBetween(from, to)), counts(stats.referralsBetween(from, to)),
                counts(stats.referralsInStatus(TRIAL, from, to)), counts(stats.referralsInStatus(PAID, EPOCH, to)), revenue);
    }

    private AmbassadorResponse row(Ambassador a, Window w) {
        long signups = w.signups().getOrDefault(a.getId(), 0L);
        long paid = w.paid().getOrDefault(a.getId(), 0L);
        BigDecimal revenue = w.revenue().getOrDefault(a.getId(), BigDecimal.ZERO);
        return AmbassadorResponse.builder()
                .id(a.getId()).name(a.getName()).email(a.getEmail()).code(a.getCode()).platform(a.getPlatform()).handle(a.getHandle())
                .audience(a.getAudience()).commissionPercent(a.getCommissionPercent()).status(a.getStatus()).notes(a.getNotes())
                .since(a.getCreatedAt()).link(linkBase + a.getCode())
                .clicks(w.clicks().getOrDefault(a.getId(), 0L)).signups(signups)
                .trials(w.trials().getOrDefault(a.getId(), 0L)).paid(paid)
                .conversion(signups == 0 ? null : round(100.0 * paid / signups))
                .revenue(round(revenue.doubleValue())).commission(commission(revenue, a.getCommissionPercent()))
                .build();
    }

    private AmbassadorsResponse.Totals totals(Window w, List<Ambassador> all) {
        long signups = sum(w.signups()), paid = sum(w.paid());
        double revenue = 0, commission = 0;
        for (Ambassador a : all) {
            BigDecimal r = w.revenue().getOrDefault(a.getId(), BigDecimal.ZERO);
            revenue += r.doubleValue();
            commission += commission(r, a.getCommissionPercent());
        }
        return AmbassadorsResponse.Totals.builder().clicks(sum(w.clicks())).signups(signups).trials(sum(w.trials())).paid(paid)
                .conversion(signups == 0 ? null : round(100.0 * paid / signups)).revenue(round(revenue)).commission(round(commission)).build();
    }

    List<AmbassadorDetailResponse.Payout> payoutRows(Ambassador a) {
        YearMonth now = YearMonth.now();
        YearMonth first = now.minusMonths(PAYOUT_MONTHS - 1L);
        Map<String, AmbassadorStatsRepository.MonthRevenue> byMonth = new HashMap<>();
        stats.revenueByMonth(a.getId(), first.atDay(1).atStartOfDay())
                .forEach(m -> byMonth.put(YearMonth.of(m.getYear(), m.getMonth()).toString(), m));
        Map<String, AmbassadorPayout> paid = new HashMap<>();
        payouts.findByAmbassadorId(a.getId()).forEach(p -> paid.put(p.getMonth(), p));
        List<AmbassadorDetailResponse.Payout> out = new ArrayList<>();
        for (YearMonth ym = now; !ym.isBefore(first); ym = ym.minusMonths(1)) {
            String key = ym.toString();
            AmbassadorPayout p = paid.get(key);
            if (p != null) {
                out.add(AmbassadorDetailResponse.Payout.builder().month(key).paidSubscribers(p.getPaidSubscribers())
                        .revenue(p.getRevenue().doubleValue()).commission(p.getCommission().doubleValue()).status("PAID").paidAt(p.getPaidAt()).build());
                continue;
            }
            AmbassadorStatsRepository.MonthRevenue m = byMonth.get(key);
            BigDecimal revenue = m == null || m.getAmount() == null ? BigDecimal.ZERO : m.getAmount();
            if (revenue.signum() == 0 && !ym.equals(now)) continue;
            out.add(AmbassadorDetailResponse.Payout.builder().month(key).paidSubscribers(m == null || m.getPayers() == null ? 0 : m.getPayers())
                    .revenue(round(revenue.doubleValue())).commission(commission(revenue, a.getCommissionPercent()))
                    .status(ym.equals(now) ? "IN_PROGRESS" : "DUE").build());
        }
        return out;
    }

    private double pendingPayout(Ambassador a) {
        return payoutRows(a).stream().filter(p -> "DUE".equals(p.getStatus())).mapToDouble(AmbassadorDetailResponse.Payout::getCommission).sum();
    }

    static double commission(BigDecimal revenue, BigDecimal percent) {
        if (revenue == null || percent == null) return 0;
        return revenue.multiply(percent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP).doubleValue();
    }

    /** Letters and digits from the name, upper-case, 3-10 chars, unique (adds digits when taken). */
    String uniqueCode(String name) {
        String base = Normalizer.normalize(name == null ? "" : name, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        if (base.length() > 10) base = base.substring(0, 10);
        if (base.length() < 3) base = (base + "COOK").substring(0, Math.max(3, base.length()));
        String code = base;
        for (int i = 2; ambassadors.existsByCodeIgnoreCase(code); i++) code = base + i;
        return code;
    }

    private Optional<Ambassador> activeByCode(String code) {
        if (code == null || code.isBlank()) return Optional.empty();
        String c = code.trim().replace(" ", "");
        if (!c.matches("[A-Za-z0-9]{3,20}")) return Optional.empty();
        return ambassadors.findByCodeIgnoreCase(c).filter(a -> a.getStatus() == AmbassadorStatus.ACTIVE);
    }

    private void apply(Ambassador a, AmbassadorRequest r) {
        a.setName(r.getName().trim());
        a.setEmail(blank(r.getEmail()));
        a.setPlatform(blank(r.getPlatform()));
        a.setHandle(blank(r.getHandle()));
        a.setAudience(blank(r.getAudience()));
        a.setNotes(blank(r.getNotes()));
        a.setCommissionPercent(r.getCommissionPercent() != null ? r.getCommissionPercent()
                : a.getCommissionPercent() != null ? a.getCommissionPercent() : defaultCommission());
    }

    private BigDecimal defaultCommission() {
        BigDecimal d = settings.current().getAmbassadorCommissionPercent();
        return d == null ? BigDecimal.valueOf(20) : d;
    }

    private Ambassador find(UUID id) {
        return ambassadors.findById(id).orElseThrow(() -> new ResourceNotFoundException("Ambassador not found"));
    }

    private static Map<UUID, Long> counts(List<IdCount> rows) {
        Map<UUID, Long> m = new HashMap<>();
        rows.forEach(r -> m.put(r.getId(), r.getTotal() == null ? 0 : r.getTotal()));
        return m;
    }

    private static long sum(Map<UUID, Long> m) {
        return m.values().stream().mapToLong(Long::longValue).sum();
    }

    private static List<AcquisitionResponse.DayCount> series(LocalDate first, int days, List<AmbassadorStatsRepository.DayCount> rows) {
        Map<LocalDate, Long> byDay = new HashMap<>();
        rows.forEach(r -> byDay.merge(r.getDay(), r.getTotal() == null ? 0 : r.getTotal(), Long::sum));
        List<AcquisitionResponse.DayCount> out = new ArrayList<>(days);
        for (int i = 0; i < days; i++) {
            LocalDate d = first.plusDays(i);
            out.add(new AcquisitionResponse.DayCount(d.toString(), byDay.getOrDefault(d, 0L)));
        }
        return out;
    }

    private static int clamp(int days) {
        return Math.max(1, Math.min(days, MAX_DAYS));
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
