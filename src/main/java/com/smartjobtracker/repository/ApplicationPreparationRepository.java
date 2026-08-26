package com.smartjobtracker.repository;
import com.smartjobtracker.model.ApplicationPreparation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface ApplicationPreparationRepository extends JpaRepository<ApplicationPreparation, Long> { Optional<ApplicationPreparation> findByIdAndUserId(Long id, Long userId); }