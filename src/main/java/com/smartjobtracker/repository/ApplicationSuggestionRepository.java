package com.smartjobtracker.repository;
import com.smartjobtracker.model.ApplicationSuggestion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ApplicationSuggestionRepository extends JpaRepository<ApplicationSuggestion, Long> { List<ApplicationSuggestion> findByPreparationIdOrderByIdAsc(Long preparationId); }