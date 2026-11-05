package com.example.kkaribackend.domain.pricing.domain.repository;

import com.example.kkaribackend.domain.pricing.domain.UnitPrice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UnitPriceRepository extends JpaRepository<UnitPrice, Long> {
    Optional<UnitPrice> findByServiceCodeAndUsageTypeAndOperation(String serviceCode, String usageType,
                                                                  String operation);
}
