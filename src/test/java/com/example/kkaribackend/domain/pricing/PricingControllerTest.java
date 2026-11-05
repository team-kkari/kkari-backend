package com.example.kkaribackend.domain.pricing;

import com.example.kkaribackend.global.aws.ArchitectureCostEstimator;
import com.example.kkaribackend.global.aws.dto.ArchitectureNode;
import com.example.kkaribackend.global.aws.dto.CostReport;
import com.example.kkaribackend.global.security.internal.InternalTokenAuthorizationManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "internal-api.token=" + PricingControllerTest.TOKEN)
@AutoConfigureMockMvc
class PricingControllerTest {
    static final String TOKEN = "test-internal-token-0123456789abcdef";
    private static final String URL = "/v1/internal/pricing/estimate";
    private static final String BODY = """
        {"nodes": [{"id": "web", "label": "EC2 m7g.large", "icon": "ec2", "role": "web",
          "pricing": {"kind": "ec2", "spec": "m7g.large", "engine": "", "multi_az": false, "count": 4,
            "hours_per_month": 730, "storage_gb": 30, "data_gb_month": 0, "requests_million_month": 0,
            "write_requests_million_month": 0, "vcpu": 0, "memory_gb": 0, "duration_ms": 0, "lcu": 0}}]}
        """;

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ArchitectureCostEstimator architectureCostEstimator;

    @Test
    void 토큰이_맞으면_AI_서버_CostReport_모양으로_돌려준다() throws Exception {
        when(architectureCostEstimator.estimate(anyList())).thenAnswer(invocation -> {
            List<ArchitectureNode> nodes = invocation.getArgument(0);
            assertThat(nodes.getFirst().pricing().hoursPerMonth()).isEqualTo(730);
            assertThat(nodes.getFirst().pricing().storageGb()).isEqualTo(30);
            return new CostReport(new BigDecimal("303.82"),
                List.of(new CostReport.Item("web", "EC2 m7g.large", "ec2", new BigDecimal("303.82"), "식"),
                    new CostReport.Item("x", "X", "unpriced", null, "가격표에 없음 — 추정 불가")),
                List.of("X"), List.of("서울 리전(ap-northeast-2) 온디맨드 기준"));
        });

        mockMvc.perform(estimate(BODY).header(InternalTokenAuthorizationManager.HEADER, TOKEN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.monthly_total_usd").value(303.82))
            .andExpect(jsonPath("$.items[0].node_id").value("web"))
            .andExpect(jsonPath("$.items[0].monthly_usd").value(303.82))
            .andExpect(jsonPath("$.items[1].monthly_usd").isEmpty())
            .andExpect(jsonPath("$.unpriced[0]").value("X"))
            .andExpect(jsonPath("$.assumptions[0]").exists());
    }

    @Test
    void 토큰이_없거나_틀리면_401이다() throws Exception {
        mockMvc.perform(estimate(BODY))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("S003"));
        mockMvc.perform(estimate(BODY).header(InternalTokenAuthorizationManager.HEADER, TOKEN + "x"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("S003"));
        verifyNoInteractions(architectureCostEstimator);
    }

    @Test
    void 토큰이_비었거나_짧으면_같은_값을_보내도_막는다() {
        for (String token : List.of("", "   ", "short-placeholder")) {
            InternalTokenAuthorizationManager manager = new InternalTokenAuthorizationManager(token);
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader(InternalTokenAuthorizationManager.HEADER, token);

            assertThat(manager.authorize(() -> null, new RequestAuthorizationContext(request)).isGranted()).isFalse();
        }
    }

    @Test
    void 노드가_없거나_값이_범위를_넘으면_400이다() throws Exception {
        mockMvc.perform(estimate("{\"nodes\": []}").header(InternalTokenAuthorizationManager.HEADER, TOKEN))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("C001"));
        mockMvc.perform(estimate(BODY.replace("\"hours_per_month\": 730", "\"hours_per_month\": 745"))
                .header(InternalTokenAuthorizationManager.HEADER, TOKEN))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("nodes[0].pricing.hoursPerMonth"));
        mockMvc.perform(estimate(BODY.replace("m7g.large\"", "m7g.large'; x\""))
                .header(InternalTokenAuthorizationManager.HEADER, TOKEN))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("nodes[0].pricing.spec"));
        verifyNoInteractions(architectureCostEstimator);
    }

    private static MockHttpServletRequestBuilder estimate(String body) {
        return post(URL).contentType(MediaType.APPLICATION_JSON).content(body);
    }
}
