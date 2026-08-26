package com.smartjobtracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class NotificationPreferenceDto {
    @NotBlank
    @Pattern(regexp = "\\+[1-9][0-9]{7,14}", message = "phoneE164 must be an international E.164 phone number")
    private String phoneE164;
    private boolean whatsappOptIn;
    @Size(max = 100)
    private String consentSource = "settings";

    public String getPhoneE164() { return phoneE164; }
    public void setPhoneE164(String value) { phoneE164 = value; }
    public boolean isWhatsappOptIn() { return whatsappOptIn; }
    public void setWhatsappOptIn(boolean value) { whatsappOptIn = value; }
    public String getConsentSource() { return consentSource; }
    public void setConsentSource(String value) { consentSource = value; }
}