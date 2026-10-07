package com.cooked.backend.service;

/** Posts a message to the workspace's Slack webhook, when one is configured (async, never throws). */
public interface SlackNotifier {
    void post(String text);
}
