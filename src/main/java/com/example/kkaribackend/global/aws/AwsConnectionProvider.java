package com.example.kkaribackend.global.aws;

import com.example.kkaribackend.domain.project.domain.AwsConnectionStatus;
import com.example.kkaribackend.global.aws.exception.AwsErrorCode;
import com.example.kkaribackend.global.aws.exception.AwsException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.Ec2Exception;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;
import software.amazon.awssdk.services.sts.model.Credentials;
import software.amazon.awssdk.services.sts.model.StsException;

import java.util.function.Function;

/**
 * 고객사 Role 을 AssumeRole 해서 연결 상태를 판정한다.
 * Access Key 는 받지 않고, 1시간짜리 임시 자격증명만 쓰고 저장하지 않는다.
 */
@Slf4j
@Component
public class AwsConnectionProvider {
    private static final int SESSION_SECONDS = 3600;

    private final StsClient stsClient;
    private final Function<AwsCredentialsProvider, Ec2Client> ec2ClientFactory;
    private volatile String accountId;

    @Autowired
    public AwsConnectionProvider(StsClient stsClient, @Value("${aws.region}") String region) {
        this(stsClient, credentials -> Ec2Client.builder()
            .region(Region.of(region))
            .credentialsProvider(credentials)
            .build());
    }

    AwsConnectionProvider(StsClient stsClient, Function<AwsCredentialsProvider, Ec2Client> ec2ClientFactory) {
        this.stsClient = stsClient;
        this.ec2ClientFactory = ec2ClientFactory;
    }

    // 고객사가 신뢰 정책에 넣을 우리 AWS 계정 ID
    public String getAccountId() {
        if (accountId == null) {
            try {
                accountId = stsClient.getCallerIdentity().account();
            } catch (SdkClientException e) {
                log.error("서버 AWS 자격증명을 불러오지 못했습니다.", e);
                throw new AwsException(AwsErrorCode.AWS_CREDENTIALS_UNAVAILABLE);
            } catch (SdkException e) {
                log.error("GetCallerIdentity 실패", e);
                throw new AwsException(AwsErrorCode.AWS_REQUEST_FAILED);
            }
        }
        return accountId;
    }

    public AwsConnectionStatus testConnection(String roleArn, String externalId, String sessionName) {
        Credentials credentials;
        try {
            credentials = stsClient.assumeRole(AssumeRoleRequest.builder()
                .roleArn(roleArn)
                .externalId(externalId)
                .roleSessionName(sessionName)
                .durationSeconds(SESSION_SECONDS)
                .build()
            ).credentials();
        } catch (StsException e) {
            if (e.statusCode() == 403) {
                log.info("AssumeRole 거부: {} {}", roleArn, e.awsErrorDetails().errorMessage());
                return AwsConnectionStatus.TRUST_POLICY_ERROR;
            }
            log.error("AssumeRole 실패: {}", roleArn, e);
            throw new AwsException(AwsErrorCode.AWS_REQUEST_FAILED);
        } catch (SdkClientException e) {
            log.error("서버 AWS 자격증명을 불러오지 못했습니다.", e);
            throw new AwsException(AwsErrorCode.AWS_CREDENTIALS_UNAVAILABLE);
        }

        AwsCredentialsProvider customerCredentials = StaticCredentialsProvider.create(AwsSessionCredentials.create(
            credentials.accessKeyId(), credentials.secretAccessKey(), credentials.sessionToken()));

        // 읽기 권한 확인: ReadOnlyAccess 에 포함된 가벼운 호출
        try (Ec2Client ec2 = ec2ClientFactory.apply(customerCredentials)) {
            ec2.describeRegions();
            return AwsConnectionStatus.CONNECTED;
        } catch (Ec2Exception e) {
            if (e.statusCode() == 403 || "UnauthorizedOperation".equals(e.awsErrorDetails().errorCode())) {
                log.info("읽기 권한 부족: {} {}", roleArn, e.awsErrorDetails().errorMessage());
                return AwsConnectionStatus.PERMISSION_DENIED;
            }
            log.error("DescribeRegions 실패: {}", roleArn, e);
            throw new AwsException(AwsErrorCode.AWS_REQUEST_FAILED);
        } catch (SdkException e) {
            log.error("DescribeRegions 실패: {}", roleArn, e);
            throw new AwsException(AwsErrorCode.AWS_REQUEST_FAILED);
        }
    }
}
