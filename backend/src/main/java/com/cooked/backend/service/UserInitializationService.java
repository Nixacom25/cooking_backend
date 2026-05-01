package com.cooked.backend.service;

import com.cooked.backend.entity.User;

public interface UserInitializationService {
    /**
     * Initializes a new user account with default content:
     * - 2 default cookbooks (e.g., "Mes Créations" and "À Tester")
     * - 8 initial recipes matching user preferences (AI generated or picked)
     * 
     * @param user The user to initialize
     */
    void initializeAccount(User user);
}
