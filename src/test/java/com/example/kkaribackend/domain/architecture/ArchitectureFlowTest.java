package com.example.kkaribackend.domain.architecture;

import com.example.kkaribackend.domain.analysis.domain.AnalysisJob;
import com.example.kkaribackend.domain.analysis.domain.AnalysisStatus;
import com.example.kkaribackend.domain.analysis.domain.AnalysisJobType;
import com.example.kkaribackend.domain.analysis.domain.repository.AnalysisJobRepository;
import com.example.kkaribackend.domain.project.domain.Project;
import com.example.kkaribackend.domain.project.domain.ProjectStage;
import com.example.kkaribackend.domain.project.domain.Requirements;
import com.example.kkaribackend.domain.project.domain.repository.ProjectRepository;
import com.example.kkaribackend.domain.user.domain.Role;
import com.example.kkaribackend.domain.user.domain.User;
import com.example.kkaribackend.domain.user.domain.repository.UserRepository;
import com.example.kkaribackend.global.ai.AiServerClient;
import com.example.kkaribackend.global.ai.dto.ArchitectureRequest;
import com.example.kkaribackend.global.security.auth.CustomUserDetails;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 생성 요청 -> 비동기 실행 -> 결과 조회까지 실제 컨텍스트에서 확인한다. AI 서버만 가짜로 둔다
@SpringBootTest
@AutoConfigureMockMvc
class ArchitectureFlowTest {
    private static final String AI_RESPONSE = """
        {"overall":"요약","current":{"cost":{"monthly_total_usd":7.59},"nodes":[]},
         "architectures":[{"rank":1,"axis":"cost","name":"서버리스","scores":{"cost":9}}],
         "sources":[],"failed":[],"timings_s":{"total":95.2}}""";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private AnalysisJobRepository analysisJobRepository;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private AiServerClient aiServerClient;

    private User user;
    private User customer;
    private Project project;
    private RequestPostProcessor owner;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.builder().email("architecture@kkari.com").password("pw").name("엔지니어")
            .companyName("MSP").role(Role.MSP_ENGINEER).build());
        customer = userRepository.save(User.builder().email("customer@a.com").password("pw").name("고객")
            .companyName("고객사").role(Role.CUSTOMER).build());
        project = projectRepository.save(Project.builder().user(user).customer(customer)
            .companyName("고객사").contactName("담당").serviceName("쇼핑몰").build());
        owner = loginAs(user.getId());
    }

    @AfterEach
    void tearDown() {
        analysisJobRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void 생성을_시작하면_비동기로_실행되어_AI_응답을_그대로_조회한다() throws Exception {
        AnalysisJob analysis = saveSucceededAnalysis();
        when(aiServerClient.architectures(any())).thenReturn(objectMapper.readTree(AI_RESPONSE));

        String started = mockMvc.perform(post(url()).with(owner))
            .andExpect(status().isAccepted())
            .andReturn().getResponse().getContentAsString();
        long jobId = ((Number) JsonPath.read(started, "$.jobId")).longValue();

        String job = "";
        for (int i = 0; i < 100 && !job.contains("SUCCEEDED"); i++) {
            Thread.sleep(50);
            job = mockMvc.perform(get("/v1/jobs/" + jobId).with(owner))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        }
        assertThat((String) JsonPath.read(job, "$.status")).isEqualTo("SUCCEEDED");
        assertThat((String) JsonPath.read(job, "$.type")).isEqualTo("ARCHITECTURE");
        assertThat((Object) JsonPath.read(job, "$.step")).isNull();

        ArgumentCaptor<ArchitectureRequest> request = ArgumentCaptor.forClass(ArchitectureRequest.class);
        verify(aiServerClient, timeout(1000)).architectures(request.capture());
        assertThat(request.getValue().count()).isEqualTo(5);
        assertThat(request.getValue().infra()).isEqualTo("[AWS] 리전 ap-northeast-2\nEC2 i-1: t3.micro");

        String architecture = mockMvc.perform(get(url()).with(owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.jobId").value(jobId))
            .andExpect(jsonPath("$.generatedAt").isNotEmpty())
            .andExpect(jsonPath("$.latestJob.jobId").value(jobId))
            .andExpect(jsonPath("$.latestJob.status").value("SUCCEEDED"))
            .andReturn().getResponse().getContentAsString();
        assertThat(OffsetDateTime.parse(objectMapper.readTree(architecture).get("generatedAt").asString())).isNotNull();
        assertThat(objectMapper.readTree(architecture).get("result")).isEqualTo(objectMapper.readTree(AI_RESPONSE));
        assertThat(projectRepository.findById(project.getId()).orElseThrow().getStage()).isEqualTo(ProjectStage.RECOMMENDED);
        mockMvc.perform(get("/v1/customer/services/" + project.getId()).with(customerLogin(customer.getId())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.stage").value("RECOMMENDED"));
        mockMvc.perform(get("/v1/customer/services").with(customerLogin(customer.getId())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].stage").value("RECOMMENDED"));

        // 추천 아키텍처 작업은 분석 결과 조회에 섞이지 않는다
        mockMvc.perform(get("/v1/projects/" + project.getId() + "/discovery").with(owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.jobId").value(analysis.getId()))
            .andExpect(jsonPath("$.result.resources.ec2[0].instanceId").value("i-1"));
    }

    @Test
    void 성공한_분석이_없으면_409_결과가_없으면_404() throws Exception {
        mockMvc.perform(post(url()).with(owner))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("AR001"));
        mockMvc.perform(get(url()).with(owner))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("AR002"))
            .andExpect(jsonPath("$.latestJob").hasJsonPath())
            .andExpect(jsonPath("$.latestJob").isEmpty());
    }

    @Test
    void 신규_구축_프로젝트는_분석_없이_상담_내용으로_설계를_시작한다() throws Exception {
        Project fresh = projectRepository.save(Project.builder().user(user).companyName("신규").contactName("담당")
            .infraStatus(com.example.kkaribackend.domain.project.domain.InfraStatus.NONE).build());
        fresh.updateRequirements(Requirements.builder().content("워크로드: 쇼핑몰").build());
        projectRepository.saveAndFlush(fresh);
        when(aiServerClient.architectures(any())).thenReturn(objectMapper.readTree(AI_RESPONSE));

        mockMvc.perform(post("/v1/projects/" + fresh.getId() + "/architectures").with(owner))
            .andExpect(status().isAccepted());

        ArgumentCaptor<ArchitectureRequest> request = ArgumentCaptor.forClass(ArchitectureRequest.class);
        verify(aiServerClient, timeout(2000)).architectures(request.capture());
        assertThat(request.getValue().infra()).isEqualTo("[신규 구축] 기존 AWS 인프라 없음");
        assertThat(request.getValue().goal()).contains("워크로드: 쇼핑몰");
        assertThat(request.getValue().currentMonthlyUsd()).isNull();
    }

    @Test
    void 기존_미지원_통화는_작업을_만들거나_AI를_호출하기_전에_400으로_알린다() throws Exception {
        saveSucceededAnalysis();
        project.updateRequirements(Requirements.builder().content("기존 데이터").currency("JPY").build());
        projectRepository.saveAndFlush(project);
        long before = analysisJobRepository.count();
        mockMvc.perform(post(url()).with(owner)).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("AR003"));
        assertThat(analysisJobRepository.count()).isEqualTo(before);
        assertThat(projectRepository.findById(project.getId()).orElseThrow().getRequirements().currency()).isEqualTo("JPY");
        org.mockito.Mockito.verifyNoInteractions(aiServerClient);
    }

    @Test
    void 성공_결과가_없어도_대기_실행_실패한_최근_설계_작업을_404에서_복원한다() throws Exception {
        for (AnalysisStatus state : new AnalysisStatus[]{AnalysisStatus.QUEUED, AnalysisStatus.RUNNING, AnalysisStatus.FAILED}) {
            AnalysisJob job = AnalysisJob.builder().project(project).type(AnalysisJobType.ARCHITECTURE).build();
            if (state == AnalysisStatus.RUNNING) job.start();
            if (state == AnalysisStatus.FAILED) job.fail("AI 서버 응답 지연");
            analysisJobRepository.saveAndFlush(job);
            // 더 최신인 인프라 분석은 latestJob에 섞이지 않는다.
            saveSucceededAnalysis();
            mockMvc.perform(get(url()).with(owner)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AR002"))
                .andExpect(jsonPath("$.latestJob.jobId").value(job.getId()))
                .andExpect(jsonPath("$.latestJob.status").value(state.name()))
                .andExpect(jsonPath("$.latestJob.failureReason").value(job.getFailureReason()));
        }
    }

    @Test
    void 이후_작업이_실패해도_이전_성공_결과와_최근_실패_작업을_함께_돌려준다() throws Exception {
        AnalysisJob succeeded = AnalysisJob.builder().project(project).type(AnalysisJobType.ARCHITECTURE).build();
        succeeded.succeed(AI_RESPONSE);
        analysisJobRepository.saveAndFlush(succeeded);
        AnalysisJob failed = AnalysisJob.builder().project(project).type(AnalysisJobType.ARCHITECTURE).build();
        failed.fail("생성 실패");
        analysisJobRepository.saveAndFlush(failed);
        mockMvc.perform(get(url()).with(owner)).andExpect(status().isOk())
            .andExpect(jsonPath("$.jobId").value(succeeded.getId()))
            .andExpect(jsonPath("$.result.overall").value("요약"))
            .andExpect(jsonPath("$.latestJob.jobId").value(failed.getId()))
            .andExpect(jsonPath("$.latestJob.status").value("FAILED"))
            .andExpect(jsonPath("$.latestJob.failureReason").value("생성 실패"));
    }

    @Test
    void 다른_담당자와_없는_프로젝트에는_설계_작업을_노출하지_않는다() throws Exception {
        User other = userRepository.save(User.builder().email("other-query@a.com").password("pw").name("다른 담당자")
            .companyName("MSP").role(Role.MSP_ENGINEER).build());
        mockMvc.perform(get(url()).with(loginAs(other.getId()))).andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("P002"))
            .andExpect(jsonPath("$.latestJob").doesNotHaveJsonPath());
        mockMvc.perform(get("/v1/projects/9223372036854775807/architectures").with(owner)).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("P001"));
    }

    @Test
    void 추천_개수가_1에서_5를_벗어나면_400() throws Exception {
        saveSucceededAnalysis();
        for (String count : new String[]{"0", "6"}) {
            mockMvc.perform(post(url()).with(owner).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"count\":" + count + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
        }
        assertThat(analysisJobRepository.count()).isEqualTo(1);
    }

    @Test
    void 담당하지_않는_프로젝트는_생성할_수_없다() throws Exception {
        saveSucceededAnalysis();
        User other = userRepository.save(User.builder().email("other@kkari.com").password("pw").name("남")
            .companyName("MSP").role(Role.MSP_ENGINEER).build());

        mockMvc.perform(post(url()).with(loginAs(other.getId())))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("P002"));
    }

    @Test
    void 고객은_본인_서비스의_추천_결과만_엔지니어와_같은_응답으로_본다() throws Exception {
        User stranger = userRepository.save(User.builder().email("stranger@a.com").password("pw").name("남")
            .companyName("남의 회사").role(Role.CUSTOMER).build());
        String customerUrl = "/v1/customer/services/" + project.getId() + "/architectures";

        mockMvc.perform(get(customerUrl).with(customerLogin(customer.getId()))).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("AR002"))
            .andExpect(jsonPath("$.latestJob").hasJsonPath());

        AnalysisJob succeeded = AnalysisJob.builder().project(project).type(AnalysisJobType.ARCHITECTURE).build();
        succeeded.succeed(AI_RESPONSE);
        analysisJobRepository.saveAndFlush(succeeded);
        String engineerView = mockMvc.perform(get(url()).with(owner))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        mockMvc.perform(get(customerUrl).with(customerLogin(customer.getId()))).andExpect(status().isOk())
            .andExpect(result -> assertThat(objectMapper.readTree(result.getResponse().getContentAsString()))
                .isEqualTo(objectMapper.readTree(engineerView)));

        mockMvc.perform(get(customerUrl).with(customerLogin(stranger.getId()))).andExpect(status().isForbidden())
            .andExpect(jsonPath("$.latestJob").doesNotHaveJsonPath());
        mockMvc.perform(get(customerUrl).with(owner)).andExpect(status().isForbidden());
    }

    private RequestPostProcessor customerLogin(Long userId) {
        CustomUserDetails principal = new CustomUserDetails(userId, Role.CUSTOMER);
        return authentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }

    private AnalysisJob saveSucceededAnalysis() {
        AnalysisJob analysis = AnalysisJob.builder().project(project).build();
        analysis.succeed("""
            {"region":"ap-northeast-2","resources":{"ec2":[{"instanceId":"i-1","instanceType":"t3.micro"}]}}""");
        return analysisJobRepository.save(analysis);
    }

    private String url() {
        return "/v1/projects/" + project.getId() + "/architectures";
    }

    private RequestPostProcessor loginAs(Long userId) {
        CustomUserDetails principal = new CustomUserDetails(userId, Role.MSP_ENGINEER);
        return authentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }
}
