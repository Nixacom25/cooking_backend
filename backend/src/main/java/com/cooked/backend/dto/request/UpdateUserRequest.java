package com.cooked.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateUserRequest {
    @NotBlank
    private String firstname;

    @NotBlank
    private String lastname;

    private String phone;

    private String discoverySource;
    private String otherDiscoverySource;
    private String language;
    private String country;
    private String measurementSystem;

    // Getters et Setters explicites pour garantir la compilation sans Lombok si besoin
    public String getFirstname() { return firstname; }
    public void setFirstname(String firstname) { this.firstname = firstname; }
    public String getLastname() { return lastname; }
    public void setLastname(String lastname) { this.lastname = lastname; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getDiscoverySource() { return discoverySource; }
    public void setDiscoverySource(String discoverySource) { this.discoverySource = discoverySource; }
    public String getOtherDiscoverySource() { return otherDiscoverySource; }
    public void setOtherDiscoverySource(String otherDiscoverySource) { this.otherDiscoverySource = otherDiscoverySource; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
    public String getMeasurementSystem() { return measurementSystem; }
    public void setMeasurementSystem(String measurementSystem) { this.measurementSystem = measurementSystem; }
}
