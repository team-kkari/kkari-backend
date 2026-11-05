package com.example.kkaribackend.domain.chat.presentation;

import com.example.kkaribackend.domain.chat.presentation.dto.request.SendChatMessageRequestDto;
import com.example.kkaribackend.domain.chat.presentation.dto.response.ChatHistoryResponseDto;
import com.example.kkaribackend.domain.chat.presentation.dto.response.ChatErrorResponseDto;
import com.example.kkaribackend.domain.chat.presentation.dto.response.SendChatMessageResponseDto;
import com.example.kkaribackend.domain.chat.service.QueryChatMessagesService;
import com.example.kkaribackend.domain.chat.service.SendChatMessageService;
import com.example.kkaribackend.global.ai.exception.AiServerException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Chat")
@RequiredArgsConstructor
@RequestMapping("/v1/projects/{projectId}/chat/messages")
@RestController
public class ChatController {
    private final QueryChatMessagesService queryChatMessagesService;
    private final SendChatMessageService sendChatMessageService;

    @Operation(summary = "대화 기록 조회", description = "대화 전체와 마지막 상담 결과를 돌려준다.")
    @GetMapping
    public ChatHistoryResponseDto get(@PathVariable Long projectId) {
        return queryChatMessagesService.execute(projectId);
    }

    @Operation(summary = "챗봇 메시지 전송", description = "메시지를 저장하고 AI 응답을 돌려준다. 내용 없이 상담 시작이나 실패한 응답을 요청할 수 있다.")
    @PostMapping
    public SendChatMessageResponseDto send(@PathVariable Long projectId,
                                        @Valid @RequestBody SendChatMessageRequestDto request) {
        return sendChatMessageService.execute(projectId, request);
    }

    @ExceptionHandler(AiServerException.class)
    public ResponseEntity<ChatErrorResponseDto> handleAiServerException(AiServerException exception) {
        return ResponseEntity.status(exception.getErrorCode().getStatus()).body(ChatErrorResponseDto.from(exception));
    }
}
