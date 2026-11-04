package com.example.kkaribackend.domain.engagement.domain.repository;

import com.example.kkaribackend.domain.engagement.domain.Engagement;
import com.example.kkaribackend.domain.engagement.domain.EngagementStage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EngagementRepository extends JpaRepository<Engagement, Long> {

    // 유저가 담당하는 고객사마다 가장 최근 의뢰 하나씩, 최근 수정 순
    @Query("""
        select e from Engagement e
        join fetch e.tenant t
        where t.user.id = :userId
          and e.id = (select max(e2.id) from Engagement e2 where e2.tenant = t)
          and (:stage is null or e.stage = :stage)
        order by e.updatedAt desc
        """)
    List<Engagement> findLatestByUserId(@Param("userId") Long userId, @Param("stage") EngagementStage stage);
}
