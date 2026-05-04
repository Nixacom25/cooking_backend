package com.cooked.backend.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public class SessionResponse {
    private UUID id;
    private String deviceName;
    private String location;
    private String ipAddress;
    private LocalDateTime lastActive;
    private boolean isCurrentSession;

    public SessionResponse() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public LocalDateTime getLastActive() { return lastActive; }
    public void setLastActive(LocalDateTime lastActive) { this.lastActive = lastActive; }
    public boolean isCurrentSession() { return isCurrentSession; }
    public void setCurrentSession(boolean isCurrentSession) { this.isCurrentSession = isCurrentSession; }

    public static SessionResponseBuilder builder() {
        return new SessionResponseBuilder();
    }

    public static class SessionResponseBuilder {
        private final SessionResponse response = new SessionResponse();

        public SessionResponseBuilder id(UUID id) { response.setId(id); return this; }
        public SessionResponseBuilder deviceName(String deviceName) { response.setDeviceName(deviceName); return this; }
        public SessionResponseBuilder location(String location) { response.setLocation(location); return this; }
        public SessionResponseBuilder ipAddress(String ipAddress) { response.setIpAddress(ipAddress); return this; }
        public SessionResponseBuilder lastActive(LocalDateTime lastActive) { response.setLastActive(lastActive); return this; }
        public SessionResponseBuilder isCurrentSession(boolean isCurrentSession) { response.setCurrentSession(isCurrentSession); return this; }

        public SessionResponse build() {
            return response;
        }
    }
}
