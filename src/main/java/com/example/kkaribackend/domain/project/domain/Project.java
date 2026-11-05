package com.example.kkaribackend.domain.project.domain;

import com.example.kkaribackend.domain.user.domain.User;
import com.example.kkaribackend.global.terraform.dto.TerraformInfra;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Getter
@Table(name = "tbl_project")
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 담당 엔지니어
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 고객사명
    @Column(length = 100, nullable = false)
    private String companyName;

    // 고객사 측 담당자
    @Column(length = 50, nullable = false)
    private String contactName;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private ProjectStage stage = ProjectStage.RECEIVED;

    // 고객사 Role 신뢰 정책에 넣을 값. 서버가 생성하고 바꾸지 않는다
    @Builder.Default
    @Column(length = 36, nullable = false, unique = true, updatable = false)
    private String externalId = UUID.randomUUID().toString();

    @Column(length = 2048)
    private String roleArn;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private AwsConnectionStatus awsConnectionStatus = AwsConnectionStatus.NOT_CONNECTED;

    // 고객 요구사항. 항상 통째로 교체하므로 JSON 한 컬럼에 둔다. 입력 전에는 null
    @JdbcTypeCode(SqlTypes.JSON)
    private Requirements requirements;

    // 선택 입력인 Terraform state 에서 읽은 리소스 구조. 다시 올리면 교체한다. 원본 파일은 저장하지 않는다
    @JdbcTypeCode(SqlTypes.JSON)
    private TerraformInfra terraform;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public void registerRoleArn(String roleArn) {
        this.roleArn = roleArn;
        this.awsConnectionStatus = AwsConnectionStatus.UNTESTED;
    }

    public void updateAwsConnectionStatus(AwsConnectionStatus awsConnectionStatus) {
        this.awsConnectionStatus = awsConnectionStatus;
    }

    public void updateRequirements(Requirements requirements) {
        this.requirements = requirements;
    }

    public void updateTerraform(TerraformInfra terraform) {
        this.terraform = terraform;
    }

    public void markAnalyzed() {
        advanceTo(ProjectStage.ANALYZED);
    }

    public void markRecommended() {
        advanceTo(ProjectStage.RECOMMENDED);
    }

    // 재분석 등으로 다시 호출돼도 단계는 뒤로 가지 않는다
    private void advanceTo(ProjectStage stage) {
        if (stage.compareTo(this.stage) > 0) {
            this.stage = stage;
        }
    }
}
