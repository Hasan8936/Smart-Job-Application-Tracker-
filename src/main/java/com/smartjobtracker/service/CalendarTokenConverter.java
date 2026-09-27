package com.smartjobtracker.service;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA side of {@link CalendarTokenCrypto}. Deliberately not a Spring bean: Hibernate creates it with the no-arg
 * constructor, and it uses the application's crypto instance when there is one (plaintext otherwise).
 */
@Converter
public class CalendarTokenConverter implements AttributeConverter<String, String> {
    @Override
    public String convertToDatabaseColumn(String value) {
        CalendarTokenCrypto crypto = CalendarTokenCrypto.current();
        return crypto == null ? value : crypto.encrypt(value);
    }

    @Override
    public String convertToEntityAttribute(String stored) {
        if (stored == null || !stored.startsWith(CalendarTokenCrypto.PREFIX)) return stored;
        CalendarTokenCrypto crypto = CalendarTokenCrypto.current();
        if (crypto == null) throw new IllegalStateException("Encrypted Google Calendar token but no encryption key is available");
        return crypto.decrypt(stored);
    }
}
