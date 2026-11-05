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
        JsonNode root = readJson(file);
        if (root == null || !root.isObject()) {
            throw new InvalidTerraformStateException("JSON 객체가 아닙니다.");
        }
        if (!Integer.valueOf(4).equals(integer(root, "version"))) {
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
                if (!a.isObject()) {
                    continue;
                }
                switch (type) {
                    // state 에는 platformDetails 가 없어 platform 은 null
                    case "aws_instance" -> {
                        ec2.add(new Ec2Instance(text(a, "id"), text(a, "instance_type"), null,
                            text(a, "instance_state"), text(a, "availability_zone")));
                        // 인스턴스에 붙은 볼륨도 AWS 수집처럼 EBS 로 본다
                        for (String device : List.of("root_block_device", "ebs_block_device")) {
                            for (JsonNode d : a.path(device)) {
                                addEbs(ebs, new EbsVolume(text(d, "volume_id"), text(d, "volume_type"),
                                    integer(d, "volume_size")));
                            }
                        }
                    }
                    case "aws_ebs_volume" -> addEbs(ebs, new EbsVolume(text(a, "id"), text(a, "type"), integer(a, "size")));
                    case "aws_db_instance" -> rds.add(new RdsInstance(text(a, "identifier"), text(a, "instance_class"),
                        text(a, "engine"), bool(a, "multi_az"), integer(a, "allocated_storage"), text(a, "status")));
                    // Aurora 인스턴스. AWS 수집(DescribeDBInstances)에도 RDS 로 나온다
                    case "aws_rds_cluster_instance" -> rds.add(new RdsInstance(text(a, "identifier"),
                        text(a, "instance_class"), text(a, "engine"), null, null, null));
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

    // aws_ebs_volume 으로 만든 볼륨이 인스턴스의 ebs_block_device 에도 나올 수 있어 ID 가 같으면 한 번만 넣는다
    private static void addEbs(List<EbsVolume> ebs, EbsVolume volume) {
        if (volume.volumeId() == null || ebs.stream().noneMatch(v -> volume.volumeId().equals(v.volumeId()))) {
            ebs.add(volume);
        }
    }

    private static String text(JsonNode attributes, String field) {
        JsonNode value = attributes.path(field);
        return value.isString() ? value.asString() : null;
    }

    private static Integer integer(JsonNode attributes, String field) {
        JsonNode value = attributes.path(field);
        // 범위를 넘는 값은 예외 대신 null
        return value.isIntegralNumber() && value.canConvertToInt() ? value.intValue() : null;
    }

    private static Boolean bool(JsonNode attributes, String field) {
        JsonNode value = attributes.path(field);
        return value.isBoolean() ? value.asBoolean() : null;
    }
}
