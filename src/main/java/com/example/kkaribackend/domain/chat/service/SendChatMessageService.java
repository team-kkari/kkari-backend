package com.example.kkaribackend.domain.chat.service;

import com.example.kkaribackend.domain.analysis.facade.AnalysisFacade;
import com.example.kkaribackend.domain.chat.domain.ChatMessage;
import com.example.kkaribackend.domain.chat.domain.ChatRole;
import com.example.kkaribackend.domain.chat.domain.repository.ChatMessageRepository;
import com.example.kkaribackend.domain.chat.exception.ChatTurnNotAllowedException;
import com.example.kkaribackend.domain.chat.exception.ConcurrentChatMessageException;
import com.example.kkaribackend.domain.chat.presentation.dto.request.SendChatMessageRequestDto;
import com.example.kkaribackend.domain.chat.presentation.dto.response.SendChatMessageResponseDto;
import com.example.kkaribackend.domain.project.domain.Project;
import com.example.kkaribackend.domain.project.facade.ProjectFacade;
import com.example.kkaribackend.global.ai.AiServerClient;
import com.example.kkaribackend.global.ai.dto.IntakeRequestDto;
import com.example.kkaribackend.global.ai.dto.IntakeResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class SendChatMessageService {
    private final ProjectFacade projectFacade;
    private final AnalysisFacade analysisFacade;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatMessageWriter chatMessageWriter;
    private final AiServerClient aiServerClient;

    public SendChatMessageResponseDto execute(Long projectId, SendChatMessageRequestDto request) {
        Project project = projectFacade.getAssignedProject(projectId);
        String infraContext = analysisFacade.getInfraContext(project);
        String hash = hash(infraContext);
        ChatMessage user = null;
        boolean hasContent = request.content() != null && !request.content().isBlank();
        if (hasContent && request.clientMessageId() != null) {
            user = chatMessageRepository.findByProjectIdAndClientMessageId(projectId, request.clientMessageId())
                .orElse(null);
            if (user != null) {
                ChatMessage assistant = chatMessageRepository
                    .findByProjectIdAndSeq(projectId, user.getSeq() + 1)
                    .filter(message -> message.getRole() == ChatRole.ASSISTANT)
                    .orElse(null);
                if (assistant != null) {
                    return SendChatMessageResponseDto.from(user, assistant);
                }
            }
        }

        List<ChatMessage> messages = chatMessageRepository.findAllByProjectIdOrderBySeqAsc(projectId);
        int lastSeq = messages.isEmpty() ? 0 : messages.getLast().getSeq();
        // 다른 사용자 메시지가 진행된 뒤에는 이전 메시지의 재시도로 대화를 이어가지 않는다
        if (user != null && user.getSeq() != lastSeq) {
            throw new ChatTurnNotAllowedException();
        }
        boolean starting = messages.isEmpty() && !hasContent;
        if (hasContent && user == null) {
            try {
                user = chatMessageWriter.saveUser(project, lastSeq + 1, request.content(), request.clientMessageId());
            } catch (DataIntegrityViolationException e) {
                throw new ConcurrentChatMessageException();
            }
            messages = chatMessageRepository.findAllByProjectIdOrderBySeqAsc(projectId);
        } else if (!hasContent && !messages.isEmpty()) {
            ChatMessage last = messages.getLast();
            if (last.getRole() == ChatRole.ASSISTANT && hash.equals(last.getInfraContextHash())) {
                throw new ChatTurnNotAllowedException();
            }
        }

        int responseSeq = messages.isEmpty() ? 1 : messages.getLast().getSeq() + 1;
        IntakeResponseDto result = aiServerClient.intake(new IntakeRequestDto(messages.stream()
            .map(m -> new IntakeRequestDto.Message(m.getRole().name().toLowerCase(Locale.ROOT), m.getContent())).toList(),
            infraContext, "project-" + projectId, null));
        ChatMessage assistant;
        try {
            assistant = chatMessageWriter.saveAssistant(project, responseSeq, result, hash);
        } catch (DataIntegrityViolationException e) {
            if (!starting) {
                throw new ConcurrentChatMessageException();
            }
            assistant = chatMessageRepository
                .findByProjectIdAndSeq(projectId, 1)
                .filter(message -> message.getRole() == ChatRole.ASSISTANT)
                .orElseThrow(ConcurrentChatMessageException::new);
        }
        return SendChatMessageResponseDto.from(user, assistant);
    }

    private String hash(String context) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(context.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 을 사용할 수 없습니다.", e);
        }
    }
}
