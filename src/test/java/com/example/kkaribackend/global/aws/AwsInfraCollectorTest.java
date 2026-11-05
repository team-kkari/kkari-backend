package com.example.kkaribackend.global.aws;

import com.example.kkaribackend.domain.analysis.domain.AnalysisStep;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot.Ec2Instance;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot.MetricStats;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot.Missing;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot.RdsInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.CloudWatchException;
import software.amazon.awssdk.services.cloudwatch.model.GetMetricDataRequest;
import software.amazon.awssdk.services.cloudwatch.model.GetMetricDataResponse;
import software.amazon.awssdk.services.cloudwatch.model.MetricDataQuery;
import software.amazon.awssdk.services.cloudwatch.model.MetricDataResult;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesResponse;
import software.amazon.awssdk.services.ec2.model.DescribeNatGatewaysRequest;
import software.amazon.awssdk.services.ec2.model.DescribeNatGatewaysResponse;
import software.amazon.awssdk.services.ec2.model.DescribeVolumesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeVolumesResponse;
import software.amazon.awssdk.services.ec2.model.Ec2Exception;
import software.amazon.awssdk.services.ec2.model.Instance;
import software.amazon.awssdk.services.ec2.model.InstanceState;
import software.amazon.awssdk.services.ec2.model.Placement;
import software.amazon.awssdk.services.ec2.model.Reservation;
import software.amazon.awssdk.services.elasticloadbalancingv2.ElasticLoadBalancingV2Client;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeLoadBalancersRequest;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeLoadBalancersResponse;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.ListFunctionsRequest;
import software.amazon.awssdk.services.lambda.model.ListFunctionsResponse;
import software.amazon.awssdk.services.rds.RdsClient;
import software.amazon.awssdk.services.rds.model.DBInstance;
import software.amazon.awssdk.services.rds.model.DescribeDbInstancesRequest;
import software.amazon.awssdk.services.rds.model.DescribeDbInstancesResponse;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Bucket;
import software.amazon.awssdk.services.s3.model.ListBucketsRequest;
import software.amazon.awssdk.services.s3.model.ListBucketsResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEFAULTS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AwsInfraCollectorTest {
    private Ec2Client ec2;
    private RdsClient rds;
    private S3Client s3;
    private LambdaClient lambda;
    private ElasticLoadBalancingV2Client elb;
    private CloudWatchClient cloudWatch;
    private AwsInfraCollector collector;

    @BeforeEach
    void setUp() {
        ec2 = client(Ec2Client.class);
        rds = client(RdsClient.class);
        s3 = client(S3Client.class);
        lambda = client(LambdaClient.class);
        elb = client(ElasticLoadBalancingV2Client.class);
        cloudWatch = client(CloudWatchClient.class);
        AwsInfraCollector.Clients clients = new AwsInfraCollector.Clients(ec2, rds, s3, lambda, elb, cloudWatch);
        // 가격 조회 미설정 상태
        AwsCostEstimator costEstimator = new AwsCostEstimator("ap-northeast-2", mock(AwsUnitPriceCatalog.class));
        collector = new AwsInfraCollector("ap-northeast-2", credentials -> clients, costEstimator);

        when(ec2.describeInstances(any(DescribeInstancesRequest.class))).thenReturn(DescribeInstancesResponse.builder()
            .reservations(Reservation.builder().instances(
                instance("i-1", "running"),
                instance("i-2", "terminated")).build())
            .build());
        when(ec2.describeVolumes(any(DescribeVolumesRequest.class))).thenReturn(DescribeVolumesResponse.builder().build());
        when(ec2.describeNatGateways(any(DescribeNatGatewaysRequest.class)))
            .thenReturn(DescribeNatGatewaysResponse.builder().build());
        when(rds.describeDBInstances(any(DescribeDbInstancesRequest.class))).thenReturn(DescribeDbInstancesResponse.builder()
            .dbInstances(DBInstance.builder().dbInstanceIdentifier("db-1").dbInstanceClass("db.t3.micro")
                .engine("mysql").multiAZ(true).allocatedStorage(20).dbInstanceStatus("available").build())
            .build());
        when(s3.listBuckets(any(ListBucketsRequest.class))).thenReturn(ListBucketsResponse.builder()
            .buckets(Bucket.builder().name("logs").build())
            .build());
        when(lambda.listFunctions(any(ListFunctionsRequest.class))).thenReturn(ListFunctionsResponse.builder().build());
        when(elb.describeLoadBalancers(any(DescribeLoadBalancersRequest.class)))
            .thenReturn(DescribeLoadBalancersResponse.builder().build());
        // 모든 지표에 1~20 을 돌려준다. S3 크기는 지표 없음
        when(cloudWatch.getMetricData(any(GetMetricDataRequest.class))).thenAnswer(invocation -> {
            List<MetricDataResult> results = new ArrayList<>();
            for (MetricDataQuery query : invocation.<GetMetricDataRequest>getArgument(0).metricDataQueries()) {
                boolean s3Size = "BucketSizeBytes".equals(query.metricStat().metric().metricName());
                results.add(MetricDataResult.builder().id(query.id())
                    .values(s3Size ? List.of() : IntStream.rangeClosed(1, 20).asDoubleStream().boxed().toList())
                    .build());
            }
            return GetMetricDataResponse.builder().metricDataResults(results).build();
        });
    }

    @Test
    void 리소스_구조와_사용률을_수집한다() {
        List<AnalysisStep> steps = new ArrayList<>();

        InfraSnapshot snapshot = collector.collect(null, steps::add);

        assertThat(steps).containsExactly(AnalysisStep.STRUCTURE, AnalysisStep.USAGE, AnalysisStep.COST);
        assertThat(snapshot.resources().ec2())
            .containsExactly(new Ec2Instance("i-1", "t3.micro", "Linux/UNIX", "running", "ap-northeast-2a"));
        assertThat(snapshot.resources().rds())
            .containsExactly(new RdsInstance("db-1", "db.t3.micro", "mysql", true, 20, "available"));
        assertThat(snapshot.resources().s3().getFirst().sizeBytes()).isNull();
        assertThat(snapshot.resources().lambda()).isEmpty();

        MetricStats expected = new MetricStats(10.5, 19.0, 20.0, 20);
        assertThat(snapshot.usage().byResource().get("EC2").get("i-1")).isEqualTo(Map.of("CPUUtilization", expected));
        assertThat(snapshot.usage().byResource().get("RDS").get("db-1"))
            .isEqualTo(Map.of("CPUUtilization", expected, "DatabaseConnections", expected));
    }

    @Test
    void 일부_서비스가_실패해도_나머지는_수집하고_missing에_남긴다() {
        when(ec2.describeInstances(any(DescribeInstancesRequest.class))).thenThrow(
            (Ec2Exception) Ec2Exception.builder().statusCode(403)
                .awsErrorDetails(AwsErrorDetails.builder().errorCode("UnauthorizedOperation").build())
                .build());
        when(cloudWatch.getMetricData(any(GetMetricDataRequest.class))).thenThrow(
            (CloudWatchException) CloudWatchException.builder().statusCode(400)
                .awsErrorDetails(AwsErrorDetails.builder().errorCode("Throttling").build())
                .build());

        InfraSnapshot snapshot = collector.collect(null, step -> {
        });

        assertThat(snapshot.resources().ec2()).isNull();
        assertThat(snapshot.resources().rds()).hasSize(1);
        assertThat(snapshot.usage().byResource()).doesNotContainKey("EC2");
        assertThat(snapshot.usage().byResource().get("RDS")).containsEntry("db-1", null);
        assertThat(snapshot.missing()).contains(
            new Missing("STRUCTURE EC2", "UnauthorizedOperation (HTTP 403)"),
            new Missing("USAGE RDS", "Throttling (HTTP 400)"),
            new Missing("USAGE S3", "Throttling (HTTP 400)"));
    }

    // 페이지네이터는 실제 구현을 써서 단건 호출 stub 으로 동작하게 한다
    private static <T> T client(Class<T> type) {
        return mock(type, invocation -> invocation.getMethod().getName().endsWith("Paginator")
            ? invocation.callRealMethod()
            : RETURNS_DEFAULTS.answer(invocation));
    }

    private static Instance instance(String id, String state) {
        return Instance.builder()
            .instanceId(id)
            .instanceType("t3.micro")
            .platformDetails("Linux/UNIX")
            .state(InstanceState.builder().name(state).build())
            .placement(Placement.builder().availabilityZone("ap-northeast-2a").build())
            .build();
    }
}
