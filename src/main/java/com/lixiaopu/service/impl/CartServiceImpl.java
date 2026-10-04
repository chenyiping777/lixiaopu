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
