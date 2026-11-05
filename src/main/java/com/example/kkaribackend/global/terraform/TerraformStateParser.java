package com.example.kkaribackend.global.terraform;

import com.example.kkaribackend.global.aws.dto.InfraSnapshot.EbsVolume;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot.Ec2Instance;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot.LambdaFunction;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot.LoadBalancer;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot.Missing;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot.NatGateway;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot.RdsInstance;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot.Resources;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot.S3Bucket;
import com.example.kkaribackend.global.terraform.dto.TerraformInfra;
import com.example.kkaribackend.global.terraform.exception.InvalidTerraformStateException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Terraform state(v4) 파일을 읽어 리소스 구조를 만든다. 파일은 JSON 으로 읽기만 하고 실행하지 않는다.
 * state 에는 비밀번호 같은 민감한 값이 들어 있을 수 있어 리소스별로 필요한 속성만 골라 읽는다.
 * ID·단위는 AwsInfraCollector 와 같다(EBS·RDS 용량 GiB, Lambda 메모리 MB).
 */
@Component
@RequiredArgsConstructor
public class TerraformStateParser {
    private final ObjectMapper objectMapper;

    public TerraformInfra parse(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".tfstate")) {
            throw new InvalidTerraformStateException("확장자가 .tfstate 인 파일만 업로드할 수 있습니다.");
        }
        JsonNode root = readJson(file);
        if (root == null || !root.isObject()) {
            throw new InvalidTerraformStateException("JSON 객체가 아닙니다.");
        }
        if (root.path("version").asInt(0) != 4) {
            throw new InvalidTerraformStateException("state 형식 버전 4만 지원합니다.");
        }
        if (!root.path("resources").isArray()) {
            throw new InvalidTerraformStateException("resources 배열이 없습니다.");
        }

        List<Missing> missing = new ArrayList<>();
        Resources resources = readResources(root.path("resources"), missing);
        return new TerraformInfra(resources, missing, Instant.now());
    }

    private JsonNode readJson(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return objectMapper.readTree(in);
        } catch (JacksonException e) {
            throw new InvalidTerraformStateException("JSON 으로 읽을 수 없습니다.");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Resources readResources(JsonNode stateResources, List<Missing> missing) {
        List<Ec2Instance> ec2 = new ArrayList<>();
        List<EbsVolume> ebs = new ArrayList<>();
        List<RdsInstance> rds = new ArrayList<>();
        List<S3Bucket> s3 = new ArrayList<>();
        List<LambdaFunction> lambda = new ArrayList<>();
        List<LoadBalancer> loadBalancers = new ArrayList<>();
        List<NatGateway> natGateways = new ArrayList<>();
        Set<String> unsupportedTypes = new TreeSet<>();
        int unsupportedCount = 0;

        for (JsonNode resource : stateResources) {
            // data 소스는 조회용이라 실제 리소스가 아니다
            if (!"managed".equals(resource.path("mode").asString(""))) {
                continue;
            }
            String type = resource.path("type").asString("");
            for (JsonNode instance : resource.path("instances")) {
                JsonNode a = instance.path("attributes");
                switch (type) {
                    // state 에는 platformDetails 가 없어 platform 은 null
                    case "aws_instance" -> ec2.add(new Ec2Instance(text(a, "id"), text(a, "instance_type"), null,
                        text(a, "instance_state"), text(a, "availability_zone")));
                    case "aws_ebs_volume" -> ebs.add(new EbsVolume(text(a, "id"), text(a, "type"), integer(a, "size")));
                    case "aws_db_instance" -> rds.add(new RdsInstance(text(a, "identifier"), text(a, "instance_class"),
                        text(a, "engine"), bool(a, "multi_az"), integer(a, "allocated_storage"), text(a, "status")));
                    case "aws_s3_bucket" -> s3.add(new S3Bucket(text(a, "bucket"), null));
                    case "aws_lambda_function" -> lambda.add(new LambdaFunction(text(a, "function_name"),
                        text(a, "runtime"), integer(a, "memory_size")));
                    case "aws_lb", "aws_alb" -> loadBalancers.add(new LoadBalancer(text(a, "name"),
                        text(a, "load_balancer_type")));
                    case "aws_nat_gateway" -> natGateways.add(new NatGateway(text(a, "id"), null));
                    default -> {
                        unsupportedTypes.add(type);
                        unsupportedCount++;
                    }
                }
            }
        }

        if (unsupportedCount > 0) {
            missing.add(new Missing("STRUCTURE TERRAFORM", "지원하지 않는 리소스 " + unsupportedCount
                + "개는 제외했습니다: " + String.join(", ", unsupportedTypes)));
        }
        return new Resources(ec2, ebs, rds, s3, lambda, loadBalancers, natGateways);
    }

    private static String text(JsonNode attributes, String field) {
        JsonNode value = attributes.path(field);
        return value.isString() ? value.asString() : null;
    }

    private static Integer integer(JsonNode attributes, String field) {
        JsonNode value = attributes.path(field);
        return value.isIntegralNumber() ? value.asInt() : null;
    }

    private static Boolean bool(JsonNode attributes, String field) {
        JsonNode value = attributes.path(field);
        return value.isBoolean() ? value.asBoolean() : null;
    }
}
