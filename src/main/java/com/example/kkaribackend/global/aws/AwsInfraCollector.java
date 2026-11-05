package com.example.kkaribackend.global.aws;

import com.example.kkaribackend.domain.analysis.domain.AnalysisStep;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot.Cost;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot.EbsVolume;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot.Ec2Instance;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot.LambdaFunction;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot.LoadBalancer;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot.MetricStats;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot.Missing;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot.NatGateway;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot.RdsInstance;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot.Resources;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot.S3Bucket;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot.Usage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.Dimension;
import software.amazon.awssdk.services.cloudwatch.model.GetMetricDataRequest;
import software.amazon.awssdk.services.cloudwatch.model.GetMetricDataResponse;
import software.amazon.awssdk.services.cloudwatch.model.Metric;
import software.amazon.awssdk.services.cloudwatch.model.MetricDataQuery;
import software.amazon.awssdk.services.cloudwatch.model.MetricDataResult;
import software.amazon.awssdk.services.cloudwatch.model.MetricStat;
import software.amazon.awssdk.services.cloudwatch.model.ScanBy;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeNatGatewaysRequest;
import software.amazon.awssdk.services.ec2.model.DescribeVolumesRequest;
import software.amazon.awssdk.services.elasticloadbalancingv2.ElasticLoadBalancingV2Client;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeLoadBalancersRequest;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.ListFunctionsRequest;
import software.amazon.awssdk.services.rds.RdsClient;
import software.amazon.awssdk.services.rds.model.DescribeDbInstancesRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Bucket;
import software.amazon.awssdk.services.s3.model.ListBucketsRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 고객사 Role 임시 자격증명으로 한 리전의 리소스 구조, 사용률, 예상 비용을 수집한다.
 * 한 서비스·지표·가격이 실패해도 나머지는 계속 수집하고, 실패는 missing 에 남긴다.
 */
@Slf4j
@Component
public class AwsInfraCollector {
    private static final int USAGE_DAYS = 14;
    private static final int USAGE_PERIOD_SECONDS = 3600;
    private static final int S3_SIZE_PERIOD_SECONDS = 86400;
    private static final int MAX_QUERIES_PER_REQUEST = 500; // GetMetricData 한도
    private static final Set<String> GONE_EC2_STATES = Set.of("shutting-down", "terminated");
    private static final Set<String> GONE_NAT_STATES = Set.of("deleting", "deleted", "failed");

    private final String region;
    private final Function<AwsCredentialsProvider, Clients> clientsFactory;
    private final AwsCostEstimator costEstimator;

    @Autowired
    public AwsInfraCollector(@Value("${aws.region}") String region, AwsCostEstimator costEstimator) {
        this(region, credentials -> Clients.create(Region.of(region), credentials), costEstimator);
    }

    AwsInfraCollector(String region, Function<AwsCredentialsProvider, Clients> clientsFactory,
                      AwsCostEstimator costEstimator) {
        this.region = region;
        this.clientsFactory = clientsFactory;
        this.costEstimator = costEstimator;
    }

    public InfraSnapshot collect(AwsCredentialsProvider credentials, Consumer<AnalysisStep> onStep) {
        List<Missing> missing = new ArrayList<>();
        try (Clients clients = clientsFactory.apply(credentials)) {
            onStep.accept(AnalysisStep.STRUCTURE);
            Resources resources = collectResources(clients, missing);

            onStep.accept(AnalysisStep.USAGE);
            Map<String, Map<String, Map<String, MetricStats>>> usage = new LinkedHashMap<>();
            if (resources.ec2() != null) {
                usage.put("EC2", collectUsage(clients.cloudWatch(), "EC2", "AWS/EC2", "InstanceId",
                    resources.ec2().stream().map(Ec2Instance::instanceId).toList(),
                    List.of("CPUUtilization"), missing));
            }
            if (resources.rds() != null) {
                usage.put("RDS", collectUsage(clients.cloudWatch(), "RDS", "AWS/RDS", "DBInstanceIdentifier",
                    resources.rds().stream().map(RdsInstance::dbInstanceId).toList(),
                    List.of("CPUUtilization", "DatabaseConnections"), missing));
            }

            onStep.accept(AnalysisStep.COST);
            Cost cost = costEstimator.estimate(resources, missing);

            return InfraSnapshot.builder()
                .source("AWS")
                .region(region)
                .collectedAt(Instant.now())
                .resources(resources)
                .usage(new Usage(USAGE_DAYS, USAGE_PERIOD_SECONDS, "Average", usage))
                .cost(cost)
                .missing(missing)
                .build();
        }
    }

    private Resources collectResources(Clients clients, List<Missing> missing) {
        Ec2Client ec2 = clients.ec2();
        return Resources.builder()
            .ec2(attempt("STRUCTURE EC2", missing, () -> ec2
                .describeInstancesPaginator(DescribeInstancesRequest.builder().build())
                .reservations().stream()
                .flatMap(reservation -> reservation.instances().stream())
                .filter(i -> !GONE_EC2_STATES.contains(i.state().nameAsString()))
                .map(i -> new Ec2Instance(i.instanceId(), i.instanceTypeAsString(), i.platformDetails(),
                    i.state().nameAsString(), i.placement().availabilityZone()))
                .toList()))
            .ebs(attempt("STRUCTURE EBS", missing, () -> ec2
                .describeVolumesPaginator(DescribeVolumesRequest.builder().build())
                .volumes().stream()
                .map(v -> new EbsVolume(v.volumeId(), v.volumeTypeAsString(), v.size()))
                .toList()))
            .rds(attempt("STRUCTURE RDS", missing, () -> clients.rds()
                .describeDBInstancesPaginator(DescribeDbInstancesRequest.builder().build())
                .dbInstances().stream()
                .map(db -> new RdsInstance(db.dbInstanceIdentifier(), db.dbInstanceClass(), db.engine(), db.multiAZ(),
                    db.allocatedStorage(), db.dbInstanceStatus()))
                .toList()))
            .s3(collectBuckets(clients, missing))
            .lambda(attempt("STRUCTURE LAMBDA", missing, () -> clients.lambda()
                .listFunctionsPaginator(ListFunctionsRequest.builder().build())
                .functions().stream()
                .map(f -> new LambdaFunction(f.functionName(), f.runtimeAsString(), f.memorySize()))
                .toList()))
            .loadBalancers(attempt("STRUCTURE ELB", missing, () -> clients.elb()
                .describeLoadBalancersPaginator(DescribeLoadBalancersRequest.builder().build())
                .loadBalancers().stream()
                .map(lb -> new LoadBalancer(lb.loadBalancerName(), lb.typeAsString()))
                .toList()))
            .natGateways(attempt("STRUCTURE NAT", missing, () -> ec2
                .describeNatGatewaysPaginator(DescribeNatGatewaysRequest.builder().build())
                .natGateways().stream()
                .filter(nat -> !GONE_NAT_STATES.contains(nat.stateAsString()))
                .map(nat -> new NatGateway(nat.natGatewayId(), nat.stateAsString()))
                .toList()))
            .build();
    }

    // 버킷 크기는 CloudWatch 일 단위 지표라 최근 3일 중 가장 최근 값을 쓴다. 지표 조회 실패는 USAGE S3 로 남긴다
    private List<S3Bucket> collectBuckets(Clients clients, List<Missing> missing) {
        List<String> names = attempt("STRUCTURE S3", missing, () -> clients.s3()
            .listBucketsPaginator(ListBucketsRequest.builder().bucketRegion(region).build())
            .buckets().stream()
            .map(Bucket::name)
            .toList());
        if (names == null) {
            return null;
        }

        List<MetricDataQuery> queries = new ArrayList<>();
        for (String name : names) {
            queries.add(query(queries.size(), S3_SIZE_PERIOD_SECONDS, "AWS/S3", "BucketSizeBytes",
                dimension("BucketName", name), dimension("StorageType", "StandardStorage")));
        }
        Map<String, List<Double>> values = attempt("USAGE S3", missing, () -> fetchMetrics(
            clients.cloudWatch(), queries, Duration.ofDays(3)));

        List<S3Bucket> buckets = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            List<Double> sizes = values == null ? List.of() : values.getOrDefault(queryId(i), List.of());
            buckets.add(new S3Bucket(names.get(i), sizes.isEmpty() ? null : sizes.getFirst().longValue()));
        }
        return buckets;
    }

    // 리소스 ID -> 지표 이름 -> 통계. 조회에 실패하면 리소스별 값이 null
    private Map<String, Map<String, MetricStats>> collectUsage(CloudWatchClient cloudWatch, String type,
                                                               String namespace, String dimensionName,
                                                               List<String> resourceIds, List<String> metricNames,
                                                               List<Missing> missing) {
        List<MetricDataQuery> queries = new ArrayList<>();
        for (String resourceId : resourceIds) {
            for (String metricName : metricNames) {
                queries.add(query(queries.size(), USAGE_PERIOD_SECONDS, namespace, metricName,
                    dimension(dimensionName, resourceId)));
            }
        }
        Map<String, List<Double>> values = attempt("USAGE " + type, missing, () -> fetchMetrics(
            cloudWatch, queries, Duration.ofDays(USAGE_DAYS)));

        Map<String, Map<String, MetricStats>> usage = new LinkedHashMap<>();
        if (values == null) {
            resourceIds.forEach(resourceId -> usage.put(resourceId, null));
            return usage;
        }

        int index = 0;
        for (String resourceId : resourceIds) {
            Map<String, MetricStats> metrics = new LinkedHashMap<>();
            for (String metricName : metricNames) {
                metrics.put(metricName, stats(values.getOrDefault(queryId(index++), List.of())));
            }
            usage.put(resourceId, metrics);
        }
        return usage;
    }

    // 쿼리 ID(m0, m1, ... 쿼리 순서) -> 값 목록(최신순)
    private static Map<String, List<Double>> fetchMetrics(CloudWatchClient cloudWatch, List<MetricDataQuery> queries,
                                                          Duration range) {
        Instant end = Instant.now();
        Map<String, List<Double>> values = new HashMap<>();
        for (int from = 0; from < queries.size(); from += MAX_QUERIES_PER_REQUEST) {
            GetMetricDataRequest request = GetMetricDataRequest.builder()
                .metricDataQueries(queries.subList(from, Math.min(from + MAX_QUERIES_PER_REQUEST, queries.size())))
                .startTime(end.minus(range))
                .endTime(end)
                .scanBy(ScanBy.TIMESTAMP_DESCENDING)
                .build();
            for (GetMetricDataResponse page : cloudWatch.getMetricDataPaginator(request)) {
                for (MetricDataResult result : page.metricDataResults()) {
                    values.computeIfAbsent(result.id(), id -> new ArrayList<>()).addAll(result.values());
                }
            }
        }
        return values;
    }

    private static MetricDataQuery query(int index, int periodSeconds, String namespace, String metricName,
                                         Dimension... dimensions) {
        return MetricDataQuery.builder()
            .id(queryId(index))
            .metricStat(MetricStat.builder()
                .metric(Metric.builder().namespace(namespace).metricName(metricName).dimensions(dimensions).build())
                .period(periodSeconds)
                .stat("Average")
                .build())
            .returnData(true)
            .build();
    }

    private static Dimension dimension(String name, String value) {
        return Dimension.builder().name(name).value(value).build();
    }

    private static String queryId(int index) {
        return "m" + index;
    }

    // p95 는 1시간 평균값들에 대한 nearest-rank 백분위수
    static MetricStats stats(List<Double> values) {
        if (values.isEmpty()) {
            return new MetricStats(null, null, null, 0);
        }
        List<Double> sorted = values.stream().sorted().toList();
        double average = sorted.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
        double p95 = sorted.get((int) Math.ceil(0.95 * sorted.size()) - 1);
        return new MetricStats(round(average), round(p95), round(sorted.getLast()), sorted.size());
    }

    private static double round(double value) {
        return Math.round(value * 100) / 100.0;
    }

    private static <T> T attempt(String target, List<Missing> missing, Supplier<T> call) {
        try {
            return call.get();
        } catch (RuntimeException e) {
            String reason = AwsFailures.reason(e);
            log.warn("인프라 수집 일부 실패: {} {}", target, reason);
            missing.add(new Missing(target, reason));
            return null;
        }
    }

    record Clients(Ec2Client ec2, RdsClient rds, S3Client s3, LambdaClient lambda, ElasticLoadBalancingV2Client elb,
                   CloudWatchClient cloudWatch) implements AutoCloseable {

        static Clients create(Region region, AwsCredentialsProvider credentials) {
            return new Clients(
                Ec2Client.builder().region(region).credentialsProvider(credentials).build(),
                RdsClient.builder().region(region).credentialsProvider(credentials).build(),
                S3Client.builder().region(region).credentialsProvider(credentials).build(),
                LambdaClient.builder().region(region).credentialsProvider(credentials).build(),
                ElasticLoadBalancingV2Client.builder().region(region).credentialsProvider(credentials).build(),
                CloudWatchClient.builder().region(region).credentialsProvider(credentials).build());
        }

        @Override
        public void close() {
            ec2.close();
            rds.close();
            s3.close();
            lambda.close();
            elb.close();
            cloudWatch.close();
        }
    }
}
