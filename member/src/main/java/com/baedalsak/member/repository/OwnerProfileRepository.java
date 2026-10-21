package com.baedalsak.member.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.baedalsak.member.domain.OwnerProfile;

public interface OwnerProfileRepository extends JpaRepository<OwnerProfile, Long>{
  
}
