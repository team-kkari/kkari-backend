package com.example.kkaribackend.domain.analysis.domain.repository;

import com.example.kkaribackend.domain.analysis.domain.AnalysisJob;
import com.example.kkaribackend.domain.analysis.domain.AnalysisJobSummary;
import com.example.kkaribackend.domain.analysis.domain.AnalysisJobType;
import com.example.kkaribackend.domain.analysis.domain.AnalysisStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AnalysisJobRepository extends JpaRepository<AnalysisJob, Long> {

    @Query("""
        select new com.example.kkaribackend.domain.analysis.domain.AnalysisJobSummary(j.id, j.status, j.failureReason)
        from AnalysisJob j
        where j.project.id = :projectId and j.type = :type
          and j.id = (select max(n.id) from AnalysisJob n where n.project.id = :projectId and n.type = :type)
        """)
    Optional<AnalysisJobSummary> findFirstByProjectIdAndTypeOrderByIdDesc(@Param("projectId") Long projectId,
                                                                       @Param("type") AnalysisJobType type);

    // 목록의 프로젝트마다 개별 조회하지 않고 최근 성공한 분석을 한 번에 읽는다.
    @Query("""
        select j from AnalysisJob j
        where j.project.id in :projectIds and j.type = :type and j.status = :status
          and j.id = (select max(n.id) from AnalysisJob n
                      where n.project.id = j.project.id and n.type = :type and n.status = :status)
        """)
    List<AnalysisJob> findLatestByProjectIdsAndTypeAndStatus(@Param("projectIds") Collection<Long> projectIds,
                                                           @Param("type") AnalysisJobType type,
                                                           @Param("status") AnalysisStatus status);

    Optional<AnalysisJob> findFirstByProjectIdAndTypeAndStatusInOrderByIdDesc(Long projectId, AnalysisJobType type,
                                                                              Collection<AnalysisStatus> statuses);

    Optional<AnalysisJob> findFirstByProjectIdAndTypeAndStatusOrderByIdDesc(Long projectId, AnalysisJobType type,
                                                                            AnalysisStatus status);

    // 비동기 실행 스레드에는 영속성 컨텍스트가 없어 프로젝트를 함께 읽는다
    @Query("select j from AnalysisJob j join fetch j.project where j.id = :id")
    Optional<AnalysisJob> findWithProjectById(@Param("id") Long id);
}
