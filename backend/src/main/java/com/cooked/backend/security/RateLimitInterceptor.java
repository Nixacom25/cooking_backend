package com.cooked.backend.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.Refill;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final ProxyManager<byte[]> proxyManager;

    public RateLimitInterceptor(ProxyManager<byte[]> proxyManager) {
        this.proxyManager = proxyManager;
    }

    private Bucket resolveAuthBucket(String ip) {
        BucketConfiguration configuration = BucketConfiguration.builder()
                .addLimit(Bandwidth.classic(5, Refill.greedy(5, Duration.ofMinutes(1))))
                .build();
        return proxyManager.builder().build(("auth-" + ip).getBytes(StandardCharsets.UTF_8), configuration);
    }

    private Bucket resolveGlobalBucket(String ip) {
        BucketConfiguration configuration = BucketConfiguration.builder()
                .addLimit(Bandwidth.classic(200, Refill.greedy(200, Duration.ofMinutes(1))))
                .build();
        return proxyManager.builder().build(("global-" + ip).getBytes(StandardCharsets.UTF_8), configuration);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String uri = request.getRequestURI();
        String ip = request.getRemoteAddr();

        // 1. Auth Rate Limit (Stricter)
        if (uri.startsWith("/auth/login") || uri.startsWith("/auth/forgot-password")
                || uri.startsWith("/auth/resend-code") || uri.startsWith("/auth/verify-email")) {
            Bucket authBucket = resolveAuthBucket(ip);
            if (!authBucket.tryConsume(1)) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.getWriter().write("Too many requests for auth endpoints");
                return false;
            }
        }

        // 2. Global Rate Limit (For scraping, DDoS prevention)
        Bucket globalBucket = resolveGlobalBucket(ip);
        if (!globalBucket.tryConsume(1)) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.getWriter().write("Too many requests globally");
            return false;
        }

        return true;
    }
}
