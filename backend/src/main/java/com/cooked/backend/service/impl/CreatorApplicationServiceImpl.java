package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.AmbassadorRequest;
import com.cooked.backend.dto.request.CreatorApplicationRequest;
import com.cooked.backend.dto.response.AmbassadorResponse;
import com.cooked.backend.dto.response.CreatorApplicationResponse;
import com.cooked.backend.dto.response.PagedResponse;
import com.cooked.backend.entity.CreatorApplication;
import com.cooked.backend.entity.CreatorApplicationStatus;
import com.cooked.backend.entity.CreatorProgram;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.exception.ResourceNotFoundException;
import com.cooked.backend.repository.CreatorApplicationRepository;
import com.cooked.backend.service.AmbassadorService;
import com.cooked.backend.service.CreatorApplicationService;
import com.cooked.backend.service.EmailService;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.Refill;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CreatorApplicationServiceImpl implements CreatorApplicationService {

    private final CreatorApplicationRepository applications;
    private final AmbassadorService ambassadors;
    private final EmailService emailService;
    private final ProxyManager<byte[]> proxyManager;

    @Override
    @Transactional
    public void submit(CreatorApplicationRequest r, String clientKey) {
        BucketConfiguration limit = BucketConfiguration.builder()
                .addLimit(Bandwidth.classic(5, Refill.intervally(5, Duration.ofHours(1)))).build();
        if (!proxyManager.builder().build(("creator-apply-" + clientKey).getBytes(StandardCharsets.UTF_8), limit).tryConsume(1)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many applications from this network. Please try again later.");
        }
        applications.save(CreatorApplication.builder()
                .program(CreatorProgram.valueOf(r.getProgram()))
                .name(r.getName().trim()).email(r.getEmail().trim().toLowerCase())
                .handle(blank(r.getHandle())).platform(blank(r.getPlatform())).audience(blank(r.getAudience()))
                .details(blank(r.getDetails())).status(CreatorApplicationStatus.NEW).build());
    }

    @Override
    public PagedResponse<CreatorApplicationResponse> list(CreatorApplicationStatus status, int page, int size) {
        PageRequest p = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100)));
        return PagedResponse.of(status == null ? applications.findAllByOrderByCreatedAtDesc(p) : applications.findByStatusOrderByCreatedAtDesc(status, p),
                CreatorApplicationServiceImpl::toResponse);
    }

    @Override
    public Map<String, Long> counts() {
        Map<String, Long> m = new LinkedHashMap<>();
        for (CreatorApplicationStatus s : CreatorApplicationStatus.values()) m.put(s.name(), 0L);
        applications.countByStatus().forEach(c -> m.put(c.getStatus().name(), c.getTotal() == null ? 0 : c.getTotal()));
        return m;
    }

    @Override
    @Transactional
    public CreatorApplicationResponse decide(UUID id, CreatorApplicationStatus status, String message, String adminEmail) {
        CreatorApplication a = applications.findById(id).orElseThrow(() -> new ResourceNotFoundException("Application not found"));
        if (status == null || status == CreatorApplicationStatus.NEW) throw new BadRequestException("Choose a decision.");
        if (a.getStatus() == CreatorApplicationStatus.APPROVED) throw new BadRequestException("This application is already approved.");
        String note = message == null ? "" : message.trim();
        if (status == CreatorApplicationStatus.NEED_INFO && note.isEmpty()) throw new BadRequestException("Write what you need to know.");

        String code = null;
        if (status == CreatorApplicationStatus.APPROVED && a.getProgram() == CreatorProgram.AMBASSADOR) {
            AmbassadorRequest r = new AmbassadorRequest();
            r.setName(a.getName());
            r.setEmail(a.getEmail());
            r.setHandle(a.getHandle());
            r.setPlatform(a.getPlatform());
            r.setAudience(a.getAudience());
            r.setNotes("From the website application of " + a.getCreatedAt().toLocalDate());
            AmbassadorResponse created = ambassadors.create(r);
            a.setAmbassadorId(created.getId());
            code = created.getCode();
        }
        a.setStatus(status);
        a.setReviewedBy(adminEmail);
        a.setReviewedAt(LocalDateTime.now());
        applications.save(a);

        String program = a.getProgram() == CreatorProgram.AMBASSADOR ? "Ambassador Program" : "Recipe Creator program";
        switch (status) {
            case APPROVED -> emailService.sendCreatorDecisionEmail(a.getEmail(), a.getName(), "Welcome to the Cooked " + program + "!",
                    (code != null ? "Your ambassador code is " + code + ". New users enter it in the app when they sign up (\"Do you have a referral code?\").\n\n" : "")
                            + (note.isEmpty() ? "We'll be in touch with the next steps." : note));
            case NEED_INFO -> emailService.sendCreatorDecisionEmail(a.getEmail(), a.getName(), "A few questions about your application", note);
            case REJECTED -> emailService.sendCreatorDecisionEmail(a.getEmail(), a.getName(), "About your " + program + " application",
                    note.isEmpty() ? "Thank you for applying. We can't offer you a place right now, but we'll keep your details for future waves." : note);
            default -> { }
        }
        return toResponse(a);
    }

    static CreatorApplicationResponse toResponse(CreatorApplication a) {
        return CreatorApplicationResponse.builder().id(a.getId()).program(a.getProgram()).name(a.getName()).email(a.getEmail())
                .handle(a.getHandle()).platform(a.getPlatform()).audience(a.getAudience()).details(a.getDetails()).status(a.getStatus())
                .reviewedBy(a.getReviewedBy()).reviewedAt(a.getReviewedAt()).ambassadorId(a.getAmbassadorId()).createdAt(a.getCreatedAt()).build();
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
