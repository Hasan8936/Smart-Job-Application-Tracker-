package com.smartjobtracker.repository;

import com.smartjobtracker.model.GoogleCalendarToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GoogleCalendarTokenRepository extends JpaRepository<GoogleCalendarToken, Long> {
    Optional<GoogleCalendarToken> findByUserId(Long userId);
    void deleteByUserId(Long userId);
}
