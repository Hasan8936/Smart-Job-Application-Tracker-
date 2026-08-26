package com.smartjobtracker.repository;
import com.smartjobtracker.model.GeneratedDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface GeneratedDocumentRepository extends JpaRepository<GeneratedDocument, Long> { List<GeneratedDocument> findByUserIdAndJobPostingIdOrderByCreatedAtDesc(Long userId, Long jobPostingId); }