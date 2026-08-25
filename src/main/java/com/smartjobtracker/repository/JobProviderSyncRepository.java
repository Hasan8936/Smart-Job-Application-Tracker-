package com.smartjobtracker.repository;

import com.smartjobtracker.model.JobProviderSync;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface JobProviderSyncRepository extends JpaRepository<JobProviderSync, Long> {
    Optional<JobProviderSync> findByProviderAndQueryKey(String provider, String queryKey);
}