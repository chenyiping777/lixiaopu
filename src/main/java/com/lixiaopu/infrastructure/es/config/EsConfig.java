package com.lixiaopu.infrastructure.es.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;


@Configuration
@RequiredArgsConstructor// 生成构造函数
public class EsConfig {

    @Autowired
    private EsProperties esProperties;

    @Bean(destroyMethod = "close")// 关闭资源，释放连接
    public RestClientTransport elasticsearchTransport() {
        // 从 Spring 配置文件读取地址，自动解析 host 和 port
        String uris =esProperties.getUris();//读取 `application.yml/application.properties` 里配置的 ES 地址
        //把逗号分隔的地址字符串（例如 `127.0.0.1:8080,192.168.1.1:80`）转换成 `HttpHost` 对象数组
        HttpHost[] hosts = Arrays.stream(uris.split(","))
                .map(String::trim)
                .map(HttpHost::create)
                .toArray(HttpHost[]::new);

        // 构建 RestClient
        var builder = RestClient.builder(hosts);
        //判断用户名不为空，开启账号密码认证
        if (esProperties.getUsername() != null && !esProperties.getUsername().isBlank()) {
            var credentials = new BasicCredentialsProvider();
            credentials.setCredentials(
                    AuthScope.ANY,//对任意访问地址都使用这套账号密码
                    new UsernamePasswordCredentials(
                            esProperties.getUsername(), esProperties.getPassword()));
            builder.setHttpClientConfigCallback(http -> http.setDefaultCredentialsProvider(credentials));
        }
        RestClient restClient = builder.build();
        //`RestClient` 是底层 HTTP 通信组件，负责发送 http 请求到 ES 服务，`HttpHost` 封装 ES 节点的 host、port、http 协议。

        // Java 时间序列化支持：商品文档使用 Instant，输出带 Z 的 UTC 字符串
        //- `JavaTimeModule`：增加对 Java8 时间类支持，比如 `LocalDateTime`、`LocalDate`，不加这个模块序列化 LocalDateTime 会直接报错
        //- `disable(WRITE_DATES_AS_TIMESTAMPS)`：关闭时间输出为时间戳（毫秒数字），序列化为标准日期字符串，例如 `2026-10-05T02:00:00Z`，方便 ES 存储和查看。
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        //`RestClientTransport` 是 ES 新客户端的传输层，把底层的 `RestClient` 和 JSON 映射器 `JacksonJsonpMapper` 包装起来。
        //`JacksonJsonpMapper`：告诉 ES 客户端使用我们自定义好的 ObjectMapper（带 Java 时间支持）做 JSON 序列化和反序列化。
        RestClientTransport transport = new RestClientTransport(
                restClient,
                new JacksonJsonpMapper(objectMapper)
        );

        return transport;
    }

    @Bean// 创建 ElasticsearchClient 客户端
    public ElasticsearchClient elasticsearchClient(RestClientTransport transport) {
        return new ElasticsearchClient(transport);
    }
}
