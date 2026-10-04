# 购物车接口与 AOP：完整源码附录

源码快照日期：2026-10-03。配套阅读 [逻辑解析笔记](购物车接口与AOP学习笔记.md)。以下按当前工作区原文收录，保留 import 和用户注释；注释中的历史表述不代表当前行为，差异见笔记第12节。未修改业务文件。

## 目录

- [源码 1 - CartController.java](#source-1)
- [源码 2 - CartService.java](#source-2)
- [源码 3 - CartServiceImpl.java](#source-3)
- [源码 4 - CartProductDTO.java](#source-4)
- [源码 5 - CartDTO.java](#source-5)
- [源码 6 - Cart.java](#source-6)
- [源码 7 - CartItem.java](#source-7)
- [源码 8 - Product.java](#source-8)
- [源码 9 - ProductSpec.java](#source-9)
- [源码 10 - CartMapper.java](#source-10)
- [源码 11 - CartMapper.xml](#source-11)
- [源码 12 - ProductMapper.java](#source-12)
- [源码 13 - ProductSpecMapper.java](#source-13)
- [源码 14 - RedisConnector.java](#source-14)
- [源码 15 - RedisKeyGenerator.java](#source-15)
- [源码 16 - RedisCacheTtlProperties.java](#source-16)
- [源码 17 - SaveCartRedisCacheToMysqlAnnotation.java](#source-17)
- [源码 18 - SaveCartRedisCacheToMysqlAspect.java](#source-18)
- [源码 19 - SaveCartRedisCacheDelayJob.java](#source-19)
- [源码 20 - CartSyncExecutorConfig.java](#source-20)
- [源码 21 - CurrentHolder.java](#source-21)
- [源码 22 - UserInfo.java](#source-22)
- [源码 23 - Result.java](#source-23)
- [源码 24 - ResultCode.java](#source-24)
- [源码 25 - MessageConstant.java](#source-25)
- [源码 26 - CommonStatus.java](#source-26)
- [源码 27 - AuthFilter.java](#source-27)
- [源码 28 - AuthErrorHandler.java](#source-28)
- [源码 29 - LixiaopuApplication.java](#source-29)
- [源码 30 - AdminApplication.java](#source-30)
- [源码 31 - pom.xml](#source-31)
- [核心表建表 SQL](#source-schema)

<a id="source-1"></a>
## 1. CartController.java

源文件：[src/main/java/com/lixiaopu/controller/user/CartController.java](../src/main/java/com/lixiaopu/controller/user/CartController.java)

```java
package com.lixiaopu.controller.user;

import com.lixiaopu.aop.annotation.SaveCartRedisCacheToMysqlAnnotation;

import com.lixiaopu.common.result.Result;
import com.lixiaopu.pojo.dto.CartDTO;
import com.lixiaopu.pojo.dto.CartProductDTO;
import com.lixiaopu.service.CartService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/api/cart")
@RestController
public class CartController {
    @Autowired
    private CartService cartService;

    /**
     * 获取购物车列表
     */
    @GetMapping("/list")
    public Result getCartList() {

        return cartService.getCartList();
    }


    /**
     * 购物车操作（添加商品、清空、删除购物车商品、合并购物车）先操作 Redis，
     * 不立刻同步 MySQL；Service 执行成功之后，AOP 切面捕获成功返回结果，往延迟队列丢一个用户 ID，
     * 过一段时间再异步把该用户 Redis 里的购物车数据批量写入数据库。
     * Service 方法上的注解给 AOP 做切点标记。
     * 添加商品到购物车
     */
    @PostMapping("/add")
    @SaveCartRedisCacheToMysqlAnnotation
    public Result addProductToCart(@RequestBody @Valid CartProductDTO cartProductDTO) {
        return cartService.addProductToCart(cartProductDTO);
    }

    /**
     * 清空购物车
     *冗余接口(暂定)
     */
    @DeleteMapping("/clear")
    @SaveCartRedisCacheToMysqlAnnotation
    public Result clearCart() {
        return cartService.clearCart();
    }

    /**
     * 批量删除购物车商品(单个+批量)
     * DELETE /api/cart/products?productIds=100&specIds=201
     * 批量删除：
     * DELETE /api/cart/products?productIds=100,101&specIds=201,203
     */
    @DeleteMapping("/products")
    @SaveCartRedisCacheToMysqlAnnotation
    public Result deleteCartProduct(@RequestParam("productIds") String productIds,
                                    @RequestParam("specIds") String specIds) {
        return cartService.deleteCartProduct(productIds, specIds);
    }

    /**
     * 将前端的购物车数据(List)更新到数据库
     * 修改购物车内容，点击保存触发
     *
     * @param cartDTO
     * @return
     */
    @PutMapping("/update")
    @SaveCartRedisCacheToMysqlAnnotation
    public Result mergeCart(@RequestBody @Valid CartDTO cartDTO) {
        return cartService.mergeCart(cartDTO);
    }

}
```

<a id="source-2"></a>
## 2. CartService.java

源文件：[src/main/java/com/lixiaopu/service/CartService.java](../src/main/java/com/lixiaopu/service/CartService.java)

```java
package com.lixiaopu.service;

import com.lixiaopu.pojo.entity.Cart;
import com.lixiaopu.pojo.entity.CartItem;
import com.baomidou.mybatisplus.spring.service.IService;
import com.lixiaopu.common.result.Result;
import com.lixiaopu.pojo.dto.CartDTO;
import com.lixiaopu.pojo.dto.CartProductDTO;

/**
* @author Mlpnk
* @description 针对表【cart_item(购物车条目，同一用户的同一商品合并数量)】的数据库操作Service
* @createDate 2026-09-22 11:16:54
*/
public interface CartService extends IService<Cart> {
    Result getCartList();

    Result addProductToCart(CartProductDTO item);

    Result clearCart();

    Result deleteCartProduct(String productIds, String specIds);

    Result mergeCart(CartDTO cart);

    void syncCartToMysql(Long userId);
}
```

<a id="source-3"></a>
## 3. CartServiceImpl.java

源文件：[src/main/java/com/lixiaopu/service/impl/CartServiceImpl.java](../src/main/java/com/lixiaopu/service/impl/CartServiceImpl.java)

```java
package com.lixiaopu.service.impl;

import com.lixiaopu.infrastructure.redis.connect.RedisConnector;
import com.lixiaopu.infrastructure.redis.generator.RedisKeyGenerator;

import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.lixiaopu.aop.annotation.SaveCartRedisCacheToMysqlAnnotation;
import com.lixiaopu.common.constant.MessageConstant;
import com.lixiaopu.common.context.CurrentHolder;
import com.lixiaopu.common.result.Result;
import com.lixiaopu.infrastructure.redis.properties.RedisCacheTtlProperties;
import com.lixiaopu.mapper.CartMapper;
import com.lixiaopu.mapper.ProductMapper;
import com.lixiaopu.mapper.ProductSpecMapper;
import com.lixiaopu.pojo.dto.CartDTO;
import com.lixiaopu.pojo.dto.CartProductDTO;
import com.lixiaopu.pojo.entity.Cart;
import com.lixiaopu.pojo.entity.CartItem;
import com.lixiaopu.pojo.entity.Product;
import com.lixiaopu.pojo.entity.ProductSpec;
import com.lixiaopu.pojo.enums.CommonStatus;
import com.lixiaopu.service.CartService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.concurrent.TimeUnit;


@Service
@RequiredArgsConstructor
@Slf4j
public class CartServiceImpl extends ServiceImpl<CartMapper, Cart> implements CartService {
    @Autowired
    private CartMapper cartMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private ProductSpecMapper productSpecMapper;

    @Autowired
    private RedisConnector redisConnector;

    @Autowired
    private RedisCacheTtlProperties redisKeyTtlProperties;

    private static final String DELETE_IDS = "deletedIds";
    private static final String SUCCESS_COUNT = "successCount";
    private static final String EMPTY_CART_HASH_KEY = RedisKeyGenerator.cartHashKey("0", "0");

    // Redis 存储用户的完整购物车快照；成功修改后由切面安排延迟同步到 MySQL。

    /**
     * 获取购物车列表
     *
     */
    @Override
    public Result getCartList() {
        // 1. 获取当前登录用户ID（从上下文持有者取出登录用户信息）
        Long userId = CurrentHolder.getCurrentUser().getId();
        // 2. 根据用户ID拼接Redis购物车Hash的key，如 cart:10001
        String cartKey = RedisKeyGenerator.cartKey(userId);
        // 3. 读取Redis购物车Hash全部数据，封装为Map（抽成公共方法loadCartMap）
        Map<String, Object> cartMap = loadCartMap(userId, cartKey);

        // 4. 流式处理购物车数据，组装返回给前端的购物车列表
        List<CartItem> cartItemList = cartMap.entrySet().stream()
                // 过滤掉占位标记的特殊HashKey（不是真实商品条目，仅用来标记购物车存在）
                .filter(entry -> !EMPTY_CART_HASH_KEY.equals(entry.getKey()))
                // 取出Hash中的value，强转为CartItem购物车条目对象
                .map(entry -> (CartItem) entry.getValue())
                // 收集流转为List集合
                .toList();

        // 5. 封装结果返回给前端，购物车数据全部来源于Redis
        return Result.success(cartItemList);
    }

    /**
     * 加载用户购物车Map，实现【Redis缓存优先，MySQL兜底】的缓存预热逻辑
     * 先读Redis；Redis无购物车数据，则读取MySQL备份快照，写入Redis，再返回数据
     * @param userId 用户ID
     * @param cartKey 用户购物车在Redis中的Hash key
     * @return 购物车Hash的field-value映射Map
     */
    private Map<String, Object> loadCartMap(Long userId, String cartKey) {
        // 1. 优先从Redis读取该用户购物车Hash全部数据
        Map<String, Object> cartMap = redisConnector.opsForHash().entries(cartKey);

        // 2. 如果Redis查到购物车数据，直接返回Redis的数据（缓存命中）
        if (!cartMap.isEmpty()) {
            return cartMap;
        }

        // 3. Redis没有购物车数据（缓存未命中），去MySQL读取备份快照
        List<CartItem> storedItems = cartMapper.getCartList(userId);

        // 4. MySQL里面也没有购物车记录：
        // 写入一个空标记key到Redis，避免后续重复查询MySQL（缓存空值，防止缓存穿透）
        if (CollectionUtils.isEmpty(storedItems)) {
            writeEmptyCart(cartKey);
            // 返回只包含占位标记key的Map，代表购物车为空
            return Map.of(EMPTY_CART_HASH_KEY, "");
        }

        // 5. MySQL查到购物车数据，把MySQL的CartItem转为Redis Hash结构
        Map<String, Object> loaded = new HashMap<>(storedItems.size());
        for (CartItem item : storedItems) {
            // 生成Hash内的field（商品+规格的唯一标识），放入CartItem对象作为value
            String hashField = RedisKeyGenerator.cartHashKey(item.getProductId(), item.getSpecId());
            loaded.put(hashField, item);
        }

        // 6. 将从MySQL读取出来的购物车数据批量写入Redis（缓存预热）
        redisConnector.opsForHash().putAll(cartKey, loaded);
        // 设置Redis购物车key的过期时间
        redisConnector.expire(cartKey, redisKeyTtlProperties.getCartTtl(), TimeUnit.SECONDS);

        // 7. 返回已经加载到内存的购物车Map
        //返回Redis 大 key `cartKey`（整个用户购物车 Hash）下，
        // 所有 hash 内 field（也就是代码里的 cartHashKey）和它们对应的 value。
        return loaded;
    }


    /**
     * 写入一个空标记到Redis，避免后续重复查询MySQL
     * @param cartKey 用户购物车在Redis中的Hash key
     */
    private void writeEmptyCart(String cartKey) {
        // 删除Redis购物车Hash key，避免后续重复查询MySQL
        redisConnector.delete(cartKey);
        // 写入一个空标记到Redis，避免后续重复查询MySQL
        redisConnector.opsForHash().put(cartKey, EMPTY_CART_HASH_KEY, "");
        // 设置Redis购物车key的过期时间
        redisConnector.expire(cartKey, redisKeyTtlProperties.getCartTtl(), TimeUnit.SECONDS);
    }

    private static Long positiveId(String value) {
        if (StringUtils.isBlank(value) || !value.matches("[1-9][0-9]*")) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean validCartProduct(CartProductDTO item) {
        return item != null && positiveId(item.getProductId()) != null
                && positiveId(item.getSpecId()) != null
                && item.getQuantity() != null && item.getQuantity() > 0;
    }


    /**
     * 根据商品 + 规格，补全购物车项信息
     * @param productDetailMap 商品ID为key，商品 + 规格列表为value的Map
     * @param cartItem 购物车项
     * @return 补全后的购物车项
     * 根据 CartItem.productId 从 Map 中找到商品。
     * 根据 CartItem.specId 从商品规格列表中找到用户选中的规格。
     * 将商品名称、图片，以及该规格的价格、库存、规格描述填入 CartItem。
     */
    private CartItem replenishCartItem(Map<Long, Product> productDetailMap, CartItem cartItem) {
        if (Objects.isNull(productDetailMap) || Objects.isNull(cartItem)) {
            return null;
        }
        // 根据cartItem里的productId取出商品
        Long productId = Long.valueOf(cartItem.getProductId());
        Product product = productDetailMap.get(productId);
        // 商品不存在 或者 商品没有任何规格 → 返回null
        if (product == null || CollectionUtils.isEmpty(product.getSpecList())) {
            return null;
        }
        List<ProductSpec> specList = product.getSpecList();
        ProductSpec productSpec = null;
        // 循环遍历规格，匹配specId，找到用户选择的那一条规格
        for (ProductSpec productSpecTemp : specList) {
            if (productSpecTemp != null && productSpecTemp.getId() != null
                    && StringUtils.equals(productSpecTemp.getId().toString(), cartItem.getSpecId())) {
                productSpec = productSpecTemp;
                break;
            }
        }
        // 找不到对应规格 → 返回null
        if (Objects.isNull(productSpec)) {
            return null;
        }
        // 把规格价格、库存；商品名称、图片；规格描述填充进cartItem
        cartItem.setPrice(productSpec.getPrice())
                .setStock(productSpec.getStock())
                .setProductName(product.getName())
                .setProductImage(product.getImage())
                .setSpecText(productSpec.getSpecText());
        return cartItem;
    }

    /**
     * 根据商品ID集合，批量查询商品 + 规格完整信息,查询商品信息和规格信息。
     * 把每个商品的全部规格放进 Product.specList。
     * @param productIds 商品ID集合
     * @return 商品ID为key，商品 + 规格列表为value的Map, Map<商品ID, Product>。
     */
    private Map<Long, Product> getProductDetailByProductIdSet(Set<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        log.info("根据商品ID集合，批量查询商品 + 规格完整信息，productIds={}", productIds);
        // 1. 查询【启用状态】的商品列表，in批量查询，传入多个商品id可以一次性查多个商品
        List<Product> products = productMapper.selectList(Wrappers.lambdaQuery(Product.class)
                .in(Product::getId, productIds)
                .eq(Product::getStatus, CommonStatus.ACTIVE));
        // 2. 查询这些商品对应的所有规格 ProductSpec
        List<ProductSpec> specs = productSpecMapper.selectList(Wrappers.lambdaQuery(ProductSpec.class)
                .in(ProductSpec::getProductId, productIds));
        // 3. 按productId分组：key=商品id，value=该商品下全部规格列表
        Map<Long, List<ProductSpec>> specsByProduct = new HashMap<>();
        for (ProductSpec spec : specs) {
            specsByProduct.computeIfAbsent(spec.getProductId(), ignored -> new ArrayList<>()).add(spec);
        }
        // 4. 遍历商品，把对应规格列表set进Product对象，组装成Map返回
        Map<Long, Product> details = new HashMap<>();
        for (Product product : products) {
            product.setSpecList(specsByProduct.getOrDefault(product.getId(), List.of()));
            //取出当前商品对应的全部规格；如果该商品没有规格，给一个空不可变集合`List.of()`，避免 null。
            details.put(product.getId(), product);//Map<Long, Product>
        }
        return details;
    }

    /**
     * 添加商品到购物车
     * 用户加购商品时先校验参数，查询 Redis 购物车，loadCartMap 方法
     * 已存在该规格商品就累加数量（做溢出保护），
     * 不存在则新建购物项并补全商品信息，更新 Redis 购物车、
     * 移除空购物标记并刷新过期时间；
     * 通过自定义注解触发后续异步把 Redis 购物车数据持久化到 MySQL，
     * 采用缓存优先、MySQL 兜底的最终一致性方案。
     */
    @Override
    @SaveCartRedisCacheToMysqlAnnotation
    public Result addProductToCart(CartProductDTO cartProductDTO) {

        if (!validCartProduct(cartProductDTO)) {
            return Result.error(MessageConstant.INPUT_DATA_ERROR);
        }
        Long userId = CurrentHolder.getCurrentUser().getId();

        String productId = cartProductDTO.getProductId();
        String specId = cartProductDTO.getSpecId();
        Integer quantity = cartProductDTO.getQuantity();

        String cartKey = RedisKeyGenerator.cartKey(userId); //lxp:cart:{userId};
        String cartHashKey = RedisKeyGenerator.cartHashKey(productId, specId);//productId + ":" + specId  商品id:规格id
        // 从Redis缓存中加载购物车数据,Redis没有购物车数据（缓存未命中），去MySQL读取备份快照
        // 数据库也没有，写入空标记
        Map<String, Object> redisCacheMap = loadCartMap(userId, cartKey);
        CartItem cartItem;
        if (redisCacheMap.containsKey(cartHashKey)) {
            //已存在该规格商品就累加数量（做溢出保护，addExact两个 int 相加，发生整数溢出时直接抛异常；不溢出返回相加结果。）
            cartItem = (CartItem) redisCacheMap.get(cartHashKey);
            try {
                cartItem.setQuantity(Math.addExact(cartItem.getQuantity(), quantity));
            } catch (ArithmeticException e) {
                return Result.error(MessageConstant.INPUT_DATA_ERROR);
            }
        } else {
            //不存在则新建购物项并补全商品信息,，更新 Redis 购物车
            cartItem = CartItem.builder()
                    .userId(String.valueOf(userId))
                    .productId(productId)
                    .specId(specId)
                    .quantity(quantity)
                    .build();
            //新增购物项时，根据商品 ID 查询商品 + 规格完整信息，
            //把商品名称、图片、规格价格、库存填充到 CartItem 购物项对象；
            //如果商品不存在 / 已下架 / 找不到对应规格，直接返回 null，
            //上层返回`DATA_ERROR`。
            //业务背景：CartItem 只存了`userId、productId、specId、quantity`这几个基础 id 和数量，
            //价格、商品名、图片、规格文字、库存不能靠前端传（前端可篡改），必须从数据库查出来填充。
            Map<Long, Product> productDetailMap =
                    getProductDetailByProductIdSet(Set.of(Long.valueOf(productId)));
            //把当前要加购的商品 id 包装成`Set`，调用`getProductDetailByProductIdSet`批量查询商品 + 规格；
            cartItem = replenishCartItem(productDetailMap, cartItem);
            if (cartItem == null) {
                return Result.error(MessageConstant.DATA_ERROR);
            }
        }
        redisConnector.opsForHash().delete(cartKey, EMPTY_CART_HASH_KEY);
        redisConnector.opsForHash().put(cartKey, cartHashKey, cartItem);
        redisConnector.expire(cartKey, redisKeyTtlProperties.getCartTtl(), TimeUnit.SECONDS);
        return Result.success();


    }

    /**
     * 清空当前登录用户的 Redis 购物车，同时收集本次被清空的所有商品 ID，返回给前端。
     * @return 被清空的商品 ID 集合
     */
    @Override
    @SaveCartRedisCacheToMysqlAnnotation
    public Result clearCart() {
        Long userId = CurrentHolder.getCurrentUser().getId();
        String cartKey = RedisKeyGenerator.cartKey(userId);
        //加载购物车信息，Redis（cartKey）没有购物车数据（缓存未命中），去MySQL读取备份快照（userId）
        Map<String, Object> cartMap = loadCartMap(userId, cartKey);
        List<String> removedProductIds =
                cartMap.entrySet()
                        .stream()
                        // 过滤掉空购物车的占位key，不要把占位标记当成商品
                        .filter(entry -> !EMPTY_CART_HASH_KEY.equals(entry.getKey()))
                        .map(entry -> ((CartItem) entry.getValue())
                                                                    .getProductId())
                        .toList();
        //1. 删除这个 Hash 里面所有原来的 field（全部购物项）；
        //2. 往 Hash 写入一条特殊占位 field：`EMPTY_CART_HASH_KEY`；并设置过期时间
        writeEmptyCart(cartKey);
        Map<String, Object> map = new HashMap<>(2);
        map.put(DELETE_IDS, removedProductIds);
        map.put(SUCCESS_COUNT, removedProductIds.size());
        return Result.success(map);//返回前端删除的商品ID集合
    }

    /** 按商品与规格删除，可一次删除多个条目。 */
    @Override
    @SaveCartRedisCacheToMysqlAnnotation
    public Result deleteCartProduct(String productIds, String specIds) {
        if (StringUtils.isBlank(productIds) || StringUtils.isBlank(specIds)) {
            return Result.error(MessageConstant.INPUT_DATA_ERROR);
        }

        ////1.把前端传来的 ID 转成“要删除的购物车条目键”
        // 第一步：按逗号分割字符串，得到字符串数组
        String[] productIdArray = productIds.split(",", -1);
        // 第二步：把数组转为List
        List<String> productIdsList = Arrays.asList(productIdArray);

        List<String> specIdsList = Arrays.asList(specIds.split(",", -1));
        if (productIdsList.size() != specIdsList.size()) {
            return Result.error(MessageConstant.INPUT_DATA_ERROR);
        }
        ////2.检查这些条目是否存在
        // 第三步：把商品ID和规格ID组合成Hash Key，同时利用set去重
        Set<String> hashKeys = new HashSet<>();
        for (int i = 0; i < productIdsList.size(); i++) {
            String productId = productIdsList.get(i);
            String specId = specIdsList.get(i);
            if (positiveId(productId) == null || positiveId(specId) == null
                    || !hashKeys.add(RedisKeyGenerator.cartHashKey(productId, specId))) {
                                                    //hashKeys = {"100:201", "101:203"};
                //两个参数不能为空。
                //商品 ID 和规格 ID 的数量必须相同。
                //每个 ID 必须是合法的正整数。
                //不能重复提交同一对商品和规格。
                return Result.error(MessageConstant.INPUT_DATA_ERROR);
            }
        }

        ////3.再从 Redis 删除。
        Long userId = CurrentHolder.getCurrentUser().getId();
        String cartKey = RedisKeyGenerator.cartKey(userId);
        Map<String, Object> cartMap = loadCartMap(userId, cartKey);
        if (!cartMap.keySet().containsAll(hashKeys)) {
            return Result.error(MessageConstant.CART_NOT_EXIST_ERROR);
        }
        redisConnector.opsForHash().delete(cartKey, hashKeys.toArray());
        if (redisConnector.opsForHash().entries(cartKey).isEmpty()) {
            //购物车为空：写入“空购物车标记”，让后续查询知道购物车确实为空，避免又去数据库加载旧数据。
            writeEmptyCart(cartKey);
        } else {
            //还有条目：刷新购物车缓存的过期时间。
            redisConnector.expire(cartKey, redisKeyTtlProperties.getCartTtl(), TimeUnit.SECONDS);
        }
        HashMap<String, Object> map = new HashMap<>(2);
        map.put(DELETE_IDS, productIdsList);
        map.put(SUCCESS_COUNT, hashKeys.size());
        return Result.success(map);
    }


    /**
     * 将前端的购物车数据(List)更新到Redis
     *接收前端传的本地购物车列表，做参数校验、去重、查数据库校验商品和规格合法性，全部校验通过后，
     *删除用户原来 Redis 购物车，一次性把新的这批购物项写入 Redis，设置过期时间，返回成功；
     *只要任意一条商品 / 规格非法，整个合并直接失败，不写入。
     */
    @Override
    @SaveCartRedisCacheToMysqlAnnotation
    public Result mergeCart(CartDTO cartDTO) {
        if (cartDTO == null || cartDTO.getCartItems() == null) {
            return Result.error(MessageConstant.INPUT_DATA_ERROR);
        }
        List<CartProductDTO> carts = cartDTO.getCartItems();
        Long userId = CurrentHolder.getCurrentUser().getId();
        String cartKey = RedisKeyGenerator.cartKey(userId);
        if (carts.isEmpty()) {
            writeEmptyCart(cartKey);
            return Result.success();
        }
        Set<Long> productIds = new HashSet<>();
        Set<String> hashKeys = new HashSet<>();
        for (CartProductDTO item : carts) {
            if (!validCartProduct(item)
                    || !hashKeys.add(RedisKeyGenerator.cartHashKey(item.getProductId(), item.getSpecId()))) {
                return Result.error(MessageConstant.INPUT_DATA_ERROR);
            }
            productIds.add(Long.valueOf(item.getProductId()));
        }
        // 先补全并校验整份快照，任何商品或规格无效都不能覆盖旧购物车。
        Map<Long, Product> productDetailMap = getProductDetailByProductIdSet(productIds);
        Map<String, Object> replacement = new HashMap<>(carts.size());
        for (CartProductDTO item : carts) {
            CartItem cartItem = CartItem
                    .builder()
                    .userId(String.valueOf(userId))
                    .productId(item.getProductId())
                    .specId(item.getSpecId())
                    .quantity(item.getQuantity())
                    .build();
            cartItem = replenishCartItem(productDetailMap, cartItem);
            if (cartItem == null) {
                return Result.error(MessageConstant.DATA_ERROR);
            }
            replacement.put(RedisKeyGenerator.cartHashKey(item.getProductId(), item.getSpecId()), cartItem);
        }
        redisConnector.delete(cartKey);
        redisConnector.opsForHash().putAll(cartKey, replacement);
        redisConnector.expire(cartKey, redisKeyTtlProperties.getCartTtl(), TimeUnit.SECONDS);
        return Result.success(carts);
    }

    /**
     * 将 Redis 缓存中的购物车同步到 MySQL
     *
     * @param userId 用户ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncCartToMysql(Long userId) {
        //从 Redisson 延迟队列取出来的用户 id，用来同步该用户 Redis 购物车到 MySQL。
        final Long validUserId = positiveId(String.valueOf(userId));
        if (validUserId == null) {
            throw new IllegalArgumentException("Invalid cart user ID");
        }

        String cartKey = RedisKeyGenerator.cartKey(validUserId);
        Map<String, Object> cartMap = redisConnector.opsForHash().entries(cartKey);
        // Redis key 缺失表示没有可信快照，不能据此清空 MySQL。
        if (cartMap.isEmpty()) {
            return;
        }
        //stream 流，把 Redis 读取到的购物车数据，转成数据库实体 List<Cart>
        List<Cart> cartList = cartMap.entrySet().stream()
                .filter(entry -> !EMPTY_CART_HASH_KEY.equals(entry.getKey()))
                .map(entry -> {
                    if (!(entry.getValue() instanceof CartItem item)) {
                        throw new IllegalStateException("Invalid cart cache entry");
                    }
                    return item;
                })
                .map(item -> Cart.builder()
                        .userId(validUserId)
                        .productId(Long.valueOf(item.getProductId()))
                        .specId(Long.valueOf(item.getSpecId()))
                        .quantity(item.getQuantity())
                        .checked(CommonStatus.INACTIVE.getNumber())
                        //勾选状态是高频变化的字段，没必要持久化到 MySQL，
                        //只存在 Redis 缓存即可。就算 Redis 宕机，从 MySQL 恢复购物车数据的时候，
                        //全部商品默认置为未选中，由用户重新勾选。
                        .build())
                .toList();
        //删除这个用户 MySQL 里全部旧的购物车记录。
        lambdaUpdate().eq(Cart::getUserId, validUserId).remove();
        //批量保存新的购物车记录。
        if (!cartList.isEmpty() && !saveBatch(cartList)) {
            throw new IllegalStateException("Failed to save cart snapshot");
        }
    }
}
```

<a id="source-4"></a>
## 4. CartProductDTO.java

源文件：[src/main/java/com/lixiaopu/pojo/dto/CartProductDTO.java](../src/main/java/com/lixiaopu/pojo/dto/CartProductDTO.java)

```java
package com.lixiaopu.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CartProductDTO {
    @NotBlank
    @Pattern(regexp = "[1-9][0-9]*")
    private String productId;// 商品ID
    @NotBlank
    @Pattern(regexp = "[1-9][0-9]*")
    private String specId;// 规格ID
    @NotNull
    @Min(1)
    private Integer quantity;// 数量
}
```

<a id="source-5"></a>
## 5. CartDTO.java

源文件：[src/main/java/com/lixiaopu/pojo/dto/CartDTO.java](../src/main/java/com/lixiaopu/pojo/dto/CartDTO.java)

```java
package com.lixiaopu.pojo.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class CartDTO {
    @NotNull
    @JsonAlias("items")
    private List<@Valid CartProductDTO> cartItems;
}
```

<a id="source-6"></a>
## 6. Cart.java

源文件：[src/main/java/com/lixiaopu/pojo/entity/Cart.java](../src/main/java/com/lixiaopu/pojo/entity/Cart.java)

```java
package com.lixiaopu.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.time.LocalDateTime;
import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 栗小铺 cart
 * @TableName cart
 */
@TableName(value ="cart")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Cart {
     /**
     * 购物车ID（主键）
     * 对应数据库字段：id
     * 自增策略匹配建表语句的 auto_increment
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 关联用户ID（外键）
     * 对应数据库字段：user_id
     */
    @TableField(value = "user_id")
    private Long userId;

    /**
     * 关联商品ID（外键）
     * 对应数据库字段：product_id
     */
    @TableField(value = "product_id")
    private Long productId;

    /**
     * 关联规格ID（外键，可空）
     * 对应数据库字段：spec_id
     */
    @TableField(value = "spec_id")
    private Long specId;

    /**
     * 购买数量
     * 对应数据库字段：quantity
     * 默认值：1（与建表语句一致）
     */
    @TableField(value = "quantity")
    private Integer quantity = 1;

    /**
     * 是否选中（0-否，1-是）
     * 对应数据库字段：checked
     * 默认值：0（与建表语句一致）
     * 数据库类型 tinyint(1)，对应Java布尔类型或Integer均可，此处用Integer更兼容
     */
    @TableField(value = "checked")
    private Integer checked = 0;

    /**
     * 创建时间
     * 对应数据库字段：create_time
     * 自动填充策略：插入时自动填充（匹配 CURRENT_TIMESTAMP）
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     * 对应数据库字段：update_time
     * 自动填充策略：插入和更新时自动填充（匹配 CURRENT_TIMESTAMP on update CURRENT_TIMESTAMP）
     */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
```

<a id="source-7"></a>
## 7. CartItem.java

源文件：[src/main/java/com/lixiaopu/pojo/entity/CartItem.java](../src/main/java/com/lixiaopu/pojo/entity/CartItem.java)

```java
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
```

<a id="source-8"></a>
## 8. Product.java

源文件：[src/main/java/com/lixiaopu/pojo/entity/Product.java](../src/main/java/com/lixiaopu/pojo/entity/Product.java)

```java
package com.lixiaopu.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.lixiaopu.common.constant.DatePatternConstants;
import com.lixiaopu.pojo.enums.CommonStatus;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 栗小铺 product
 * @TableName product
 */
@Data
@TableName(value = "product")
@FieldNameConstants
public class Product implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 商品ID（主键）
     */
    @TableId(type = IdType.AUTO) // 对应数据库自增主键
    private Long id;

    /**
     * 关联分类 ID
     */
    @TableField(value = "category_id")
    private Long categoryId;

    /**
     * 商品名称
     */
    @TableField(value = "name")
    private String name;

    /**
     * 商品卖点/简介
     */
    @TableField(value = "sell_point")
    private String sellPoint;

    /**
     * 基础价格（最低规格价格）
     */
    @TableField(value = "price")
    private BigDecimal price;

    /**
     * 企业批量价格（有值则表示启用企业价）
     */
    @TableField(value = "enterprise_price")
    private BigDecimal enterprisePrice;

    /**
     * 总库存数量（所有规格库存之和）
     */
    @TableField(value = "stock")
    private Long stock;

    /**
     * 商品封面图 URL
     */
    @TableField(value = "cover_image")
    private String image;

    /**
     * 商品详情（富文本）
     */
    @TableField(value = "description")
    private String description;

    /**
     * 浏览量
     */
    @TableField(value = "view_count")
    private Long viewCount;

    /**
     * 销量
     */
    @TableField(value = "sales_count")
    private Long salesCount;

    /**
     * 状态（0-下架，1-上架）
     */
    @TableField(value = "status")
    private CommonStatus status;

    /**
     * 是否收藏,0-否,1-是
     */
    @TableField(exist = false)
    private Integer isCollection;

    /**
     * 商品图片 URL 列表
     */
    @TableField(exist = false)
    private List<String> imageUrls;

    /**
     *商品规格列表
     */
    @TableField(exist = false)
    private List<ProductSpec> specList;

    /**
     * 创建时间
     */
    @DateTimeFormat(pattern = DatePatternConstants.DATE_TIME_FORM)
    @JsonFormat(pattern = DatePatternConstants.DATE_TIME_FORM)
    @TableField(value = "create_time", fill = FieldFill.INSERT) // 插入时自动填充
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @DateTimeFormat(pattern = DatePatternConstants.DATE_TIME_FORM)
    @JsonFormat(pattern = DatePatternConstants.DATE_TIME_FORM)
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE) // 插入/更新时自动填充
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    private LocalDateTime updateTime;


}
```

<a id="source-9"></a>
## 9. ProductSpec.java

源文件：[src/main/java/com/lixiaopu/pojo/entity/ProductSpec.java](../src/main/java/com/lixiaopu/pojo/entity/ProductSpec.java)

```java
package com.lixiaopu.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.lixiaopu.common.constant.DatePatternConstants;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

/** 对应 product_spec；业务逻辑按模块逐步实现。 */
@Data
@TableName("product_spec")
public class  ProductSpec implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 规格ID（主键）
     */
    @TableId(type = IdType.AUTO) // 对应数据库 auto_increment 自增主键
    private Long id;

    /**
     * 关联商品ID（外键）
     */
    @TableField(value = "product_id")
    private Long productId;

    /**
     * 规格描述（如：1.2m×0.8m/原木色）
     */
    @TableField(value = "spec_text")
    private String specText;

    /**
     * 规格单价
     */
    @TableField(value = "price")
    private BigDecimal price;

    /**
     * 企业批量价格（有值则表示启用）
     */
    @TableField(value = "enterprise_price")
    private BigDecimal enterprisePrice;

    /**
     * 规格库存
     */
    @TableField(value = "stock")
    private Integer stock;

    /**
     * 创建时间
     */
    @DateTimeFormat(pattern = DatePatternConstants.DATE_TIME_FORM)
    @JsonFormat(pattern = DatePatternConstants.DATE_TIME_FORM)
    @TableField(value = "create_time", fill = FieldFill.INSERT) // 插入时自动填充
    private Date createTime;

    /**
     * 更新时间
     */
    @DateTimeFormat(pattern = DatePatternConstants.DATE_TIME_FORM)
    @JsonFormat(pattern = DatePatternConstants.DATE_TIME_FORM)
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE) // 插入和更新时自动填充
    private Date updateTime;
}
```

<a id="source-10"></a>
## 10. CartMapper.java

源文件：[src/main/java/com/lixiaopu/mapper/CartMapper.java](../src/main/java/com/lixiaopu/mapper/CartMapper.java)

```java
package com.lixiaopu.mapper;

import com.lixiaopu.pojo.entity.Cart;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lixiaopu.pojo.entity.CartItem;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
* @author Mlpnk
* @description 针对表【cart(栗小铺 cart)】的数据库操作Mapper
* @createDate 2026-09-23 17:02:22
* @Entity com.lixiaopu.pojo.entity.Cart
*/
public interface CartMapper extends BaseMapper<Cart> {
    @Select("""
            SELECT c.id, c.user_id, c.product_id, c.spec_id, c.quantity, c.checked,
                   c.create_time, c.update_time, p.name AS product_name,
                   p.cover_image AS product_image, ps.spec_text, ps.price, ps.stock
            FROM cart c
            LEFT JOIN product p ON p.id = c.product_id
            LEFT JOIN product_spec ps ON ps.id = c.spec_id
            WHERE c.user_id = #{userId}
            """)
    List<CartItem> getCartList(@Param("userId") Long userId);
}
```

<a id="source-11"></a>
## 11. CartMapper.xml

源文件：[src/main/resources/mapper/CartMapper.xml](../src/main/resources/mapper/CartMapper.xml)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper
        PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.lixiaopu.mapper.CartMapper">

    <resultMap id="BaseResultMap" type="com.lixiaopu.pojo.entity.Cart">
            <id property="id" column="id" />
            <result property="userId" column="user_id" />
            <result property="productId" column="product_id" />
            <result property="specId" column="spec_id" />
            <result property="quantity" column="quantity" />
            <result property="checked" column="checked" />
            <result property="createTime" column="create_time" />
            <result property="updateTime" column="update_time" />
    </resultMap>

    <sql id="Base_Column_List">
        id,user_id,product_id,spec_id,quantity,checked,
        create_time,update_time
    </sql>
</mapper>
```

<a id="source-12"></a>
## 12. ProductMapper.java

源文件：[src/main/java/com/lixiaopu/mapper/ProductMapper.java](../src/main/java/com/lixiaopu/mapper/ProductMapper.java)

```java
package com.lixiaopu.mapper;

import com.lixiaopu.pojo.entity.Product;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
* @author Mlpnk
* @description 针对表【product(栗小铺 product)】的数据库操作Mapper
* @createDate 2026-09-23 17:08:37
* @Entity com.lixiaopu.pojo.entity.Product
*/
public interface ProductMapper extends BaseMapper<Product> {

}
```

<a id="source-13"></a>
## 13. ProductSpecMapper.java

源文件：[src/main/java/com/lixiaopu/mapper/ProductSpecMapper.java](../src/main/java/com/lixiaopu/mapper/ProductSpecMapper.java)

```java
package com.lixiaopu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lixiaopu.pojo.entity.ProductSpec;

public interface ProductSpecMapper extends BaseMapper<ProductSpec> {
}
```

<a id="source-14"></a>
## 14. RedisConnector.java

源文件：[src/main/java/com/lixiaopu/infrastructure/redis/connect/RedisConnector.java](../src/main/java/com/lixiaopu/infrastructure/redis/connect/RedisConnector.java)

```java
package com.lixiaopu.infrastructure.redis.connect;

import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.stereotype.Component;

@Component
public final class RedisConnector {

    private final RedisTemplate<String, Object> template;
    //可以直接用RedisTemplate，
    //`RedisTemplate`默认序列化器是 JDK 序列化，
    //存进去是二进制乱码，Redis 客户端看不了，而且要求实体实现 Serializable，缺点很多。
    //当前在`RedisConnector`的构造器里面一次性配置好：
    //- key：String 序列化
    //- hashKey：String 序列化
    //- hashValue：Jackson JSON 序列化，直接存 Java 对象 CartItem
    //`StringRedisTemplate` 只能value 存字符串；
    public RedisConnector(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> configured = new RedisTemplate<>();
        // 配置 Redis 连接工厂
        configured.setConnectionFactory(connectionFactory);
        // 配置 key 序列化器
        configured.setKeySerializer(new StringRedisSerializer());
        // 配置 hashKey 序列化器
        configured.setHashKeySerializer(new StringRedisSerializer());
        // 配置 hashValue 序列化器
        configured.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        // 初始化配置
        configured.afterPropertiesSet();
        // 返回配置好的 RedisTemplate
        this.template = configured;
    }

    // Hash 操作
    public HashOperations<String, String, Object> opsForHash() {
        return template.opsForHash();
    }

    // 设置过期时间
    public void expire(String key, long ttl, TimeUnit unit) {
        template.expire(key, ttl, unit);
    }
    // 删除键

    public void delete(String key) {
        template.delete(key);
    }
}
```

<a id="source-15"></a>
## 15. RedisKeyGenerator.java

源文件：[src/main/java/com/lixiaopu/infrastructure/redis/generator/RedisKeyGenerator.java](../src/main/java/com/lixiaopu/infrastructure/redis/generator/RedisKeyGenerator.java)

```java
package com.lixiaopu.infrastructure.redis.generator;

public final class RedisKeyGenerator {
    private static final String CART_KEY_PREFIX = "lxp:cart:";

    private RedisKeyGenerator() {
    }

    public static String cartKey(Long userId) {
        return CART_KEY_PREFIX + userId;
    }

    public static String cartHashKey(Object productId, Object specId) {
        return productId + ":" + specId;
    }
}
```

<a id="source-16"></a>
## 16. RedisCacheTtlProperties.java

源文件：[src/main/java/com/lixiaopu/infrastructure/redis/properties/RedisCacheTtlProperties.java](../src/main/java/com/lixiaopu/infrastructure/redis/properties/RedisCacheTtlProperties.java)

```java
package com.lixiaopu.infrastructure.redis.properties;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "redis.cache.ttl")
@Data
public class RedisCacheTtlProperties {

    private long productDetailTtl;

    private long productCollectionTtl;

    private long cartTtl = 7200;

    private long orderTtl;

    private long productFirstCommentTtl;

    private long productSecondCommentTtl;

    private long productAppendCommentTtl;

    private long productCommentCountTtl;

    private long productCommentUserIdSetTtl;

    private long userProductCommentLikeIdSetTtl;


}
```

<a id="source-17"></a>
## 17. SaveCartRedisCacheToMysqlAnnotation.java

源文件：[src/main/java/com/lixiaopu/aop/annotation/SaveCartRedisCacheToMysqlAnnotation.java](../src/main/java/com/lixiaopu/aop/annotation/SaveCartRedisCacheToMysqlAnnotation.java)

```java
package com.lixiaopu.aop.annotation;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SaveCartRedisCacheToMysqlAnnotation {
    // 该注解用于标识需要将购物车缓存保存到MySQL的方法
}
```

<a id="source-18"></a>
## 18. SaveCartRedisCacheToMysqlAspect.java

源文件：[src/main/java/com/lixiaopu/aop/aspect/SaveCartRedisCacheToMysqlAspect.java](../src/main/java/com/lixiaopu/aop/aspect/SaveCartRedisCacheToMysqlAspect.java)

```java
package com.lixiaopu.aop.aspect;

import com.lixiaopu.common.context.CurrentHolder;
import com.lixiaopu.common.result.Result;
import com.lixiaopu.service.CartService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import java.util.Objects;
import com.lixiaopu.job.delay.SaveCartRedisCacheDelayJob;


@Aspect
@Component
@Slf4j
public class SaveCartRedisCacheToMysqlAspect {

    @Autowired
    private SaveCartRedisCacheDelayJob saveCartRedisCacheDelayJob;

    @Autowired
    private CartService cartService;


    //切入点（Pointcut）：单独定义，命名复用
    //@Pointcut 注解里那串
    //@annotation(com.lixiaopu.aop.annotation.SaveCartRedisCacheToMysqlAnnotation)
    //才是真正的筛选规则（匹配"所有标了这个注解的方法"）。
    //但 Spring 规定：这个规则要挂在一个方法上，
    //那个方法就是 pointCut()——它方法体为空，没有逻辑，只是给这条规则起个名字，类似"给规则贴个标签"
    @Pointcut(value = "@annotation(com.lixiaopu.aop.annotation.SaveCartRedisCacheToMysqlAnnotation)")
    public void pointCut() {
    }


    /**
     * cart RedisCache 延迟存库
     * @param result
     */
    //通知
    @AfterReturning(pointcut = "pointCut()", returning = "result")
    public void afterReturnSuccess(Result result) {
        if (Objects.isNull(result)) {
            return;
        }
        if (!Boolean.TRUE.equals(result.getSuccess())) {
            return;
        }
        long userId = CurrentHolder.getCurrentUser().getId();
        try {
            saveCartRedisCacheDelayJob.setUserIdToDelayedQueue(userId);
        } catch (Exception enqueueError) {
            log.error("购物车延迟同步任务提交失败，立即同步 MySQL，用户ID: {}", userId, enqueueError);
            try {
                cartService.syncCartToMysql(userId);
            } catch (Exception syncError) {
                syncError.addSuppressed(enqueueError);
                throw new IllegalStateException("购物车保存失败，请稍后重试", syncError);
            }
        }
    }
}
```

<a id="source-19"></a>
## 19. SaveCartRedisCacheDelayJob.java

源文件：[src/main/java/com/lixiaopu/job/delay/SaveCartRedisCacheDelayJob.java](../src/main/java/com/lixiaopu/job/delay/SaveCartRedisCacheDelayJob.java)

```java
package com.lixiaopu.job.delay;

import com.lixiaopu.infrastructure.redis.properties.RedisCacheTtlProperties;
import com.lixiaopu.service.CartService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;


@Component
@Slf4j
public class SaveCartRedisCacheDelayJob {

    /*
     * 1. 用户调用购物车接口：add /clear/deleteCartProduct /mergeCart
     * 2. Controller 方法打上 `@SaveCartRedisCacheToMysqlAnnotation`
     * 3. 接口执行成功，返回 `Result.success`；AOP 的 `@AfterReturning` 触发
     * 4. AOP 调用 `saveCartRedisCacheDelayJob.setUserIdToDelayedQueue(userId)`
     * 5. Job 内部：删除该用户在延迟队列中已存在的旧任务，重新把 userId 放入延迟队列，重新开始倒计时
     * 6. 👉 如果用户继续操作购物车：重复第 4、5 步，倒计时不断重置
     * 7. 用户不再操作购物车，等待延迟时间到期
     * 8. Redisson 自动把到期的 userId 转移到阻塞队列，消费线程 `take()` 获取 userId
     * 9. 执行 `cartService.syncCartToMysql(userId)`，Redis 购物车数据同步到 MySQL
     * 10. 任务完成，消费线程继续阻塞等待下一个到期任务
     */



    /**
     * 1
     * Job 泛指不在接口主线程同步执行，而是后台异步跑的任务，比如定时任务、延迟任务、异步消费任务。
     * 用户操作购物车（增 / 删 / 改 / 合并）
     * 并且接口成功返回 → AOP 切面把 userId 交给这个 Job → Job先删掉这个用户旧的延迟任务，
     * 再重新放入延迟队列（防抖重置倒计时）。
     * 等到延迟时间到期 → 后台线程取出 userId → 调用 `cartService.syncCartToMysql(userId)`，
     * 把 Redis 里该用户购物车全量写入 MySQL。
     * 2
     * 核心亮点：防抖重置倒计时
     * 用户短时间连续点加入购物车，每次都会移除旧任务，重新计时。
     * 比如延迟 30 分钟，用户每隔 1 分钟加一次商品，倒计时会不断重置；
     * 只有用户停止操作，等待设定的延迟时间后，才会执行一次同步 DB。避免频繁写库。
    * */

    //怎么解决依赖循环，common里的job包里的SaveCartRedisCacheDelayJob依赖 service，
    //common里的aspect文件里又注入了当前这个文件SaveCartRedisCacheDelayJob，也就是common依赖user？
    //可是user本来就依赖common，这不就是依赖循环了吗


    //- `@PostConstruct`：Bean 被 Spring 实例化完成之后自动执行一次。
    // 项目启动时，就初始化队列，并且开启一个后台线程，持续监听任务。
    //- Redisson 的 `RDelayedQueue`
    // 原理：消息先放到延迟队列，到期之后才会转移到 `RBlockingQueue`；
    // `take()` 阻塞拿元素，没有到期任务就卡住等待。

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private RedisCacheTtlProperties redisKeyTtlProperties;

    @Autowired
    private CartService cartService;

    @Autowired
    @Qualifier("saveCartRedisCacheToMysqlThreadPool")
    //`@Qualifier` 的作用：按 Bean 名称精确匹配。
    //`"saveCartRedisCacheToMysqlThreadPool"` 就是在配置类里定义线程池 Bean 时写的名字。
    private Executor threadPool;

    private RBlockingQueue<String> blockingQueue;
    private RDelayedQueue<String> delayedQueue;

    private static final String BLOCKING_QUEUE_NAME = "saveCartRedisCacheBlockingQueue";

    @PostConstruct
    //当前这个 Bean 的所有属性全部注入完成之后，自动执行这个 init 方法
    private void init() {
        // 1. 初始化队列
        this.blockingQueue = redissonClient.getBlockingQueue(BLOCKING_QUEUE_NAME);
        this.delayedQueue = redissonClient.getDelayedQueue(blockingQueue);

        // 2. 启动后台消费线程
        startConsumer();
    }

    /**
     * 将用户ID加入延迟队列
     * 逻辑：如果任务已存在，先移除再添加，实现倒计时重置（防抖）
     */
    public void setUserIdToDelayedQueue(long userId) {
        // 1. 防抖逻辑：移除已存在的旧任务
        delayedQueue.remove(String.valueOf(userId));

        // 2. 计算延迟时间（比缓存过期时间早 1 小时，确保同步时 Redis 还有数据）
        // 假设 redisKeyTtlProperties.getCartTtl() 单位是秒
        long delayInSeconds = redisKeyTtlProperties.getCartTtl() - 3600;

        // 安全检查，如果 TTL 设置过短，至少延迟 10 秒
        if (delayInSeconds < 0) {
            delayInSeconds = 10;
        }

        // 3. 添加新任务
        delayedQueue.offer(String.valueOf(userId), delayInSeconds, TimeUnit.SECONDS);
        log.info("用户 {} 的购物车同步任务已加入延迟队列，将在 {} 秒后执行", userId, delayInSeconds);
    }

    /**
     * 后台消费逻辑
     */
    private void startConsumer() {
        /*
        *把里面的 while 死循环丢到线程池里新开一个独立后台线程跑，
        *不和 Tomcat 接口线程抢资源。这个线程专门阻塞等着延迟队列消息，程序启动后就一直在后台活着。
        * */
        threadPool.execute(() -> {
            log.info("购物车延迟同步消费者已启动...");
            while (!Thread.currentThread().isInterrupted()) {
                //获取当前正在执行这行代码的线程对象,读取这个线程的ya中断标记（中断状态），返回布尔值
                //确保项目停止时，线程能正常退出，不会成为僵尸线程
                try {
                    // take() 是阻塞的，会一直等到有任务到期
                    long userId = Long.parseLong(blockingQueue.take());
                    log.info("收到购物车同步任务，用户ID: {}", userId);

                    // 执行同步逻辑
                    cartService.syncCartToMysql(userId);

                } catch (InterruptedException e) {
                    log.warn("购物车同步消费者线程被中断，停止运行");
                    Thread.currentThread().interrupt();
                    /*
                    * 1. 项目准备关闭，Spring 给这个消费者线程调用 `interrupt()`，打上中断标记 = true
                    * 2. 线程此时大概率卡在 `blockingQueue.take()` 阻塞等待任务
                    * 3. take 检测到中断信号，抛出`InterruptedException`
                    * 4. 进入异常 catch，打印日志，执行 `Thread.currentThread().interrupt()`（恢复中断标记，因为抛出这个异常会自动清空标记），然后 break 跳出 try
                    * 5. 回到 while 判断：`!isInterrupted()` → `!true` → false，循环终止
                    * 6. 线程执行结束，JVM 可以正常退出，不会残留后台线程
                    * */
                    break;
                } catch (Exception e) {
                    log.error("处理购物车同步任务时发生异常: ", e);
                }
            }
        });
    }
}
```

<a id="source-20"></a>
## 20. CartSyncExecutorConfig.java

源文件：[src/main/java/com/lixiaopu/infrastructure/thread/config/CartSyncExecutorConfig.java](../src/main/java/com/lixiaopu/infrastructure/thread/config/CartSyncExecutorConfig.java)

```java
package com.lixiaopu.infrastructure.thread.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CartSyncExecutorConfig {

    @Bean(name = "saveCartRedisCacheToMysqlThreadPool", destroyMethod = "shutdownNow")
    //`destroyMethod = "shutdownNow"`
    //Spring 容器销毁（项目停止）的时候，自动调用线程池的`shutdownNow()`方法，尝试停止里面正在运行的线程，释放资源。
    public ExecutorService cartSyncExecutor() {
        //创建一个单线程的线程池
        //这个池子里面，永远只维护 1 条工作线程，名字叫`cart-sync-consumer`
        //你往这个池子`execute()`提交任何任务，全部都交给这唯一一条线程串行执行
        //如果提交多个任务，任务会放在池子内部的队列排队，一个做完再跑下一个
        //重点：这个池子的 1 个线程，**只属于这个池子**，和 Tomcat 线程池不是同一个东西。
        return Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "cart-sync-consumer");
            thread.setDaemon(true);
            //设置为守护线程
            //用户线程：只要还有一个活着，JVM 就不会关闭。
            //守护线程：只是辅助后台干活；一旦所有用户线程结束，守护线程会被 JVM 直接粗暴终止，不管有没有做完。
            return thread;
        });//task是thread对象
    }
}
```

<a id="source-21"></a>
## 21. CurrentHolder.java

源文件：[src/main/java/com/lixiaopu/common/context/CurrentHolder.java](../src/main/java/com/lixiaopu/common/context/CurrentHolder.java)

```java
package com.lixiaopu.common.context;


import com.lixiaopu.common.result.UserInfo;

public class CurrentHolder {

    private static final ThreadLocal<UserInfo> CURRENT_USER = new ThreadLocal<>();
    public static void setCurrentUser(UserInfo userInfo){
        CURRENT_USER.set(userInfo);
    }
    public static  UserInfo getCurrentUser(){
        return CURRENT_USER.get();
    }

    public static void remove(){
        CURRENT_USER.remove();
    }
}
```

<a id="source-22"></a>
## 22. UserInfo.java

源文件：[src/main/java/com/lixiaopu/common/result/UserInfo.java](../src/main/java/com/lixiaopu/common/result/UserInfo.java)

```java
package com.lixiaopu.common.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserInfo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    /**
     * 用户 ID
     */
    private Long id;

    /**
     * 用户昵称
     */
    private String nickname;

    /**
     * 用户头像 URL
     */
    private String avatar;

    /**
     * 手机号码
     */
    private String phone;

    /**
     * 微信用户唯一标识
     */
    private String openid;



}
```

<a id="source-23"></a>
## 23. Result.java

源文件：[src/main/java/com/lixiaopu/common/result/Result.java](../src/main/java/com/lixiaopu/common/result/Result.java)

```java
package com.lixiaopu.common.result;

import lombok.Data;

@Data
public class Result {
    private Boolean success;      // 是否成功
    private Integer code;         // 状态码
    private String message;       // 描述信息
    private Object data;          // 数据，把T改成Object

    private Result() {}

    //链式setter
    public Result code(Integer code) {
        this.code = code;
        return this;
    }

    public Result message(String message) {
        this.message = message;
        return this;
    }

    public Result data(Object data) {
        this.data = data;
        return this;
    }

    public Result success(Boolean success) {
        this.success = success;
        return this;
    }

    public static Result success() {
        return build(ResultCode.SUCCESS, true, null);
    }

    public static Result success(Object data) {
        return build(ResultCode.SUCCESS, true, data);
    }

    //1.默认错误--系统异常
    public static Result error() {
        return build(ResultCode.ERROR, false, null);
    }

    //2.只传message，错误码不变，只想改提示信息
    public static Result error(String message) {
        return build(ResultCode.ERROR, false, message, null);
    }

    //3.指定不同的业务状态码
    public static Result error(ResultCode resultCode) {
        return build(resultCode, false, null);
    }

    //4.指定不同的业务状态码和提示信息
    public static Result error(ResultCode resultCode, String message) {
        return build(resultCode, false, message, null);
    }

    private static Result build(ResultCode rc, boolean success, Object data) {
        Result result = new Result();
        result.success = success;
        result.code = rc.getCode();
        result.message = rc.getMessage();
        result.data = data;
        return result;
    }

    private static Result build(ResultCode rc, boolean success, String customMessage, Object data) {
        Result result = new Result();
        result.success = success;
        result.code = rc.getCode();
        result.message = customMessage != null ? customMessage : rc.getMessage();
        result.data = data;
        return result;
    }
}
```

<a id="source-24"></a>
## 24. ResultCode.java

源文件：[src/main/java/com/lixiaopu/common/result/ResultCode.java](../src/main/java/com/lixiaopu/common/result/ResultCode.java)

```java
package com.lixiaopu.common.result;

import lombok.Getter;

/**
 * 业务状态码枚举
 */
@Getter
public enum ResultCode {

    SUCCESS(200, "操作成功"),
    ERROR(500, "系统异常"),
    UNAUTHORIZED(401, "未授权"),
    FORBIDDEN(403, "禁止访问"),
    VALIDATE_FAILED(404, "参数检验失败"),
    LOGIN_ERROR(20001, "用户名或密码错误"),
    USER_NOT_EXIST(20002, "用户不存在"),
    USER_EXIST(20003, "用户已存在"),

    NO_TOKEN(10001,"无 token"),
    ACCESS_TOKEN_EXPIRED(10002,"认证 token 过期"), //自动调用刷新接口
    REFRESH_TOKEN_EXPIRED(10003,"刷新 token 过期"),
    AUTHENTICATION_SIGNATURE_ERROR(10004,"认证签名错误/篡改");


    private final Integer code;
    private final String message;

    ResultCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
```

<a id="source-25"></a>
## 25. MessageConstant.java

源文件：[src/main/java/com/lixiaopu/common/constant/MessageConstant.java](../src/main/java/com/lixiaopu/common/constant/MessageConstant.java)

```java
package com.lixiaopu.common.constant;

public class MessageConstant {
    public static final String LOGIN_ERROR="用户名或密码错误";
    public static final String NO_ACCESS_TOKEN="用户未登录";
    public static final String TOKEN_EXPIRED="JWT 令牌解析失败(accessToken过期)";
    public static final String TOKEN_INVALID="JWT 令牌解析失败(accessToken签名错误,篡改)";
    public static final String TOKEN_PARSE_ERROR="JWT 令牌解析失败";
    public static final String REFRESH_TOKEN_EXPIRED_ERROR="登录已过期,请重新登录";
    public static final String GET_OPENID_ERROR="登录校验失败,请重新登录";
    public static final String PASSWORD_MODIFY_ERROR="密码修改失败,请重试";
    public static final String USER_NOT_LOGIN = "用户登录异常,请重新登录";
    public static final String WECHAT_CODE_EMPTY="微信登录失败,请重试";
    public static final String ACCOUNT_LOCKED = "账号被锁定";
    public static final String PERMISSION_DENIED="权限不足";
    public static final String SYSTEM_ERROR="服务器异常";
    public static final String NETWORK_ERROR="网络异常";
    public static final String INPUT_DATA_ERROR="输入数据不合法";
    public static final String ACCOUNT_NOT_FOUND = "账号不存在";
    public static final String ORDER_NOT_FOUND = "订单不存在";
    public static final String USER_NAME_EXISTS = "用户名已存在";
    public static final String USER_NAME_NOT_NULL = "用户名不能为空";
    public static final String UNKNOWN_ERROR = "未知错误";
    public static final String SQL_MESSAGE_SAVE_ERROR = "数据保存失败，请稍后重试";
    public static final String TOM_CAT_ERROR = "系统繁忙，请稍后重试";
    public static final String CART_NOT_EXIST_ERROR = "删除的购物车不存在";
    public static final String CONTENT_NOT_EXIST_ERROR = "删除的内容不存在";
    public static final String DELETE_ERROR = "删除失败,请重试";
    public static final String DATA_ERROR = "数据异常,请重试";
    public static final String JSON_CONVERT_ERROR =  "JSON 序列化异常";
    public static final String DATE_TIME_PARSE_ERROR="时间格式转换异常";
    public static final String HAVE_APPEND ="一条评价只允许追评一次,该评价已经追评过了";


}
```

<a id="source-26"></a>
## 26. CommonStatus.java

源文件：[src/main/java/com/lixiaopu/pojo/enums/CommonStatus.java](../src/main/java/com/lixiaopu/pojo/enums/CommonStatus.java)

```java
package com.lixiaopu.pojo.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

@Getter
public enum CommonStatus {
    ACTIVE("active",1,"启用"),
    INACTIVE("inactive",0,"禁用");
    @JsonValue
    private final String value;
    @EnumValue
    private final Integer number;

    private final String desc;


    CommonStatus(String value, Integer number, String desc) {
        this.value = value;
        this.number = number;
        this.desc = desc;
    }

    /**
     * 根据传递 number返回 value
     * @param number
     * @return
     */
    public static String getValueByNumber(Integer number){
        for (CommonStatus commonStatus : values()) {
            if (commonStatus.number==number){
                return commonStatus.value;
            }
        }
        throw new IllegalArgumentException("无效的CommonStatus.number:" + number);
    }


    /**
     * static 根据 value 返回枚举
     *
     * @param value
     * @return
     */
    public static CommonStatus getByValue(String value) {
        for (CommonStatus commonStatus : values()) {
            if (commonStatus.value.equals(value)) {
                return commonStatus;
            }
        }
        throw new IllegalArgumentException("无效的CommonStatus.value:" + value);
    }


    /**
     * 前端传字符串,自动转换为枚举
     *
     * @param value
     * @return
     */
    @JsonCreator
    public static CommonStatus fromValue(String value) {
        return getByValue(value);
    }




}
```

<a id="source-27"></a>
## 27. AuthFilter.java

源文件：[src/main/java/com/lixiaopu/security/filter/AuthFilter.java](../src/main/java/com/lixiaopu/security/filter/AuthFilter.java)

```java
package com.lixiaopu.security.filter;

import com.lixiaopu.common.exception.AuthException;
import com.lixiaopu.security.token.JwtAuthenticationToken;

import com.lixiaopu.common.context.AuthContext;
import com.lixiaopu.security.token.TokenService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lixiaopu.common.context.CurrentHolder;
import com.lixiaopu.common.result.Result;
import com.lixiaopu.common.result.ResultCode;
import com.lixiaopu.common.result.UserInfo;
import com.lixiaopu.mapper.SysUserMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.mgt.DefaultSecurityManager;
import org.apache.shiro.subject.Subject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

@Component
public class AuthFilter extends OncePerRequestFilter {
    @Autowired
    private  DefaultSecurityManager securityManager;
    @Autowired
    private  SysUserMapper userMapper;
    @Autowired
    private  ObjectMapper jsonMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
        throws ServletException,IOException {
        String path=request.getRequestURI();
        boolean admin=path.startsWith("/api/admin/");
        boolean protectedBuyer=path.startsWith("/api/user/") || path.equals("/api/cart") || path.startsWith("/api/cart/")
            || path.equals("/api/addresses") || path.startsWith("/api/addresses/")
            || path.equals("/api/address") || path.startsWith("/api/address/")
            || path.equals("/api/upload/image")
            || path.equals("/api/auth/logout") || path.equals("/api/auth/change-password");
        boolean publicAdmin=path.equals("/api/admin/auth/login") || path.equals("/api/admin/auth/refresh") || path.equals("/api/admin/auth/sms")
            || path.equals("/api/admin/auth/reset/verify") || path.equals("/api/admin/auth/reset/password");
        if ((!admin && !protectedBuyer) || (admin && publicAdmin)) { chain.doFilter(request,response);return; }
        try {
            String header=request.getHeader("Authorization");
            if (header==null || !header.startsWith("Bearer "))
                throw new AuthException(ResultCode.NO_TOKEN);

            Subject subject=new Subject.Builder(securityManager).buildSubject();

            subject.login(new JwtAuthenticationToken(header.substring(7),admin?"ADMIN":"BUYER"));
            TokenService.Principal principal=(TokenService.Principal)subject.getPrincipal();
            String required=admin?"admin:access":"buyer:access";
            if (!subject.isPermitted(required)) throw new AuthException(ResultCode.FORBIDDEN);
            if (path.equals("/api/admin/users/administrators") && !subject.hasRole("SYS_ADMIN"))
                throw new AuthException(ResultCode.FORBIDDEN);
            if (path.equals("/api/admin/users/administrators") && !subject.isPermitted("admin:create"))
                throw new AuthException(ResultCode.FORBIDDEN);
            if (path.matches("/api/admin/users/[0-9]+/disable") && !subject.isPermitted("user:disable"))
                throw new AuthException(ResultCode.FORBIDDEN);
            AuthContext.set(principal);
            var user=userMapper.selectById(principal.getUid());

            CurrentHolder.setCurrentUser(UserInfo.builder()
                    .id(user.getId())
                    .nickname(user.getNickname())
                    .avatar(user.getAvatar())
                    .phone(user.getPhone())
                    .openid(user.getOpenid())
                    .build());
            chain.doFilter(request,response);
        } catch (AuthException ex) {
            error(response,ex.getCode(),ex.getMessage());
        } catch (AuthenticationException ex) {
            error(response,ResultCode.UNAUTHORIZED,ResultCode.UNAUTHORIZED.getMessage());
        } finally {
            CurrentHolder.remove();AuthContext.clear();
        }
    }
    private void error(HttpServletResponse response,ResultCode code,String message) throws IOException {
        response.setStatus(code==ResultCode.FORBIDDEN?403:401);
        response.setContentType("application/json;charset=UTF-8");
        jsonMapper.writeValue(response.getWriter(),Result.error(code,message));
    }
}
```

<a id="source-28"></a>
## 28. AuthErrorHandler.java

源文件：[src/main/java/com/lixiaopu/security/handler/AuthErrorHandler.java](../src/main/java/com/lixiaopu/security/handler/AuthErrorHandler.java)

```java
package com.lixiaopu.security.handler;

import com.lixiaopu.common.exception.AuthException;

import com.lixiaopu.common.result.Result;
import com.lixiaopu.common.result.ResultCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AuthErrorHandler {
    @ExceptionHandler(AuthException.class)
    public ResponseEntity<Result> auth(AuthException ex) {
        int status=switch (ex.getCode()) {
            case FORBIDDEN -> 403;
            case UNAUTHORIZED,NO_TOKEN,ACCESS_TOKEN_EXPIRED,REFRESH_TOKEN_EXPIRED,LOGIN_ERROR -> 401;
            default -> 400;
        };
        return ResponseEntity.status(status).body(Result.error(ex.getCode(),ex.getMessage()));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result> invalid() {
        return ResponseEntity.badRequest().body(Result.error(ResultCode.VALIDATE_FAILED));
    }
}
```

<a id="source-29"></a>
## 29. LixiaopuApplication.java

源文件：[src/main/java/com/lixiaopu/LixiaopuApplication.java](../src/main/java/com/lixiaopu/LixiaopuApplication.java)

```java
package com.lixiaopu;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import java.util.Map;

/** 栗小铺启动入口。在 IDEA 中运行 main 方法即可启动。 */
@SpringBootApplication(scanBasePackages = {
        "com.lixiaopu.common", "com.lixiaopu.infrastructure", "com.lixiaopu.properties",
        "com.lixiaopu.security", "com.lixiaopu.controller.user", "com.lixiaopu.controller.tool",
        "com.lixiaopu.service", "com.lixiaopu.aop", "com.lixiaopu.job"
})
@MapperScan("com.lixiaopu.mapper")
public class LixiaopuApplication {
    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(LixiaopuApplication.class);
        application.setAdditionalProfiles("user");
        application.setDefaultProperties(Map.of(
                "server.port", "8081",
                "spring.web.resources.static-locations", "classpath:/static/"));
        application.run(args);
    }
}
```

<a id="source-30"></a>
## 30. AdminApplication.java

源文件：[src/main/java/com/lixiaopu/AdminApplication.java](../src/main/java/com/lixiaopu/AdminApplication.java)

```java
package com.lixiaopu;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import java.util.Map;

@SpringBootApplication(scanBasePackages = {
        "com.lixiaopu.common", "com.lixiaopu.infrastructure", "com.lixiaopu.properties",
        "com.lixiaopu.security", "com.lixiaopu.controller.admin", "com.lixiaopu.application",
        "com.lixiaopu.service"
})
@MapperScan("com.lixiaopu.mapper")
public class AdminApplication {
    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(AdminApplication.class);
        application.setAdditionalProfiles("admin");
        application.setDefaultProperties(Map.of(
                "server.port", "8082",
                "spring.web.resources.static-locations", "classpath:/admin-static/"));
        application.run(args);
    }
}
```

<a id="source-31"></a>
## 31. pom.xml

源文件：[pom.xml](../pom.xml)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.5.16</version>
    <relativePath/>
  </parent>

  <groupId>com.lixiaopu</groupId>
  <artifactId>lixiaopu</artifactId>
  <version>0.0.1-SNAPSHOT</version>
  <name>lixiaopu</name>
  <description>Buyer and admin applications in one Maven module</description>

  <properties>
    <java.version>17</java.version>
    <mybatis-plus.version>3.5.17</mybatis-plus.version>
    <hutool.version>5.8.47</hutool.version>
  </properties>

  <dependencies>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-jdbc</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-data-redis</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-aop</artifactId>
    </dependency>
    <dependency>
      <groupId>com.baomidou</groupId>
      <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
      <version>${mybatis-plus.version}</version>
    </dependency>
    <dependency>
      <groupId>com.baomidou</groupId>
      <artifactId>mybatis-plus-jsqlparser</artifactId>
      <version>${mybatis-plus.version}</version>
    </dependency>
    <dependency>
      <groupId>com.mysql</groupId>
      <artifactId>mysql-connector-j</artifactId>
      <scope>runtime</scope>
    </dependency>
    <dependency>
      <groupId>org.redisson</groupId>
      <artifactId>redisson-spring-boot-starter</artifactId>
      <version>3.31.0</version>
    </dependency>
    <dependency>
      <groupId>cn.hutool</groupId>
      <artifactId>hutool-all</artifactId>
      <version>${hutool.version}</version>
    </dependency>
    <dependency>
      <groupId>org.mindrot</groupId>
      <artifactId>jbcrypt</artifactId>
      <version>0.4</version>
    </dependency>
    <dependency>
      <groupId>org.apache.shiro</groupId>
      <artifactId>shiro-core</artifactId>
      <version>3.0.1</version>
    </dependency>
    <dependency>
      <groupId>org.projectlombok</groupId>
      <artifactId>lombok</artifactId>
      <optional>true</optional>
    </dependency>
    <dependency>
      <groupId>com.aliyun.oss</groupId>
      <artifactId>aliyun-sdk-oss</artifactId>
      <version>3.17.4</version>
    </dependency>
    <dependency>
      <groupId>com.fasterxml.jackson.datatype</groupId>
      <artifactId>jackson-datatype-jsr310</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
        <configuration>
          <mainClass>com.lixiaopu.LixiaopuApplication</mainClass>
        </configuration>
        <executions>
          <execution>
            <id>repackage-admin</id>
            <goals><goal>repackage</goal></goals>
            <configuration>
              <classifier>admin</classifier>
              <mainClass>com.lixiaopu.AdminApplication</mainClass>
            </configuration>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
</project>
```

<a id="source-schema"></a>
## 核心表建表 SQL（原文节选）

来源：[01-full-schema.sql](../src/main/resources/db/01-full-schema.sql)。只收录 sys_user、category、product、product_spec、cart；这是建表脚本，不是本次连接数据库导出的结构。

```sql
CREATE TABLE IF NOT EXISTS `sys_user` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(64) NULL,
  password VARCHAR(100) NULL,
  nickname VARCHAR(64) NOT NULL DEFAULT '',
  avatar VARCHAR(1024) NULL,
  openid VARCHAR(128) NULL,
  phone VARCHAR(20) NULL,
  user_type TINYINT NOT NULL DEFAULT 3,
  is_enable TINYINT NOT NULL DEFAULT 1,
  auth_version BIGINT NOT NULL DEFAULT 0,
  first_login_time DATETIME NULL,
  last_login_time DATETIME NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_sys_user_username(username),
  UNIQUE KEY uk_sys_user_openid(openid),
  UNIQUE KEY uk_sys_user_phone(phone),
  CONSTRAINT ck_sys_user_enable CHECK(is_enable IN(0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='栗小铺 sys_user';

CREATE TABLE IF NOT EXISTS `category` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(100) NOT NULL,
  parent_id BIGINT NOT NULL DEFAULT 0,
  icon_url VARCHAR(1024) NULL DEFAULT '/static/images/default-category.png',
  sort INT NOT NULL DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_category_parent_sort(parent_id,sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='栗小铺 category';

CREATE TABLE IF NOT EXISTS `product` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  category_id BIGINT NOT NULL,
  name VARCHAR(128) NOT NULL,
  sell_point VARCHAR(255) NULL,
  price DECIMAL(10,2) NOT NULL,
  enterprise_price DECIMAL(10,2) NULL,
  stock BIGINT NOT NULL DEFAULT 0,
  cover_image VARCHAR(1024) NULL,
  description LONGTEXT NULL,
  view_count BIGINT NOT NULL DEFAULT 0,
  sales_count BIGINT NOT NULL DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_product_category_status(category_id,status),
  KEY idx_product_name(name),
  FOREIGN KEY(category_id) REFERENCES category(id),
  CHECK(price>=0 AND stock>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='栗小铺 product';

CREATE TABLE IF NOT EXISTS `product_spec` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  product_id BIGINT NOT NULL,
  spec_text VARCHAR(255) NOT NULL,
  price DECIMAL(10,2) NOT NULL,
  enterprise_price DECIMAL(10,2) NULL,
  stock INT NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_spec_product(product_id),
  FOREIGN KEY(product_id) REFERENCES product(id),
  CHECK(price>=0 AND stock>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='栗小铺 product_spec';

CREATE TABLE IF NOT EXISTS `cart` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  spec_id BIGINT NULL,
  quantity INT NOT NULL DEFAULT 1,
  checked TINYINT NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_cart_user(user_id),
  KEY idx_cart_product(product_id),
  FOREIGN KEY(user_id) REFERENCES sys_user(id),
  FOREIGN KEY(product_id) REFERENCES product(id),
  FOREIGN KEY(spec_id) REFERENCES product_spec(id),
  CHECK(quantity>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='栗小铺 cart';

```
