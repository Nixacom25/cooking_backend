package com.cooked.backend.entity;

public enum GiftCodeStatus {
    AVAILABLE,
    REDEEMED,
    /** The purchase was refunded before the code was used. */
    VOIDED
}
