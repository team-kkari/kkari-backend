package com.example.kkaribackend.global.terraform;

import com.example.kkaribackend.global.aws.dto.InfraSnapshot.Ec2Instance;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot.Missing;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot.RdsInstance;
import com.example.kkaribackend.global.terraform.dto.TerraformInfra;
import com.example.kkaribackend.global.terraform.exception.InvalidTerraformStateException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// STATE 는 TerraformControllerTest 에서도 쓴다
public class TerraformStateParserTest {
    public static final String SECRET = "super-secret-pw-1234";
    public static final String STATE = """
        {
          "version": 4,
          "terraform_version": "1.9.0",
          "resources": [
            {"mode": "managed", "type": "aws_instance", "name": "web", "instances": [{"attributes": {
              "id": "i-0abc", "instance_type": "t3.micro", "instance_state": "running",
              "availability_zone": "ap-northeast-2a", "user_data": "%1$s"}}]},
            {"mode": "managed", "type": "aws_db_instance", "name": "db", "instances": [{"attributes": {
              "identifier": "prod-db", "instance_class": "db.t3.micro", "engine": "mysql", "multi_az": false,
              "allocated_storage": 20, "status": "available", "username": "admin", "password": "%1$s"}}]},
            {"mode": "managed", "type": "aws_iam_role", "name": "role", "instances": [{"attributes": {
              "name": "app-role", "assume_role_policy": "%1$s"}}]},
            {"mode": "data", "type": "aws_ami", "name": "ubuntu", "instances": [{"attributes": {"id": "ami-1"}}]}
          ]
        }
        """.formatted(SECRET);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TerraformStateParser parser = new TerraformStateParser(objectMapper);

    @Test
    void 지원하는_리소스만_필요한_속성으로_읽고_나머지는_missing에_남긴다() {
        TerraformInfra terraform = parser.parse(file("terraform.tfstate", STATE));

        assertThat(terraform.resources().ec2()).containsExactly(
            new Ec2Instance("i-0abc", "t3.micro", null, "running", "ap-northeast-2a"));
        assertThat(terraform.resources().rds()).containsExactly(
            new RdsInstance("prod-db", "db.t3.micro", "mysql", false, 20, "available"));
        assertThat(terraform.resources().s3()).isEmpty();
        assertThat(terraform.missing()).extracting(Missing::target).containsExactly("STRUCTURE TERRAFORM");
        assertThat(terraform.missing().getFirst().reason()).contains("1개", "aws_iam_role").doesNotContain("aws_ami");
        assertThat(objectMapper.writeValueAsString(terraform)).doesNotContain(SECRET, "admin");
    }

    @Test
    void JSON이_아니면_거부한다() {
        assertThatThrownBy(() -> parser.parse(file("terraform.tfstate", "not json {")))
            .isInstanceOf(InvalidTerraformStateException.class)
            .hasMessageContaining("JSON 으로 읽을 수 없습니다");
    }

    @Test
    void 버전_4가_아니면_거부한다() {
        assertThatThrownBy(() -> parser.parse(file("terraform.tfstate", "{\"version\":3,\"modules\":[]}")))
            .isInstanceOf(InvalidTerraformStateException.class)
            .hasMessageContaining("버전 4");
    }

    @Test
    void tfstate_확장자가_아니면_거부한다() {
        assertThatThrownBy(() -> parser.parse(file("main.tf", STATE)))
            .isInstanceOf(InvalidTerraformStateException.class)
            .hasMessageContaining(".tfstate");
    }

    @Test
    void 빈_파일은_거부한다() {
        assertThatThrownBy(() -> parser.parse(file("terraform.tfstate", "")))
            .isInstanceOf(InvalidTerraformStateException.class);
    }

    private static MockMultipartFile file(String filename, String content) {
        return new MockMultipartFile("file", filename, "application/json", content.getBytes(StandardCharsets.UTF_8));
    }
}
