package com.smartjobtracker.repository;
import com.smartjobtracker.model.ApplicationFieldMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ApplicationFieldMappingRepository extends JpaRepository<ApplicationFieldMapping, Long> { List<ApplicationFieldMapping> findByPreparationId(Long preparationId); }