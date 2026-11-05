package com.example.kkaribackend.domain.project;

import com.example.kkaribackend.domain.analysis.domain.AnalysisJob;
import com.example.kkaribackend.domain.analysis.domain.AnalysisJobType;
import com.example.kkaribackend.domain.analysis.domain.repository.AnalysisJobRepository;
import com.example.kkaribackend.domain.project.domain.Project;
import com.example.kkaribackend.domain.project.domain.repository.ProjectRepository;
import com.example.kkaribackend.domain.user.domain.Role;
import com.example.kkaribackend.domain.user.domain.User;
import com.example.kkaribackend.domain.user.domain.repository.UserRepository;
import com.example.kkaribackend.global.security.auth.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProjectPresentationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private AnalysisJobRepository analysisJobRepository;
    @Autowired private ObjectMapper objectMapper;
    private Project project;
    private User user;
    private RequestPostProcessor owner;

    @BeforeEach
    void setUp() {
        user = userRepository.saveAndFlush(User.builder().email("cost-list@a.com").password("pw").name("엔지니어")
            .companyName("MSP").role(Role.MSP_ENGINEER).build());
        project = saveProject("비용 있는 회사");
        var principal = new CustomUserDetails(user.getId(), Role.MSP_ENGINEER);
        owner = authentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }

    @Test
    void 목록의_비용은_최근_성공한_분석을_사용하며_미수집과_부분_누락은_null이다() throws Exception {
        saveAnalysis(project, "{\"EC2\":{\"i-1\":100}}");
        saveAnalysis(project, "{\"EC2\":{\"i-1\":7.592},\"EBS\":{\"vol-1\":8}}");
        AnalysisJob failed = AnalysisJob.builder().project(project).build();
        failed.fail("실패");
        analysisJobRepository.saveAndFlush(failed);
        AnalysisJob design = AnalysisJob.builder().project(project).type(AnalysisJobType.ARCHITECTURE).build();
        design.succeed("{\"current\":{\"cost\":{\"monthly_total_usd\":999}}}");
        analysisJobRepository.saveAndFlush(design);
        Project none = saveProject("미수집 회사");
        Project partial = saveProject("일부 누락 회사");
        saveAnalysis(partial, "{\"EC2\":{\"i-1\":10,\"i-2\":null}}");
        Project free = saveProject("0달러 회사");
        saveAnalysis(free, "{\"EC2\":{\"i-1\":0}}");

        JsonNode list = objectMapper.readTree(mockMvc.perform(get("/v1/projects").with(owner)).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString());
        assertThat(item(list, project.getId()).get("monthlyCost").decimalValue()).isEqualByComparingTo("15.592");
        assertThat(item(list, none.getId()).get("monthlyCost").isNull()).isTrue();
        assertThat(item(list, partial.getId()).get("monthlyCost").isNull()).isTrue();
        assertThat(item(list, free.getId()).get("monthlyCost").decimalValue()).isEqualByComparingTo("0");
        assertThat(OffsetDateTime.parse(item(list, project.getId()).get("updatedAt").asString()).toInstant())
            .isEqualTo(project.getUpdatedAt().atZone(ZoneId.systemDefault()).toInstant());
    }

    @Test
    void 목록은_고객_접수_여부와_서비스명과_생성_시각을_포함한다() throws Exception {
        User customer = userRepository.saveAndFlush(User.builder().email("submit-list@a.com").password("pw").name("고객")
            .companyName("고객사").role(Role.CUSTOMER).build());
        Project submitted = projectRepository.saveAndFlush(Project.builder().user(user).customer(customer)
            .serviceName("쇼핑몰").companyName("고객사").contactName("고객").build());

        JsonNode list = objectMapper.readTree(mockMvc.perform(get("/v1/projects").with(owner)).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString());
        assertThat(item(list, submitted.getId()).get("customerSubmitted").asBoolean()).isTrue();
        assertThat(item(list, submitted.getId()).get("serviceName").asString()).isEqualTo("쇼핑몰");
        assertThat(item(list, project.getId()).get("customerSubmitted").asBoolean()).isFalse();
        assertThat(item(list, project.getId()).get("serviceName").isNull()).isTrue();
        assertThat(OffsetDateTime.parse(item(list, project.getId()).get("createdAt").asString()).toInstant())
            .isEqualTo(project.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant());
    }

    @Test
    void 상세의_생성과_수정_시각은_offset을_포함한다() throws Exception {
        JsonNode detail = objectMapper.readTree(mockMvc.perform(get("/v1/projects/" + project.getId()).with(owner))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(OffsetDateTime.parse(detail.get("createdAt").asString()).toInstant())
            .isEqualTo(project.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant());
        assertThat(OffsetDateTime.parse(detail.get("updatedAt").asString()).toInstant())
            .isEqualTo(project.getUpdatedAt().atZone(ZoneId.systemDefault()).toInstant());
    }

    @Test
    void Swagger는_새_엔드포인트와_추가된_응답_필드를_노출한다() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/v1/users/me'].get").exists())
            .andExpect(jsonPath("$.components.schemas.ArchitectureResponse.properties.latestJob").exists())
            .andExpect(jsonPath("$.components.schemas.ArchitectureErrorResponseDto.properties.latestJob").exists())
            .andExpect(jsonPath("$.paths['/v1/projects/{projectId}/architectures'].get.responses['404'].content['application/json'].schema.anyOf.length()")
                .value(2))
            .andExpect(jsonPath("$.paths['/v1/projects/{projectId}/architectures'].get.responses['404'].content['application/json'].schema.oneOf")
                .doesNotHaveJsonPath())
            .andExpect(jsonPath("$.components.schemas.SaveRequirementsRequest.properties.currency.enum")
                .value(org.hamcrest.Matchers.contains("USD", "KRW")))
            .andExpect(jsonPath("$.components.schemas.ProjectDetailResponse.properties.terraformUploaded").exists())
            .andExpect(jsonPath("$.components.schemas.TerraformInfra.properties.fileName").exists())
            .andExpect(jsonPath("$.components.schemas.ProjectListResponse.properties.updatedAt.format").value("date-time"))
            .andExpect(jsonPath("$.components.schemas.SendChatMessageResponseDto.properties.missingFields.description")
                .value("profile과 동일한 camelCase 필드명"));
    }

    private Project saveProject(String companyName) {
        return projectRepository.saveAndFlush(Project.builder().user(user).companyName(companyName).contactName("담당").build());
    }

    private void saveAnalysis(Project target, String costs) {
        AnalysisJob job = AnalysisJob.builder().project(target).build();
        job.succeed("{\"cost\":{\"currency\":\"USD\",\"hoursPerMonth\":730,\"monthlyUsdByResource\":" + costs + "}}");
        analysisJobRepository.saveAndFlush(job);
    }

    private static JsonNode item(JsonNode list, long projectId) {
        for (JsonNode item : list) {
            if (item.get("projectId").asLong() == projectId) return item;
        }
        throw new AssertionError("목록에 프로젝트가 없음: " + projectId);
    }
}
