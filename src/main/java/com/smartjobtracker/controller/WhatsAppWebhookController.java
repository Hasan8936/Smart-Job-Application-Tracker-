package com.smartjobtracker.controller;

import com.smartjobtracker.config.MetaWhatsAppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/whatsapp/webhook")
@ConditionalOnProperty(name = "app.whatsapp.enabled", havingValue = "true")
public class WhatsAppWebhookController {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookController.class);
    private final MetaWhatsAppConfig config;

    public WhatsAppWebhookController(MetaWhatsAppConfig config) {
        this.config = config;
    }

    /** Meta webhook verification handshake. */
    @GetMapping
    public ResponseEntity<String> verify(
            @RequestParam("hub.mode") String mode,
            @RequestParam("hub.verify_token") String token,
            @RequestParam("hub.challenge") String challenge) {

        if ("subscribe".equals(mode) && config.getVerifyToken().equals(token)) {
            log.info("WhatsApp webhook verified");
            return ResponseEntity.ok(challenge);
        }
        log.warn("WhatsApp webhook verification failed: mode={}, token match={}", mode, config.getVerifyToken().equals(token));
        return ResponseEntity.status(403).build();
    }

    /** Incoming message events from Meta. */
    @PostMapping
    public ResponseEntity<Void> receive(@RequestBody String payload) {
        log.debug("WhatsApp webhook event received");
        return ResponseEntity.ok().build();
    }
}
