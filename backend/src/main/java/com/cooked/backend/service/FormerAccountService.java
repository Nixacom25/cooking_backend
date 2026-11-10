package com.cooked.backend.service;

/** Remembers deleted accounts (hashed email only) so a returning person gets no second free trial. */
public interface FormerAccountService {

    void remember(String email);

    boolean hadAccountBefore(String email);
}
