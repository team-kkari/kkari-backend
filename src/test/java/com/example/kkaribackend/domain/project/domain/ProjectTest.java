package com.example.kkaribackend.domain.project.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectTest {

    @Test
    void 진행_단계는_앞으로만_나아간다() {
        Project project = Project.builder().build();
        assertThat(project.getStage()).isEqualTo(ProjectStage.RECEIVED);

        project.markAnalyzed();
        assertThat(project.getStage()).isEqualTo(ProjectStage.ANALYZED);

        project.markRecommended();
        project.markAnalyzed();
        assertThat(project.getStage()).isEqualTo(ProjectStage.RECOMMENDED);
    }
}
