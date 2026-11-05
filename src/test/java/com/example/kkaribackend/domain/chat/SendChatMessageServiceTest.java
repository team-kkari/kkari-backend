package com.example.kkaribackend.domain.chat;

import com.example.kkaribackend.domain.analysis.facade.AnalysisFacade;
import com.example.kkaribackend.domain.chat.domain.ChatMessage;
import com.example.kkaribackend.domain.chat.domain.ChatRole;
import com.example.kkaribackend.domain.chat.domain.repository.ChatMessageRepository;
import com.example.kkaribackend.domain.chat.exception.ConcurrentChatMessageException;
import com.example.kkaribackend.domain.chat.presentation.dto.request.SendChatMessageRequestDto;
import com.example.kkaribackend.domain.chat.service.ChatMessageWriter;
import com.example.kkaribackend.domain.chat.service.SendChatMessageService;
import com.example.kkaribackend.domain.project.domain.Project;
import com.example.kkaribackend.domain.project.facade.ProjectFacade;
import com.example.kkaribackend.global.ai.AiServerClient;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SendChatMessageServiceTest {
    @Test
    void 사용자_저장과_기록_재조회_사이에_다른_메시지가_끼어들면_AI를_호출하지_않는다() {
        Project project = Project.builder().id(1L).build();
        ProjectFacade projects = mock(ProjectFacade.class);
        AnalysisFacade analysis = mock(AnalysisFacade.class);
        ChatMessageRepository repository = mock(ChatMessageRepository.class);
        ChatMessageWriter writer = mock(ChatMessageWriter.class);
        AiServerClient ai = mock(AiServerClient.class);
        when(projects.getAssignedProject(1L)).thenReturn(project);
        when(analysis.getInfraContext(project)).thenReturn("");

        List<ChatMessage> rows = new ArrayList<>();
        when(repository.findAllByProjectIdOrderBySeqAsc(1L)).thenAnswer(invocation -> List.copyOf(rows));
        when(writer.saveUser(project, 1, "첫 메시지", "A")).thenAnswer(invocation -> {
            ChatMessage first = ChatMessage.builder().id(1L).seq(1).role(ChatRole.USER)
                .content("첫 메시지").clientMessageId("A").build();
            rows.add(first);
            // A의 저장은 끝났지만 기록 재조회 전 B의 저장도 완료된 순서를 재현한다.
            rows.add(ChatMessage.builder().id(2L).seq(2).role(ChatRole.USER)
                .content("다음 메시지").clientMessageId("B").build());
            return first;
        });
        SendChatMessageService service = new SendChatMessageService(projects, analysis, repository, writer, ai);

        assertThatThrownBy(() -> service.execute(1L, new SendChatMessageRequestDto("첫 메시지", "A")))
            .isInstanceOf(ConcurrentChatMessageException.class);

        assertThat(rows).extracting(ChatMessage::getClientMessageId).containsExactly("A", "B");
        verifyNoInteractions(ai);
        verify(writer, never()).saveAssistant(any(), anyInt(), any(), anyString());
    }
}
