package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.EmailCampaignRequest;
import com.cooked.backend.dto.request.EmailCampaignScheduleRequest;
import com.cooked.backend.dto.response.EmailAudienceCount;
import com.cooked.backend.dto.response.EmailCampaignResponse;
import com.cooked.backend.entity.EmailCampaign;
import com.cooked.backend.entity.EmailCampaignSend;
import com.cooked.backend.entity.User;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.exception.ResourceNotFoundException;
import com.cooked.backend.repository.EmailCampaignRepository;
import com.cooked.backend.repository.EmailCampaignSendRepository;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.service.ActivityLogService;
import com.cooked.backend.service.EmailCampaignService;
import com.cooked.backend.service.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class EmailCampaignServiceImpl implements EmailCampaignService {

    static final int BATCH = 200;
    static final Map<EmailCampaign.Audience, String[]> AUDIENCES = new LinkedHashMap<>();
    static {
        AUDIENCES.put(EmailCampaign.Audience.TRIAL, new String[]{"Trial users", "Started a free trial, not yet paying"});
        AUDIENCES.put(EmailCampaign.Audience.TRIAL_ENDING, new String[]{"Trial ending in ≤ 3 days", "Best moment to convert"});
        AUDIENCES.put(EmailCampaign.Audience.FREE, new String[]{"Free users", "Never subscribed"});
        AUDIENCES.put(EmailCampaign.Audience.PAYING, new String[]{"Paying subscribers", "Active Premium"});
        AUDIENCES.put(EmailCampaign.Audience.INACTIVE_30, new String[]{"Inactive 30+ days", "Opened the app before, not lately"});
    }

    private final EmailCampaignRepository campaigns;
    private final EmailCampaignSendRepository sends;
    private final UserRepository users;
    private final EmailService emailService;
    private final ActivityLogService activityLog;
    private final byte[] secret;
    private final String publicApiUrl;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public EmailCampaignServiceImpl(EmailCampaignRepository campaigns, EmailCampaignSendRepository sends, UserRepository users,
                                    EmailService emailService, ActivityLogService activityLog,
                                    @Value("${email.unsubscribe.secret}") String secret,
                                    @Value("${public.api-url}") String publicApiUrl) {
        this.campaigns = campaigns;
        this.sends = sends;
        this.users = users;
        this.emailService = emailService;
        this.activityLog = activityLog;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.publicApiUrl = publicApiUrl.replaceAll("/+$", "");
    }

    // --- audiences --------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<EmailAudienceCount> audiences() {
        LocalDateTime now = LocalDateTime.now();
        List<EmailAudienceCount> out = new ArrayList<>();
        AUDIENCES.forEach((a, txt) -> out.add(new EmailAudienceCount(a.name(), txt[0], txt[1],
                campaigns.countAudience(a.name(), now, now.plusDays(3), now.minusDays(30)),
                campaigns.countAudienceUnderCap(a.name(), now, now.plusDays(3), now.minusDays(30), now.minusDays(7)))));
        return out;
    }

    // --- drafts -------------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public Page<EmailCampaignResponse> list(int page, int size) {
        return campaigns.findAllByOrderByCreatedAtDesc(PageRequest.of(Math.max(page, 0), Math.max(1, Math.min(size, 50)))).map(EmailCampaignServiceImpl::view);
    }

    @Override
    @Transactional(readOnly = true)
    public EmailCampaignResponse get(UUID id) {
        return view(find(id));
    }

    @Override
    @Transactional
    public EmailCampaignResponse create(EmailCampaignRequest r, String adminEmail) {
        EmailCampaign c = EmailCampaign.builder().status(EmailCampaign.Status.DRAFT).createdBy(adminEmail).template("TRIAL_ENDING").build();
        apply(c, r);
        return view(campaigns.save(c));
    }

    @Override
    @Transactional
    public EmailCampaignResponse update(UUID id, EmailCampaignRequest r) {
        EmailCampaign c = find(id);
        if (c.getStatus() != EmailCampaign.Status.DRAFT) throw new BadRequestException("Only drafts can be edited. Cancel the schedule first.");
        apply(c, r);
        return view(campaigns.save(c));
    }

    private static void apply(EmailCampaign c, EmailCampaignRequest r) {
        if (r == null) return;
        if (r.audience() != null) c.setAudience(EmailCampaign.Audience.valueOf(r.audience()));
        if (r.subject() != null) c.setSubject(r.subject().trim());
        if (r.preheader() != null) c.setPreheader(r.preheader().trim());
        if (r.template() != null) c.setTemplate(r.template());
        if (r.title() != null) c.setTitle(r.title().trim());
        if (r.body() != null) c.setBody(r.body().trim());
        if (r.buttonLabel() != null) c.setButtonLabel(r.buttonLabel().trim());
        if (r.buttonUrl() != null) c.setButtonUrl(r.buttonUrl().trim());
        if (r.heroImageUrl() != null) c.setHeroImageUrl(r.heroImageUrl().trim());
    }

    @Override
    @Transactional(readOnly = true)
    public String preview(UUID id) {
        return html(find(id), "Alex", "#");
    }

    @Override
    @Transactional
    public EmailCampaignResponse sendTest(UUID id, String adminEmail) {
        EmailCampaign c = find(id);
        requireComplete(c);
        User admin = users.findByEmail(adminEmail).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        boolean ok = emailService.sendCampaignEmail(adminEmail, "[Test] " + c.getSubject(),
                html(c, admin.getFirstname(), unsubscribeUrl(admin.getId())), unsubscribeUrl(admin.getId()));
        if (!ok) throw new BadRequestException("Brevo didn't accept the test email. Check the Brevo integration.");
        c.setTestSentTo(adminEmail);
        c.setTestSentAt(LocalDateTime.now());
        return view(campaigns.save(c));
    }

    @Override
    @Transactional
    public EmailCampaignResponse schedule(UUID id, EmailCampaignScheduleRequest r, String adminEmail) {
        EmailCampaign c = find(id);
        if (c.getStatus() != EmailCampaign.Status.DRAFT) throw new BadRequestException("This campaign is already " + c.getStatus().name().toLowerCase() + ".");
        requireComplete(c);
        if (c.getTestSentAt() == null) throw new BadRequestException("Send a test to yourself first.");
        if (r == null || !r.confirmed()) throw new BadRequestException("Confirm that you checked the content and the audience.");
        LocalDateTime when = r.sendAt() == null ? LocalDateTime.now()
                : LocalDateTime.ofInstant(r.sendAt().toInstant(), ZoneId.systemDefault());
        if (when.isBefore(LocalDateTime.now().minusMinutes(5))) throw new BadRequestException("Choose a time in the future.");
        c.setStatus(EmailCampaign.Status.SCHEDULED);
        c.setScheduledAt(when);
        campaigns.save(c);
        users.findByEmail(adminEmail).ifPresent(a -> activityLog.logActivity(a, "Email campaign scheduled",
                "“" + c.getSubject() + "” · " + c.getAudience() + " · " + when, c.getId(), "EMAIL_CAMPAIGN"));
        return view(c);
    }

    @Override
    @Transactional
    public EmailCampaignResponse cancel(UUID id, String adminEmail) {
        EmailCampaign c = find(id);
        if (c.getStatus() == EmailCampaign.Status.SCHEDULED) {
            c.setStatus(EmailCampaign.Status.DRAFT); // back to an editable draft
            c.setScheduledAt(null);
        } else if (c.getStatus() == EmailCampaign.Status.DRAFT) {
            c.setStatus(EmailCampaign.Status.CANCELLED);
        } else {
            throw new BadRequestException("A campaign that is sending or sent can't be cancelled.");
        }
        users.findByEmail(adminEmail).ifPresent(a -> activityLog.logActivity(a, "Email campaign cancelled", "“" + c.getSubject() + "”", c.getId(), "EMAIL_CAMPAIGN"));
        return view(campaigns.save(c));
    }

    private static void requireComplete(EmailCampaign c) {
        List<String> missing = new ArrayList<>();
        if (c.getAudience() == null) missing.add("audience");
        if (c.getSubject() == null || c.getSubject().isBlank()) missing.add("subject");
        if (c.getTitle() == null || c.getTitle().isBlank()) missing.add("title");
        if (c.getBody() == null || c.getBody().isBlank()) missing.add("body text");
        if (!missing.isEmpty()) throw new BadRequestException("Add the " + String.join(", ", missing) + " first.");
    }

    // --- sending ------------------------------------------------------------------------------

    @Override
    @Scheduled(fixedDelay = 60_000, initialDelay = 90_000)
    public void sendDue() {
        if (!running.compareAndSet(false, true)) return;
        try {
            List<EmailCampaign> due = new ArrayList<>(campaigns.findAllByStatusAndScheduledAtLessThanEqual(EmailCampaign.Status.SCHEDULED, LocalDateTime.now()));
            due.addAll(campaigns.findAllByStatusAndScheduledAtLessThanEqual(EmailCampaign.Status.SENDING, LocalDateTime.now()));
            for (EmailCampaign c : due) send(c.getId());
        } finally {
            running.set(false);
        }
    }

    void send(UUID id) {
        EmailCampaign c = campaigns.findById(id).orElse(null);
        if (c == null) return;
        LocalDateTime now = LocalDateTime.now();
        String aud = c.getAudience().name();
        if (c.getStatus() == EmailCampaign.Status.SCHEDULED) {
            long all = campaigns.countAudience(aud, now, now.plusDays(3), now.minusDays(30));
            long reachable = campaigns.countAudienceUnderCap(aud, now, now.plusDays(3), now.minusDays(30), now.minusDays(7));
            c.setStatus(EmailCampaign.Status.SENDING);
            c.setStartedAt(now);
            c.setRecipients((int) reachable);
            c.setSkippedCount((int) (all - reachable));
            c.setSentCount(0);
            c.setFailedCount(0);
            campaigns.save(c);
        }
        int sent = c.getSentCount() == null ? 0 : c.getSentCount();
        int failed = c.getFailedCount() == null ? 0 : c.getFailedCount();
        Set<UUID> failedNow = new HashSet<>();
        while (true) {
            LocalDateTime t = LocalDateTime.now();
            List<Object[]> batch = campaigns.nextRecipients(aud, t, t.plusDays(3), t.minusDays(30), t.minusDays(7), id, PageRequest.of(0, BATCH));
            batch = batch.stream().filter(r -> !failedNow.contains((UUID) r[0])).toList();
            if (batch.isEmpty()) break;
            for (Object[] r : batch) {
                UUID userId = (UUID) r[0];
                String unsub = unsubscribeUrl(userId);
                boolean ok;
                try {
                    ok = emailService.sendCampaignEmail((String) r[1], c.getSubject(), html(c, (String) r[2], unsub), unsub);
                } catch (Exception e) {
                    ok = false;
                }
                if (ok) {
                    sends.save(EmailCampaignSend.builder().campaignId(id).userId(userId).sentAt(LocalDateTime.now()).build());
                    sent++;
                } else {
                    failed++;
                    failedNow.add(userId); // not retried in this run
                }
            }
            c.setSentCount(sent);
            c.setFailedCount(failed);
            campaigns.save(c);
        }
        c.setStatus(sent == 0 && failed > 0 ? EmailCampaign.Status.FAILED : EmailCampaign.Status.SENT);
        c.setSentAt(LocalDateTime.now());
        campaigns.save(c);
        log.info("Email campaign {} done: {} sent, {} failed, {} skipped (cap)", id, sent, failed, c.getSkippedCount());
    }

    // --- unsubscribe --------------------------------------------------------------------------

    @Override
    public String unsubscribeLink(UUID userId) {
        return unsubscribeUrl(userId);
    }

    String unsubscribeUrl(UUID userId) {
        return publicApiUrl + "/public/email/unsubscribe?u=" + userId + "&t=" + token(userId);
    }

    String token(UUID userId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] d = mac.doFinal(("unsubscribe:" + userId).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(d);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    @Transactional
    public boolean unsubscribe(UUID userId, String token) {
        if (userId == null || token == null) return false;
        if (!MessageDigest.isEqual(token(userId).getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8))) return false;
        return users.findById(userId).map(u -> {
            u.setEmailMarketingEnabled(false);
            users.save(u);
            return true;
        }).orElse(false);
    }

    // --- rendering ----------------------------------------------------------------------------

    static String html(EmailCampaign c, String firstName, String unsubscribeUrl) {
        String name = firstName == null || firstName.isBlank() ? "there" : firstName.trim();
        StringBuilder paragraphs = new StringBuilder();
        for (String p : (c.getBody() == null ? "" : c.getBody()).split("\\n\\s*\\n")) {
            if (p.isBlank()) continue;
            paragraphs.append("<p style=\"margin:0 0 14px;font-size:16px;line-height:1.55;color:#141414\">")
                    .append(esc(p.trim().replace("{firstname}", name)).replace("\n", "<br>")).append("</p>");
        }
        String button = c.getButtonLabel() == null || c.getButtonLabel().isBlank() ? "" :
                "<p style=\"margin:22px 0\"><a href=\"" + esc(c.getButtonUrl() == null || c.getButtonUrl().isBlank() ? "https://cookedapp.com" : c.getButtonUrl())
                        + "\" style=\"display:inline-block;padding:14px 24px;border-radius:999px;background:#C4161C;color:#fff;text-decoration:none;font-weight:600\">"
                        + esc(c.getButtonLabel()) + "</a></p>";
        String hero = c.getHeroImageUrl() == null || c.getHeroImageUrl().isBlank() ? "" :
                "<img src=\"" + esc(c.getHeroImageUrl()) + "\" alt=\"\" style=\"display:block;width:100%;border-radius:16px;margin:0 0 20px\">";
        String pre = c.getPreheader() == null ? "" : "<span style=\"display:none;max-height:0;overflow:hidden;opacity:0\">" + esc(c.getPreheader()) + "</span>";
        return "<div style=\"background:#F3F1ED;padding:24px 0;font-family:Arial,sans-serif\">" + pre
                + "<div style=\"max-width:560px;margin:0 auto;background:#fff;border-radius:20px;overflow:hidden\">"
                + "<div style=\"background:#C4161C;color:#fff;padding:18px 24px;font-size:20px;font-weight:700\">Cooked</div>"
                + "<div style=\"padding:24px\">" + hero
                + "<h1 style=\"margin:0 0 14px;font-size:24px;color:#141414\">" + esc((c.getTitle() == null ? "" : c.getTitle()).replace("{firstname}", name)) + "</h1>"
                + paragraphs + button + "</div>"
                + "<div style=\"padding:16px 24px;border-top:1px solid #ECE8E1;font-size:12px;color:#8E8981\">You get this email because you have a Cooked account. "
                + "<a href=\"" + esc(unsubscribeUrl) + "\" style=\"color:#66625C\">Unsubscribe</a></div>"
                + "</div></div>";
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private EmailCampaign find(UUID id) {
        return campaigns.findById(id).orElseThrow(() -> new ResourceNotFoundException("Campaign not found"));
    }

    static EmailCampaignResponse view(EmailCampaign c) {
        return new EmailCampaignResponse(c.getId(), c.getAudience() == null ? null : c.getAudience().name(), c.getSubject(), c.getPreheader(),
                c.getTemplate(), c.getTitle(), c.getBody(), c.getButtonLabel(), c.getButtonUrl(), c.getHeroImageUrl(), c.getStatus().name(),
                c.getScheduledAt(), c.getSentAt(), c.getRecipients(), c.getSentCount(), c.getSkippedCount(), c.getFailedCount(),
                c.getTestSentTo(), c.getTestSentAt(), c.getCreatedBy(), c.getCreatedAt());
    }
}
