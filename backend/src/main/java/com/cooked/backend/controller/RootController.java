package com.cooked.backend.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class RootController {

    /**
     * Redirects the root path ("/") to the main landing page or store URL.
     * This prevents users from seeing a 403 or exposing the API backend when
     * they visit the domain directly (e.g., https://api.cookedapp.com).
     */
    @GetMapping("/")
    public String redirectRoot() {
        return "redirect:https://play.google.com/store/apps/details?id=com.cookedapp.app"; 
    }
}
