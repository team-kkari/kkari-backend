package com.example.kkaribackend.domain.chat.service;

import com.example.kkaribackend.domain.chat.domain.ChatMessage;
import com.example.kkaribackend.domain.chat.domain.ChatRole;
import com.example.kkaribackend.domain.chat.domain.repository.ChatMessageRepository;
import com.example.kkaribackend.domain.chat.presentation.dto.response.ChatHistoryResponseDto;
import com.example.kkaribackend.domain.chat.presentation.dto.response.ChatMessageResponseDto;
import com.example.kkaribackend.domain.chat.presentation.dto.response.IntakeProfileResponseDto;
import com.example.kkaribackend.domain.project.facade.ProjectFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class QueryChatMessagesService {
    private final ProjectFacade projectFacade;
    private final ChatMessageRepository chatMessageRepository;

    @Transactional(readOnly = true)
    public ChatHistoryResponseDto execute(Long projectId) {
        projectFacade.getAssignedProject(projectId);
        List<ChatMessage> messages = chatMessageRepository.findAllByProjectIdOrderBySeqAsc(projectId);
        ChatMessage latest = messages.stream().filter(m -> m.getRole() == ChatRole.ASSISTANT)
            .reduce((previous, current) -> current).orElse(null);
        return new ChatHistoryResponseDto(messages.stream().map(ChatMessageResponseDto::from).toList(),
            latest == null ? List.of() : latest.getMissingFields(),
            latest == null ? null : latest.getReady(), IntakeProfileResponseDto.from(latest == null ? null : latest.getProfile()));
    }
}
