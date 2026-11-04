package com.example.kkaribackend.domain.project.domain.repository;

import com.example.kkaribackend.domain.project.domain.Project;
import com.example.kkaribackend.domain.project.domain.ProjectStage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    @Query("""
        select p from Project p
        where p.user.id = :userId
          and (:stage is null or p.stage = :stage)
        order by p.updatedAt desc
        """)
    List<Project> findAllByUserId(@Param("userId") Long userId, @Param("stage") ProjectStage stage);
}
