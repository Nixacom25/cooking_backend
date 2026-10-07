package com.cooked.backend.service.impl;

import com.cooked.backend.entity.CriticalError;
import com.cooked.backend.service.CriticalErrorNotifier;
import com.cooked.backend.service.EmailService;
import com.cooked.backend.service.SlackNotifier;
import com.cooked.backend.service.WorkspaceSettingsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Email (if "Critical alerts by email" is on) and Slack (if a webhook is set; no user identity sent). */
@Service
public class CriticalErrorNotifierImpl implements CriticalErrorNotifier {

    private final EmailService emailService;
    private final SlackNotifier slack;
    private final WorkspaceSettingsService settings;
    private final String supportTeamEmail;

    public CriticalErrorNotifierImpl(EmailService emailService, SlackNotifier slack, WorkspaceSettingsService settings,
                                     @Value("${support.notification.email:contact@cookedapp.com}") String supportTeamEmail) {
        this.emailService = emailService;
        this.slack = slack;
        this.settings = settings;
        this.supportTeamEmail = supportTeamEmail;
    }

    @Override
    public void notify(CriticalError e, String shortId) {
        if (settings.current().isCriticalAlertsEmail()) {
            emailService.sendCriticalErrorAlert(supportTeamEmail, shortId, e.getErrorType(), e.getErrorMessage(), e.getUserId(),
                    e.getUserEmail(), e.getPlatform(), e.getOsVersion(), e.getAppVersion(), e.getContext());
        }
        String message = e.getErrorMessage() == null ? "" : e.getErrorMessage();
        if (message.length() > 200) message = message.substring(0, 200) + "…";
        slack.post("🚨 Critical error " + shortId + " · " + e.getErrorType() + " · " + nz(e.getPlatform()) + " " + nz(e.getAppVersion()) + "\n" + message);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
