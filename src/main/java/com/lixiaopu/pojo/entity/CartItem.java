package com.lixiaopu.pojo.entity;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 购物车条目，同一用户的同一商品合并数量
 */
@Data
@Builder
/*
*1. @Builder
建造者模式注解，自动生成建造者 Builder 静态内部类。
```
CartItem.builder()
        .userId("1001")
        .productId("p001")
        .specId("s001")
        .quantity(1)
        .build();
```
* 新建对象，一次性组装多个字段
- 自动生成：`CartItem.builder()` 静态方法，链式赋值，最后调用 `.build()` 创建对象。
- 原理：编译时生成 `CartItemBuilder` 内部类，包含所有字段的赋值方法，最后 build () new 对象。
- 适用场景：字段多的实体（CartItem、DTO），不用写一大堆重载构造器，可读性强。
**/

@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
//`@Accessors` 也是 Lombok 注解，`chain=true` 代表开启链式 setter
/** CartItem item = new CartItem()
*        .setUserId("1001")
*        .setProductId("p001")
*        .setQuantity(2);
 *        对象已经存在，需要修改属性；部分字段赋值
 */
public class CartItem implements Serializable {
/**
 * `implements Serializable` 代表这个类支持 Java 序列化；
 * `@Serial private static final long serialVersionUID = 1L;`
 * 是序列化版本号，用来在反序列化时校验序列化对象和当前类版本是否一致。
 * CartItem 存入 Redis，Redis 用 JDK 序列化时，就要求实体类实现 Serializable。
 * 1.
 * 只要类实现这个接口，就告诉 JVM：这个类的对象可以被**序列化 / 反序列化**。
 * - 序列化：把内存中的 Java 对象 → 字节数组（存入 Redis、文件、网络传输）
 * - 反序列化：字节数组 → 还原成内存 Java 对象
 * 2。
 * 反序列化的时候，JVM 会对比：
 * 序列化时对象里记录的 `serialVersionUID` 和 当前代码类里面的 `serialVersionUID`
 * - ✅ 相等：就算类新增 / 删除了几个字段，依然可以正常反序列化，缺失字段赋默认值。
 * static 属性不会参与序列化，`serialVersionUID` 本身不会被序列化；
 * final 手动固定写死，修改实体类（新增字段）时不改动这个值，旧的序列化数据依然能正常反序列化
 * - ❌ 不相等：直接抛出 `InvalidClassException`，反序列化失败。
* */


    @Serial
    private static final long serialVersionUID = 1L;
    /**
     * 购物车 ID
     */
    private String id;
    /**
     * 用户 ID
     */
    private String userId;
    /**
     * 商品 ID
     */
    private String productId;
    /**
     * 规格 ID
     */
    private String specId;
    /**
     * 数量
     */
    private Integer quantity;
    /**
     * 单价
     */
    private BigDecimal price;
    /**
     * 库存数量
     */
    private Integer stock;
    /**
     * 商品名称
     */
    private String productName;
    /**
     * 商品图片 URL
     */
    private String productImage;
    /**
     * 规格文本描述
     */
    private String specText;
    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    /**
     * 更新时间
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
}
