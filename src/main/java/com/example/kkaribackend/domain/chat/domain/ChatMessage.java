package com.example.kkaribackend.domain.chat.domain;

import com.example.kkaribackend.domain.project.domain.Project;
import com.example.kkaribackend.global.ai.dto.IntakeProfile;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Getter
@Table(name = "tbl_chat_message", uniqueConstraints = {
    @UniqueConstraint(name = "uk_chat_project_seq", columnNames = {"project_id", "seq"}),
    @UniqueConstraint(name = "uk_chat_project_client_message", columnNames = {"project_id", "client_message_id"})
})
public class ChatMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(nullable = false)
    private int seq;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChatRole role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(length = 64)
    private String clientMessageId;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> quickReplies;

    @JdbcTypeCode(SqlTypes.JSON)
    private IntakeProfile profile;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> missingFields;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> missing;

    private Boolean ready;

    @JdbcTypeCode(SqlTypes.JSON)
    private String recommendRequest;

    // AI 원본 응답. 알려지지 않은 새 필드도 requirements로 변환하지 않고 보존한다.
    @JdbcTypeCode(SqlTypes.JSON)
    private String aiResponse;

    @Column(length = 64)
    private String infraContextHash;
}
