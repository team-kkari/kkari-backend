package com.example.kkaribackend.domain.project.domain.repository;

import com.example.kkaribackend.domain.project.domain.Project;
import com.example.kkaribackend.domain.project.domain.ProjectStage;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    @Query("""
        select p from Project p
        where p.user.id = :userId
          and (:stage is null or p.stage = :stage)
        order by p.updatedAt desc
        """)
    List<Project> findAllByUserId(@Param("userId") Long userId, @Param("stage") ProjectStage stage);

    @Query("""
        select p from Project p
        join fetch p.user
        where p.customer.id = :customerId
        order by p.createdAt desc, p.id desc
        """)
    List<Project> findAllByCustomerIdWithEngineer(@Param("customerId") Long customerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Project p where p.id = :id")
    Optional<Project> findByIdForUpdate(@Param("id") Long id);
}
