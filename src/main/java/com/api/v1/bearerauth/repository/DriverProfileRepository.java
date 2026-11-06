package com.api.v1.bearerauth.repository;

import com.api.v1.bearerauth.model.entity.DriverProfile;
import com.api.v1.bearerauth.model.entity.AppUser;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface DriverProfileRepository extends JpaRepository<DriverProfile, Long> {
    Optional<DriverProfile> findByUser(AppUser user);
    List<DriverProfile> findByApprovedTrue();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from DriverProfile p where p.user = :user")
    Optional<DriverProfile> findByUserForUpdate(@Param("user") AppUser user);
}