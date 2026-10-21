package com.baedalsak.member.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.baedalsak.member.domain.RiderProfile;

public interface RiderProfileRepository extends JpaRepository<RiderProfile, Long> {
  
}
