package com.smartjobtracker.repository;

import com.smartjobtracker.model.UserPhoto;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserPhotoRepository extends JpaRepository<UserPhoto, Long> {
}
