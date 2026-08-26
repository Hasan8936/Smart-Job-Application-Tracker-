package com.smartjobtracker.repository;

import com.smartjobtracker.model.TailoringSuggestion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TailoringSuggestionRepository extends JpaRepository<TailoringSuggestion, Long> {
    List<TailoringSuggestion> findBySessionIdOrderByIdAsc(Long sessionId);
    Optional<TailoringSuggestion> findByIdAndSessionId(Long id, Long sessionId);
}