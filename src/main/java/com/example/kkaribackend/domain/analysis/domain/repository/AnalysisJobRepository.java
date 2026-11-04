package com.example.kkaribackend.domain.analysis.domain.repository;

import com.example.kkaribackend.domain.analysis.domain.AnalysisJob;
import com.example.kkaribackend.domain.analysis.domain.AnalysisStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;

public interface AnalysisJobRepository extends JpaRepository<AnalysisJob, Long> {

    Optional<AnalysisJob> findFirstByProjectIdAndStatusInOrderByIdDesc(Long projectId,
                                                                       Collection<AnalysisStatus> statuses);

    Optional<AnalysisJob> findFirstByProjectIdAndStatusOrderByIdDesc(Long projectId, AnalysisStatus status);

    // 비동기 실행 스레드에는 영속성 컨텍스트가 없어 프로젝트를 함께 읽는다
    @Query("select j from AnalysisJob j join fetch j.project where j.id = :id")
    Optional<AnalysisJob> findWithProjectById(@Param("id") Long id);
}
