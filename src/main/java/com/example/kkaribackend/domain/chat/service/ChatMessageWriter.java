package com.example.kkaribackend.domain.chat.service;

import com.example.kkaribackend.domain.chat.domain.ChatMessage;
import com.example.kkaribackend.domain.chat.domain.ChatRole;
import com.example.kkaribackend.domain.chat.domain.repository.ChatMessageRepository;
import com.example.kkaribackend.domain.project.domain.Project;
import com.example.kkaribackend.global.ai.dto.IntakeResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ChatMessageWriter {
    private final ChatMessageRepository chatMessageRepository;

    @Transactional
    public ChatMessage saveUser(Project project, int seq, String content, String clientMessageId) {
        return chatMessageRepository.saveAndFlush(ChatMessage.builder()
            .project(project).seq(seq).role(ChatRole.USER).content(content).clientMessageId(clientMessageId).build());
    }

    @Transactional
    public ChatMessage saveAssistant(Project project, int seq, IntakeResponseDto response, String infraContextHash) {
        // AI 에 보낸 마지막 seq 다음에만 저장한다. 대화가 바뀌면 유니크 제약으로 거절한다
        return chatMessageRepository.saveAndFlush(ChatMessage.builder()
            .project(project).seq(seq).role(ChatRole.ASSISTANT).content(response.reply())
            .quickReplies(response.quickReplies()).profile(response.profile()).missingFields(response.missingFields())
            .ready(response.ready())
            // Hibernate 의 JSON 매퍼와 API 의 Jackson 버전에 의존하지 않도록 JSON 문자열로 둔다
            .recommendRequest(response.recommendRequest() == null || response.recommendRequest().isNull()
                ? null : response.recommendRequest().toString())
            .infraContextHash(infraContextHash)
            .build());
    }
}
