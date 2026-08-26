package com.smartjobtracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartjobtracker.config.MetaWhatsAppConfig;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class MetaWhatsAppProvider implements NotificationProvider {
    private final MetaWhatsAppConfig config;
    private final RestClient client;

    public MetaWhatsAppProvider(MetaWhatsAppConfig config, RestClient.Builder builder) {
        this.config = config;
        this.client = builder.baseUrl("https://graph.facebook.com").build();
    }

    @Override public String channel() { return "WHATSAPP"; }

    @Override
    public Submission send(String recipient, String message) {
        if (!config.isEnabled() || blank(config.getAccessToken()) || blank(config.getPhoneNumberId())) {
            throw new IllegalStateException("WhatsApp provider is not configured");
        }
        JsonNode response = client.post()
                .uri("/" + config.getApiVersion() + "/" + config.getPhoneNumberId() + "/messages")
                .header("Authorization", "Bearer " + config.getAccessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("messaging_product", "whatsapp", "recipient_type", "individual",
                        "to", recipient, "type", "text", "text", Map.of("preview_url", false, "body", message)))
                .retrieve().body(JsonNode.class);
        String id = response == null || response.path("messages").isEmpty()
                ? null : response.path("messages").get(0).path("id").asText(null);
        if (blank(id)) throw new IllegalStateException("Meta returned no message id");
        return new Submission(id);
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
}