package com.smartjobtracker.service;

import com.smartjobtracker.model.IngestedEmail;
import com.smartjobtracker.repository.IngestedEmailRepository;
import com.smartjobtracker.repository.UserRepository;
import com.smartjobtracker.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@ActiveProfiles("test")
class IngestedEmailRepositoryTest {
    @Autowired
    private IngestedEmailRepository repository;
    @Autowired
    private UserRepository users;

    @Test
    void rejectsDuplicateGmailMessageForTheSameUser() {
        User user = new User(); user.setName("Test"); user.setEmail("gmail-test@example.com"); user.setPasswordHash("hash");
        Long userId = users.saveAndFlush(user).getId();
        IngestedEmail first = email(userId, "gmail-123");
        repository.saveAndFlush(first);
        IngestedEmail duplicate = email(userId, "gmail-123");
        assertThrows(DataIntegrityViolationException.class, () -> repository.saveAndFlush(duplicate));
    }

    private IngestedEmail email(Long userId, String messageId) {
        IngestedEmail email = new IngestedEmail();
        email.setUserId(userId);
        email.setGmailMessageId(messageId);
        return email;
    }
}
