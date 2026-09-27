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
    @Query("select j from JobPosting j where j.scriptBlocked = false and (:q is null or lower(j.title) like lower(concat('%', cast(:q as string), '%')) or lower(j.company) like lower(concat('%', cast(:q as string), '%'))) " +
            "and (:location is null or lower(j.location) like lower(concat('%', cast(:location as string), '%'))) " +
            "and (:employmentType is null or lower(j.employmentType) = lower(cast(:employmentType as string))) " +
            "and (:provider is null or lower(j.provider) = lower(cast(:provider as string))) " +
            "and (cast(:postedAfter as timestamp) is null or j.postedAt >= :postedAfter) " +
            "and (cast(:postedBefore as timestamp) is null or j.postedAt <= :postedBefore) " +
            "and (:country is null or j.countryCode = :country) " +
            // Preferred country first; the Pageable's sort (e.g. postedAt desc) applies within each group.
            "order by case when j.countryCode = :preferredCountry then 0 else 1 end")
    Page<JobPosting> search(@Param("q") String q, @Param("location") String location,
                            @Param("employmentType") String employmentType, @Param("provider") String provider,
                            @Param("postedAfter") OffsetDateTime postedAfter, @Param("postedBefore") OffsetDateTime postedBefore,
                            @Param("country") String country, @Param("preferredCountry") String preferredCountry,
                            Pageable pageable);

    /** Postings whose yearly salary was reported by the source or stated in the posting (the estimator's evidence). */
    @Query("select j from JobPosting j where j.salarySource in ('PROVIDER', 'DESCRIPTION') and j.salaryPeriod = 'YEAR' " +
            "and j.salaryCurrency is not null and (j.salaryMin is not null or j.salaryMax is not null)")
    List<JobPosting> findWithReportedYearlySalary();

    /**
     * Postings with no reported salary: blank, previously estimated, or legacy (pre-V25) estimates. Legacy rows with a
     * salary that was NOT flagged estimated came from a job source, so they are excluded and never overwritten.
     */
    @Query("select j from JobPosting j where j.salarySource = 'ESTIMATE' or (j.salarySource is null and " +
            "(j.salaryEstimated = true or (j.salaryMin is null and j.salaryMax is null)))")
    List<JobPosting> findWithoutReportedSalary();

    /** Delete job postings older than {@code cutoff} that no user has saved, bookmarked, or applied to. */
    @org.springframework.data.jpa.repository.Modifying
    @Query("delete from JobPosting j where j.createdAt < :cutoff and j.id not in (select s.jobPostingId from SavedJob s)")
    int deleteStaleJobs(@Param("cutoff") OffsetDateTime cutoff);

    @Query("select j from JobPosting j where j.scriptBlocked = false and j.createdAt > :since " +
            "and (:q is null or lower(j.title) like lower(concat('%', cast(:q as string), '%')) or lower(j.company) like lower(concat('%', cast(:q as string), '%'))) " +
            "and (:location is null or lower(j.location) like lower(concat('%', cast(:location as string), '%'))) " +
            "and (:employmentType is null or lower(j.employmentType) = lower(cast(:employmentType as string))) " +
            "and (:provider is null or lower(j.provider) = lower(cast(:provider as string))) " +
            "and (:country is null or j.countryCode = :country) " +
            "order by case when j.countryCode = :preferredCountry then 0 else 1 end")
    Page<JobPosting> findNewSince(@Param("since") OffsetDateTime since, @Param("q") String q, @Param("location") String location,
                            @Param("employmentType") String employmentType, @Param("provider") String provider,
                            @Param("country") String country, @Param("preferredCountry") String preferredCountry,
                            Pageable pageable);
}