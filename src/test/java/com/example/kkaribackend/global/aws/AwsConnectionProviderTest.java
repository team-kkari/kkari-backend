package com.example.kkaribackend.global.aws;

import com.example.kkaribackend.domain.project.domain.AwsConnectionStatus;
import com.example.kkaribackend.global.aws.exception.AwsErrorCode;
import com.example.kkaribackend.global.aws.exception.AwsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.DescribeRegionsResponse;
import software.amazon.awssdk.services.ec2.model.Ec2Exception;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;
import software.amazon.awssdk.services.sts.model.AssumeRoleResponse;
import software.amazon.awssdk.services.sts.model.Credentials;
import software.amazon.awssdk.services.sts.model.StsException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AwsConnectionProviderTest {
    private static final String ROLE_ARN = "arn:aws:iam::123456789012:role/kkari-readonly";
    private static final String EXTERNAL_ID = "ext-id";

    private StsClient stsClient;
    private Ec2Client ec2Client;
    private AwsConnectionProvider provider;

    @BeforeEach
    void setUp() {
        stsClient = mock(StsClient.class);
        ec2Client = mock(Ec2Client.class);
        provider = new AwsConnectionProvider(stsClient, credentials -> ec2Client);
    }

    @Test
    void AssumeRole과_읽기_권한이_모두_되면_CONNECTED() {
        givenAssumeRoleSucceeds();
        when(ec2Client.describeRegions()).thenReturn(DescribeRegionsResponse.builder().build());

        assertThat(provider.testConnection(ROLE_ARN, EXTERNAL_ID, "kkari-project-1"))
            .isEqualTo(AwsConnectionStatus.CONNECTED);

        ArgumentCaptor<AssumeRoleRequest> captor = ArgumentCaptor.forClass(AssumeRoleRequest.class);
        verify(stsClient).assumeRole(captor.capture());
        assertThat(captor.getValue().roleArn()).isEqualTo(ROLE_ARN);
        assertThat(captor.getValue().externalId()).isEqualTo(EXTERNAL_ID);
        assertThat(captor.getValue().durationSeconds()).isEqualTo(3600);
    }

    @Test
    void AssumeRole이_거부되면_TRUST_POLICY_ERROR() {
        when(stsClient.assumeRole(any(AssumeRoleRequest.class))).thenThrow(
            (StsException) StsException.builder().statusCode(403)
                .awsErrorDetails(AwsErrorDetails.builder().errorCode("AccessDenied").errorMessage("denied").build())
                .build());

        assertThat(provider.testConnection(ROLE_ARN, EXTERNAL_ID, "kkari-project-1"))
            .isEqualTo(AwsConnectionStatus.TRUST_POLICY_ERROR);
    }

    @Test
    void 읽기_권한이_없으면_PERMISSION_DENIED() {
        givenAssumeRoleSucceeds();
        when(ec2Client.describeRegions()).thenThrow(
            (Ec2Exception) Ec2Exception.builder().statusCode(403)
                .awsErrorDetails(AwsErrorDetails.builder().errorCode("UnauthorizedOperation").errorMessage("no").build())
                .build());

        assertThat(provider.testConnection(ROLE_ARN, EXTERNAL_ID, "kkari-project-1"))
            .isEqualTo(AwsConnectionStatus.PERMISSION_DENIED);
    }

    @Test
    void 서버_자격증명이_없으면_AWS_CREDENTIALS_UNAVAILABLE() {
        when(stsClient.assumeRole(any(AssumeRoleRequest.class)))
            .thenThrow(SdkClientException.create("Unable to load credentials"));

        assertThatThrownBy(() -> provider.testConnection(ROLE_ARN, EXTERNAL_ID, "kkari-project-1"))
            .isInstanceOf(AwsException.class)
            .extracting(e -> ((AwsException) e).getErrorCode())
            .isEqualTo(AwsErrorCode.AWS_CREDENTIALS_UNAVAILABLE);
    }

    private void givenAssumeRoleSucceeds() {
        when(stsClient.assumeRole(any(AssumeRoleRequest.class))).thenReturn(AssumeRoleResponse.builder()
            .credentials(Credentials.builder().accessKeyId("AKIA").secretAccessKey("secret").sessionToken("token").build())
            .build());
    }
}
