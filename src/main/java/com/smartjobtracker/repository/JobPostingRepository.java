package com.smartjobtracker.repository;

import com.smartjobtracker.model.JobPosting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface JobPostingRepository extends JpaRepository<JobPosting, Long> {
    Optional<JobPosting> findByProviderAndExternalId(String provider, String externalId);
    Optional<JobPosting> findByDedupeHash(String dedupeHash);
    @Query("select j from JobPosting j where (:q is null or lower(j.title) like lower(concat('%', :q, '%')) or lower(j.company) like lower(concat('%', :q, '%'))) " +
            "and (:location is null or lower(j.location) like lower(concat('%', :location, '%'))) " +
            "and (:employmentType is null or lower(j.employmentType) = lower(:employmentType)) " +
            "and (:provider is null or lower(j.provider) = lower(:provider)) " +
            "and (:postedAfter is null or j.postedAt >= :postedAfter) " +
            "and (:postedBefore is null or j.postedAt <= :postedBefore)")
    Page<JobPosting> search(@Param("q") String q, @Param("location") String location,
                            @Param("employmentType") String employmentType, @Param("provider") String provider,
                            @Param("postedAfter") OffsetDateTime postedAfter, @Param("postedBefore") OffsetDateTime postedBefore,
                            Pageable pageable);
}