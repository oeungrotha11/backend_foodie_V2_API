package com.api.v1.bearerauth.repository;

import com.api.v1.bearerauth.model.entity.DriverProfile;
import com.api.v1.bearerauth.model.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface DriverProfileRepository extends JpaRepository<DriverProfile, Long> {
    Optional<DriverProfile> findByUser(AppUser user);
    List<DriverProfile> findByApprovedTrue();
}
