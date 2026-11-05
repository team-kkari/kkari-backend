package com.example.kkaribackend.domain.chat.domain.repository;

import com.example.kkaribackend.domain.chat.domain.ChatMessage;
import com.example.kkaribackend.domain.chat.domain.ChatRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findAllByProjectIdOrderBySeqAsc(Long projectId);
    Optional<ChatMessage> findFirstByProjectIdOrderBySeqDesc(Long projectId);
    Optional<ChatMessage> findFirstByProjectIdAndRoleOrderBySeqDesc(Long projectId, ChatRole role);
    Optional<ChatMessage> findByProjectIdAndClientMessageId(Long projectId, String clientMessageId);
    Optional<ChatMessage> findByProjectIdAndSeq(Long projectId, int seq);
}
