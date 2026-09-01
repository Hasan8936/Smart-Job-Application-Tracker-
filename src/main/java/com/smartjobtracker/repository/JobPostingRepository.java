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
    @Query("select j from JobPosting j where (:q is null or lower(j.title) like lower(concat('%', cast(:q as string), '%')) or lower(j.company) like lower(concat('%', cast(:q as string), '%'))) " +
            "and (:location is null or lower(j.location) like lower(concat('%', cast(:location as string), '%'))) " +
            "and (:employmentType is null or lower(j.employmentType) = lower(cast(:employmentType as string))) " +
            "and (:provider is null or lower(j.provider) = lower(cast(:provider as string))) " +
            "and (cast(:postedAfter as timestamp) is null or j.postedAt >= :postedAfter) " +
            "and (cast(:postedBefore as timestamp) is null or j.postedAt <= :postedBefore)")
    Page<JobPosting> search(@Param("q") String q, @Param("location") String location,
                            @Param("employmentType") String employmentType, @Param("provider") String provider,
                            @Param("postedAfter") OffsetDateTime postedAfter, @Param("postedBefore") OffsetDateTime postedBefore,
                            Pageable pageable);

    @Query("select j from JobPosting j where j.createdAt > :since " +
            "and (:q is null or lower(j.title) like lower(concat('%', cast(:q as string), '%')) or lower(j.company) like lower(concat('%', cast(:q as string), '%'))) " +
            "and (:location is null or lower(j.location) like lower(concat('%', cast(:location as string), '%'))) " +
            "and (:employmentType is null or lower(j.employmentType) = lower(cast(:employmentType as string))) " +
            "and (:provider is null or lower(j.provider) = lower(cast(:provider as string)))")
    Page<JobPosting> findNewSince(@Param("since") OffsetDateTime since, @Param("q") String q, @Param("location") String location,
                            @Param("employmentType") String employmentType, @Param("provider") String provider,
                            Pageable pageable);
}