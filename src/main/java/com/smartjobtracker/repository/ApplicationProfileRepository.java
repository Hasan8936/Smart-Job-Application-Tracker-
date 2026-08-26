package com.smartjobtracker.repository;
import com.smartjobtracker.model.ApplicationProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface ApplicationProfileRepository extends JpaRepository<ApplicationProfile, Long> { Optional<ApplicationProfile> findByUserId(Long userId); }