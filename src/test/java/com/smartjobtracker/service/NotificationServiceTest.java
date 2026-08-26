package com.smartjobtracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartjobtracker.config.MetaWhatsAppConfig;
import com.smartjobtracker.model.*;
import com.smartjobtracker.repository.NotificationDeliveryRepository;
import com.smartjobtracker.repository.NotificationPreferenceRepository;
import com.smartjobtracker.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NotificationServiceTest {
    @Test
    void enqueueRequiresExplicitOptInAndVerification() {
        NotificationPreference preference = preference(true, null);
        NotificationPreferenceRepository preferences = mock(NotificationPreferenceRepository.class);
        when(preferences.findByUserId(1L)).thenReturn(Optional.of(preference));
        NotificationService service = service(preferences, mock(NotificationDeliveryRepository.class), mock(NotificationProvider.class));
        assertTrue(service.enqueueWhatsApp(1L, "event-1", "hello").isEmpty());
    }

    @Test
    void enqueueIsIdempotentForSameEvent() {
        NotificationPreference preference = preference(true, OffsetDateTime.now());
        NotificationPreferenceRepository preferences = mock(NotificationPreferenceRepository.class);
        when(preferences.findByUserId(1L)).thenReturn(Optional.of(preference));
        NotificationDelivery existing = new NotificationDelivery(); existing.setDedupeKey("1:WHATSAPP:event-1");
        NotificationDeliveryRepository deliveries = mock(NotificationDeliveryRepository.class);
        when(deliveries.findByDedupeKey("1:WHATSAPP:event-1")).thenReturn(Optional.of(existing));
        assertSame(existing, service(preferences, deliveries, mock(NotificationProvider.class)).enqueueWhatsApp(1L, "event-1", "hello").orElseThrow());
        verify(deliveries, never()).save(any());
    }

    @Test
    void webhookSignatureUsesMetaAppSecret() {
        MetaWhatsAppConfig config = new MetaWhatsAppConfig(); config.setAppSecret("secret");
        NotificationService service = new NotificationService(mock(NotificationPreferenceRepository.class), mock(NotificationDeliveryRepository.class), mock(UserRepository.class), mock(NotificationProvider.class), config);
        String body = "{\"entry\":[]}";
        try {
            var mac = javax.crypto.Mac.getInstance("HmacSHA256"); mac.init(new javax.crypto.spec.SecretKeySpec("secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String signature = "sha256=" + java.util.HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
            assertTrue(service.validWebhookSignature(signature, body));
            assertFalse(service.validWebhookSignature("sha256=wrong", body));
        } catch (Exception ex) { fail(ex); }
    }

    @Test
    void webhookStatusConfirmsOnlyKnownProviderMessage() {
        NotificationDeliveryRepository deliveries = mock(NotificationDeliveryRepository.class);
        NotificationDelivery delivery = new NotificationDelivery(); delivery.setStatus(NotificationDeliveryStatus.SUBMITTED); delivery.setProviderMessageId("wamid.1");
        when(deliveries.findByProviderMessageId("wamid.1")).thenReturn(Optional.of(delivery));
        NotificationService service = service(mock(NotificationPreferenceRepository.class), deliveries, mock(NotificationProvider.class));
        service.applyProviderStatus("wamid.1", "delivered");
        assertEquals(NotificationDeliveryStatus.DELIVERED, delivery.getStatus());
        assertNotNull(delivery.getConfirmedAt());
        verify(deliveries).save(delivery);
        service.applyProviderStatus("unknown", "delivered");
        verify(deliveries, times(1)).save(delivery);
    }

    private NotificationService service(NotificationPreferenceRepository preferences, NotificationDeliveryRepository deliveries, NotificationProvider provider) {
        MetaWhatsAppConfig config = new MetaWhatsAppConfig(); config.setVerifyToken("verify"); config.setAppSecret("secret");
        return new NotificationService(preferences, deliveries, mock(UserRepository.class), provider, config);
    }

    private NotificationPreference preference(boolean optIn, OffsetDateTime verifiedAt) {
        NotificationPreference preference = new NotificationPreference(); preference.setWhatsappOptIn(optIn); preference.setPhoneE164("+15551234567"); preference.setVerifiedAt(verifiedAt); return preference;
    }
}