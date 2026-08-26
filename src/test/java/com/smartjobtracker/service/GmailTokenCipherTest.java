package com.smartjobtracker.service;

import com.smartjobtracker.config.GmailConfig;
import org.junit.jupiter.api.Test;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;

class GmailTokenCipherTest {
    @Test
    void encryptsAndDecryptsWithoutStoringPlaintext() {
        GmailConfig config = new GmailConfig();
        config.setEncryptionKey(Base64.getEncoder().encodeToString(new byte[32]));
        GmailTokenCipher cipher = new GmailTokenCipher(config);
        String encrypted = cipher.encrypt("refresh-token");
        assertNotEquals("refresh-token", encrypted);
        assertEquals("refresh-token", cipher.decrypt(encrypted));
    }
}