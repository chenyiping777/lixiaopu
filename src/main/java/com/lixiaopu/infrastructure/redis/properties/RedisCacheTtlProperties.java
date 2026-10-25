package com.lixiaopu.infrastructure.redis.properties;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.Min;

@Component
@ConfigurationProperties(prefix = "redis.cache.ttl")
@Data
@Validated
public class RedisCacheTtlProperties {

    @Min(1)
    private long productDetailTtl = 1800;

    @Min(1)
    private long productCollectionTtl = 600;

    @Min(1)
    private long productEmptyTtl = 60;

    private long cartTtl = 7200;

    @Min(1)
    private long orderDetailTtl = 300;

    @Min(1)
    private long orderEmptyTtl = 30;

    private long productFirstCommentTtl;

    private long productSecondCommentTtl;

    private long productAppendCommentTtl;

    private long productCommentCountTtl;

    private long productCommentUserIdSetTtl;



}
