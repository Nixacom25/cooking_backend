package com.cooked.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "device_sessions")
public class DeviceSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String deviceName;

    @Column
    private String location;

    @Column
    private String ipAddress;

    @Column(nullable = false, length = 1000)
    private String token;

    @CreationTimestamp
    private LocalDateTime lastActive;

    public DeviceSession() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public LocalDateTime getLastActive() { return lastActive; }
    public void setLastActive(LocalDateTime lastActive) { this.lastActive = lastActive; }

    public static DeviceSessionBuilder builder() {
        return new DeviceSessionBuilder();
    }

    public static class DeviceSessionBuilder {
        private final DeviceSession session = new DeviceSession();

        public DeviceSessionBuilder id(UUID id) { session.setId(id); return this; }
        public DeviceSessionBuilder user(User user) { session.setUser(user); return this; }
        public DeviceSessionBuilder deviceName(String deviceName) { session.setDeviceName(deviceName); return this; }
        public DeviceSessionBuilder location(String location) { session.setLocation(location); return this; }
        public DeviceSessionBuilder ipAddress(String ipAddress) { session.setIpAddress(ipAddress); return this; }
        public DeviceSessionBuilder token(String token) { session.setToken(token); return this; }
        public DeviceSessionBuilder lastActive(LocalDateTime lastActive) { session.setLastActive(lastActive); return this; }

        public DeviceSession build() {
            return session;
        }
    }
}
