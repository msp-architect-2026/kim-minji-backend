package com.minjikim.codecobainbackend.util;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.minjikim.codecobainbackend.exception.FileProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Component
public class MinioUploader {

    private static final Logger log = LoggerFactory.getLogger(MinioUploader.class);

    @Value("${minio.endpoint}")
    private String endpoint;

    @Value("${minio.access-key}")
    private String accessKey;

    @Value("${minio.secret-key}")
    private String secretKey;

    @Value("${minio.bucket}")
    private String bucket;

    private AmazonS3 getS3Client() {
        BasicAWSCredentials credentials = new BasicAWSCredentials(accessKey, secretKey);
        return AmazonS3ClientBuilder.standard()
                .withEndpointConfiguration(
                        new AwsClientBuilder.EndpointConfiguration(endpoint, "us-east-1")
                )
                .withCredentials(new AWSStaticCredentialsProvider(credentials))
                .withPathStyleAccessEnabled(true)
                .build();
    }

    public String upload(MultipartFile file, String dirName) {
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String storedFilename = UUID.randomUUID() + "_" + file.getOriginalFilename();
        String key = dirName + "/" + datePath + "/" + storedFilename;

        try {
            log.info("MinIO 업로드 시작: 원본 파일명 = {}", file.getOriginalFilename());
            log.info("MinIO 저장 경로 = {}", key);

            AmazonS3 s3 = getS3Client();

            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType(file.getContentType());
            metadata.setContentLength(file.getSize());

            s3.putObject(bucket, key, file.getInputStream(), metadata);

            log.info("MinIO 업로드 성공: bucket={}, key={}", bucket, key);

            // object_key만 반환 (AI 서버에 bucket + key로 넘길 거라서)
            return key;

        } catch (IOException e) {
            throw new FileProcessingException("MinIO 업로드 실패", e);
        }
    }
}