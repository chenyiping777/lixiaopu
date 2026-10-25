package com.lixiaopu.properties;

import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Data
@Component
@Validated
@ConfigurationProperties(prefix = "order")
public class OrderProperties {
    @Min(1)
    private long paymentTimeoutSeconds = 3600;

    /** 当前单店统一运费（元），报价与落单共用，禁止采用客户端报价。 */
    @jakarta.validation.constraints.NotNull
    @jakarta.validation.constraints.DecimalMin("0.00")
    @jakarta.validation.constraints.Digits(integer = 10, fraction = 2)
    private java.math.BigDecimal freight = new java.math.BigDecimal("6.00");
}
