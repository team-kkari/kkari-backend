package com.example.kkaribackend.domain.tenant.domain.repository;

import com.example.kkaribackend.domain.tenant.domain.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<Tenant, Long> {
}
