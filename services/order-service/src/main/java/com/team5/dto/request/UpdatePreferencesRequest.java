package com.team5.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class UpdatePreferencesRequest {

    @NotBlank
    @Pattern(regexp = "INR|USD")
    private String currency;

    @NotBlank
    @Pattern(regexp = "LIGHT|DARK")
    private String theme;

    @NotBlank
    @Pattern(regexp = "EMAIL|PUSH|BROWSER|SMS|NONE")
    private String alertChannel;

    public UpdatePreferencesRequest() {
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getTheme() {
        return theme;
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }

    public String getAlertChannel() {
        return alertChannel;
    }

    public void setAlertChannel(String alertChannel) {
        this.alertChannel = alertChannel;
    }
}