package com.smartjobtracker.repository;

import com.smartjobtracker.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    Optional<User> findByEmailIgnoreCase(String email);

    /** Admin console search: email or name contains {@code q}; optional sign-up date window. */
    @Query("select u from User u where (:q is null or lower(u.email) like lower(concat('%', cast(:q as string), '%')) " +
            "or lower(u.name) like lower(concat('%', cast(:q as string), '%'))) " +
            "and (cast(:createdFrom as timestamp) is null or u.createdAt >= :createdFrom) " +
            "and (cast(:createdTo as timestamp) is null or u.createdAt <= :createdTo)")
    Page<User> adminSearch(@Param("q") String q, @Param("createdFrom") OffsetDateTime createdFrom,
                           @Param("createdTo") OffsetDateTime createdTo, Pageable pageable);
}
