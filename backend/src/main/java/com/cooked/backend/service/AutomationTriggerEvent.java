package com.cooked.backend.service;

import java.util.UUID;

/**
 * Something custom automations can react to, published by the code where it
 * happens (Spring event): a failed payment or import (about one user) or a
 * creator application (for the team).
 */
public record AutomationTriggerEvent(String trigger, UUID userId, String email, String detail) {
}
