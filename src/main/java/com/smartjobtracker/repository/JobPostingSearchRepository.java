package com.smartjobtracker.repository;

import com.smartjobtracker.model.JobPosting;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.Repository;

/** Read-only criteria search over job postings (the job board's search box); see JobSearch. */
public interface JobPostingSearchRepository extends Repository<JobPosting, Long>, JpaSpecificationExecutor<JobPosting> {
}
