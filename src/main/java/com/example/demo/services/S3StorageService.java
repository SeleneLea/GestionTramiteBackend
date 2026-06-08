package com.example.demo.services;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.InputStream;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;

@Service
@Slf4j
public class S3StorageService {

    @Autowired
    private ObjectProvider<S3Client> s3ClientProvider;

    @Autowired
    private ObjectProvider<S3Presigner> presignerProvider;

    @Value("${aws.s3.bucket:}")
    private String bucket;

    @Value("${aws.s3.presigned-ttl-seconds:300}")
    private long presignedTtlSeconds;

    @Value("${aws.enabled:false}")
    private boolean awsEnabled;

    @PostConstruct
    void verificar() {
        if (!awsEnabled) {
            log.warn("[S3] aws.enabled=false → las operaciones de S3 lanzarán IllegalStateException");
            return;
        }
        if (bucket == null || bucket.isBlank()) {
            log.warn("[S3] aws.s3.bucket vacío; revisa application.yml o AWS_S3_BUCKET");
        } else {
            log.info("[S3] habilitado · bucket={} · ttl-presigned={}s", bucket, presignedTtlSeconds);
        }
    }

    public void upload(String key, InputStream input, String contentType, long size) {
        S3Client cli = clienteRequerido();
        PutObjectRequest req = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType != null ? contentType : "application/octet-stream")
                .contentLength(size)
                .build();
        cli.putObject(req, RequestBody.fromInputStream(input, size));
    }

    public URL presignedGet(String key) {
        return presignedGet(key, Duration.ofSeconds(presignedTtlSeconds));
    }

    public URL presignedGet(String key, Duration ttl) {
        S3Presigner pre = presignerRequerido();
        GetObjectRequest get = GetObjectRequest.builder()
                .bucket(bucket).key(key).build();
        GetObjectPresignRequest req = GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(get)
                .build();
        return pre.presignGetObject(req).url();
    }

    public Instant calcularExpiracion() {
        return Instant.now().plusSeconds(presignedTtlSeconds);
    }

    public byte[] download(String key) {
        S3Client cli = clienteRequerido();
        return cli.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build()).asByteArray();
    }

    public String contentType(String key) {
        S3Client cli = clienteRequerido();
        String ct = cli.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build()).contentType();
        return ct != null ? ct : "application/octet-stream";
    }

    public boolean exists(String key) {
        S3Client cli = clienteRequerido();
        try {
            cli.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }

    public void delete(String key) {
        S3Client cli = clienteRequerido();
        cli.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }

    public String bucket() {
        return bucket;
    }

    public boolean enabled() {
        return awsEnabled;
    }

    private S3Client clienteRequerido() {
        S3Client cli = s3ClientProvider.getIfAvailable();
        if (cli == null) {
            throw new IllegalStateException(
                    "S3 deshabilitado o sin credenciales. Pon aws.enabled=true y configura las claves.");
        }
        return cli;
    }

    private S3Presigner presignerRequerido() {
        S3Presigner pre = presignerProvider.getIfAvailable();
        if (pre == null) {
            throw new IllegalStateException(
                    "S3 deshabilitado o sin credenciales. Pon aws.enabled=true y configura las claves.");
        }
        return pre;
    }
}
