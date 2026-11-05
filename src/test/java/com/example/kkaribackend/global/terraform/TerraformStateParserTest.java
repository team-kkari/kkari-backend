package com.example.kkaribackend.global.terraform;

import com.example.kkaribackend.global.aws.dto.InfraSnapshot.EbsVolume;
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
        TerraformInfra terraform = parser.parse(file(STATE));

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
        assertThatThrownBy(() -> parser.parse(file("not json {")))
            .isInstanceOf(InvalidTerraformStateException.class)
            .hasMessageContaining("JSON 으로 읽을 수 없습니다");
    }

    @Test
    void 버전_4가_아니면_거부한다() {
        assertThatThrownBy(() -> parser.parse(file("{\"version\":3,\"modules\":[]}")))
            .isInstanceOf(InvalidTerraformStateException.class)
            .hasMessageContaining("버전 4");
    }

    @Test
    void 인스턴스_볼륨과_Aurora_인스턴스도_AWS_수집과_같은_모양으로_읽는다() {
        TerraformInfra terraform = parser.parse(file("""
            {"version": 4, "resources": [
              {"mode": "managed", "type": "aws_instance", "name": "web", "instances": [{"attributes": {
                "id": "i-1", "instance_type": "t3.micro", "instance_state": "running",
                "root_block_device": [{"volume_id": "vol-root", "volume_type": "gp3", "volume_size": 8,
                  "kms_key_id": "%1$s"}],
                "ebs_block_device": [{"volume_id": "vol-data", "volume_type": "gp3", "volume_size": 100}]}}]},
              {"mode": "managed", "type": "aws_ebs_volume", "name": "data", "instances": [
                {"attributes": {"id": "vol-data", "type": "gp3", "size": 100}},
                {"attributes": {"id": "vol-huge", "type": "gp3", "size": 99999999999}}]},
              {"mode": "managed", "type": "aws_rds_cluster_instance", "name": "aurora", "instances": [{"attributes": {
                "identifier": "aurora-1", "instance_class": "db.r6g.large", "engine": "aurora-mysql"}}]},
              {"mode": "managed", "type": "aws_s3_bucket", "name": "broken", "instances": [{"attributes": null}, {}]}
            ]}
            """.formatted(SECRET)));

        assertThat(terraform.resources().ebs()).containsExactly(
            new EbsVolume("vol-root", "gp3", 8),
            new EbsVolume("vol-data", "gp3", 100),
            new EbsVolume("vol-huge", "gp3", null));
        assertThat(terraform.resources().rds()).containsExactly(
            new RdsInstance("aurora-1", "db.r6g.large", "aurora-mysql", null, null, null));
        assertThat(terraform.resources().s3()).isEmpty();
        assertThat(objectMapper.writeValueAsString(terraform)).doesNotContain(SECRET);
    }

    @Test
    void 빈_파일은_거부한다() {
        assertThatThrownBy(() -> parser.parse(file("")))
            .isInstanceOf(InvalidTerraformStateException.class);
    }

    private static MockMultipartFile file(String content) {
        return new MockMultipartFile("file", "terraform.tfstate", "application/json",
            content.getBytes(StandardCharsets.UTF_8));
    }
}
