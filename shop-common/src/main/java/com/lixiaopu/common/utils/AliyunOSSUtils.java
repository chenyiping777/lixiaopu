package com.lixiaopu.common.utils;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.PutObjectRequest;
import com.lixiaopu.common.properties.AliyunOSSProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import java.io.InputStream;
import java.io.IOException;
import java.util.UUID;

@Component
@Slf4j
public class AliyunOSSUtils {

    @Autowired
    private AliyunOSSProperties aliyunOSSProperties;

    /**
     * 默认上传，根目录
     */
    public String upload(MultipartFile file) throws IOException {
        return upload(file, "common");
    }

    /**
     * 上传文件，指定OSS存储目录（avatar / goods）
     * @param file 上传文件
     * @param dir  目录名，例如 avatar、goods
     * @return 文件完整访问url
     */
    public String upload(MultipartFile file, String dir) throws IOException {
        // 获取文件输入流
        InputStream inputStream = file.getInputStream();
        // 原始文件名
        String originalFilename = file.getOriginalFilename();
        // 获取后缀
        String suffix = originalFilename.substring(originalFilename.lastIndexOf("."));
        // 生成唯一文件名，防止重名覆盖，拼接目录
        String fileName = dir + "/" + UUID.randomUUID() + suffix;

        // 创建OSS客户端
        OSS ossClient = new OSSClientBuilder().build(
                aliyunOSSProperties.getEndpoint(),
                aliyunOSSProperties.getAccessKeyId(),
                aliyunOSSProperties.getAccessKeySecret()
        );

        try {
            PutObjectRequest putObjectRequest = new PutObjectRequest(
                    aliyunOSSProperties.getBucketName(),
                    fileName,
                    inputStream
            );
            ossClient.putObject(putObjectRequest);
            // 拼接访问地址
            String url = "https://" + aliyunOSSProperties.getBucketName() + "." + aliyunOSSProperties.getEndpoint() + "/" + fileName;
            log.info("OSS文件上传成功，url={}", url);
            return url;
        } finally {
            // 关闭oss客户端，释放资源
            if (ossClient != null) {
                ossClient.shutdown();
            }
            inputStream.close();
        }
    }
}
