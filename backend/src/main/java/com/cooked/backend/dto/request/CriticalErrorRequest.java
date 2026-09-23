package com.cooked.backend.dto.request;

import lombok.Data;

@Data
public class CriticalErrorRequest {
    private String errorType;
    private String errorMessage;
    private String stackTrace;
    private String userId;
    private String userEmail;
    private String platform;
    private String osVersion;
    private String appVersion;
    private Object context;
    private String timestamp;
}