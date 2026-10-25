package com.lixiaopu.common.util;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lixiaopu.mapper.ProductMapper;
import com.lixiaopu.pojo.entity.Product;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;
import java.util.UUID;


/**
 * 商品布隆过滤器工具类
 * 主要作用：
 * 1. 判断商品 ID 是否可能存在，防止大量不存在的商品 ID 请求穿透 Redis 缓存访问 MySQL。
 * 2. 支持新增商品 ID，实时更新布隆过滤器。
 * 3. 支持从 MySQL 全量重建布隆过滤器。
 * 4. 重建期间采用双写机制，避免新插入的商品 ID 遗漏。
 * 5. 重建完成后，切换到新过滤器，并删除旧过滤器。
 * 注意：
 * 布隆过滤器判断不存在：商品 ID 一定不存在（前提是数据同步正确）。
 * 布隆过滤器判断存在：商品 ID 可能存在，因为存在误判概率。
 * Redis Key：
 * product:bloom:active
 *     保存当前正在使用的布隆过滤器名称。
 * product:bloom:filter
 *     默认布隆过滤器名称。
 * product:bloom:filter:UUID
 *     每次全量重建时生成的新布隆过滤器名称。
 */
@Component
@RequiredArgsConstructor
public class BloomFilterUtils {

    /** 默认商品布隆过滤器名称 */
    private static final String PRODUCT_BLOOM_FILTER = "product:bloom:filter";

    /** 记录当前正在使用的布隆过滤器名称 */
    private static final String ACTIVE_KEY = "product:bloom:active";

    /** Redisson 客户端，用于操作 Redis 中的布隆过滤器 */
    private final RedissonClient redissonClient;

    /** 商品 Mapper，用于从 MySQL 查询商品 ID */
    private final ProductMapper productMapper;

    /**
     * 当前正在构建的新布隆过滤器。
     * 正常情况下为 null。
     * rebuild() 开始构建时指向新过滤器。
     * 构建完成或失败后恢复为 null。
     * 主要用途：当全量重建正在进行时，如果新增商品，
     * add() 不仅更新旧过滤器，还要更新新过滤器，
     * 防止新过滤器遗漏重建期间新增的商品。
     * 注意：这是当前 JVM 实例中的字段，
     * 不能自动协调其他应用实例。
     * 同一时刻对外只使用 1 个活跃布隆过滤器；重建过程中会短暂存在2个布隆过滤器（旧活跃 + 正在构建的新过滤器），
     * 重建完成后旧的会被删掉，又回到只有 1 个活跃过滤器。
     */
    private RBloomFilter<Long> buildingFilter;

    /**
     * 获取当前正在使用的商品布隆过滤器。
     *
     * 执行流程：
     * 1. 查询 Redis 中的 ACTIVE_KEY，获取当前过滤器名称。
     * 2. 根据名称获取布隆过滤器对象。
     * 3. 如果没有活动过滤器，或者过滤器已经丢失，则执行全量重建。
     * 4. 重建完成后重新获取新的活动过滤器。
     * 5. 返回过滤器对象。
     *
     * synchronized：
     * 使用当前 BloomFilterUtils 对象作为锁，
     * 保证同一个 JVM 实例内，同一时刻只有一个线程执行该方法。
     *
     * 注意：
     * synchronized 只能控制当前实例中的线程，
     * 不是 Redis 分布式锁。
     *
     * @return 当前活动的商品布隆过滤器
     */
    public synchronized RBloomFilter<Long> getProductBloomFilter() {

        // 1. 从 Redis 获取当前正在使用的布隆过滤器名称
        String name = redissonClient.<String>getBucket(ACTIVE_KEY).get();

        // 2. 如果 ACTIVE_KEY 不存在，则尝试使用默认名称
        // getBloomFilter() 获取的是过滤器操作对象，不代表 Redis 中已经存在过滤器
        RBloomFilter<Long> filter = redissonClient.getBloomFilter(
                name == null ? PRODUCT_BLOOM_FILTER : name
        );

        // 3. 没有活动过滤器记录，或者对应过滤器不存在
        if (name == null || !filter.isExists()) {

            // 执行全量重建
            // 当前线程等待 rebuild() 返回后才继续执行
            rebuild();

            // 4. 重建后重新读取 ACTIVE_KEY
            // 获取新过滤器的名称，并取得对应对象
            filter = redissonClient.getBloomFilter(
                    redissonClient.<String>getBucket(ACTIVE_KEY).get());
        }

        // 5. 返回当前活动的布隆过滤器
        return filter;
    }

    /**
     * 判断商品 ID 是否可能存在。
     *
     * @param id 商品 ID
     * @return true：可能存在；false：一定不存在（前提是过滤器数据完整）
     *
     * 使用场景：
     * 商品详情查询之前，先检查布隆过滤器。
     * 如果返回 false，直接结束查询，避免访问数据库。
     *
     * synchronized：
     * 与 add()、rebuild() 中的同步代码块使用同一把对象锁，
     * 控制当前实例内的并发访问。
     */
    public synchronized boolean contains(Long id) {

        // 获取当前活动过滤器，并判断商品 ID 是否可能存在
        return getProductBloomFilter().contains(id);
    }

    /**
     * 将商品 ID 添加到布隆过滤器。
     * 正常情况：只需要添加到当前活动过滤器。
     * 重建期间：
     * 需要同时添加到旧过滤器和正在构建的新过滤器。这种操作称为“双写”。
     * 为什么双写？
     * 假设新过滤器正在从数据库加载商品，
     * 此时管理员新增了一个商品。
     * 如果只写旧过滤器：新过滤器可能没有这个商品 ID，
     * 切换后就可能将真实存在的商品误判为不存在。
     * @param id 商品 ID
     * @return Redisson add() 的返回结果：
     *         true 表示过滤器中的对应位发生变化；
     *         false 不代表商品一定已经存在
     */
    public synchronized boolean add(Long id) {

        // 1. 将商品 ID 添加到当前正在使用的过滤器
        boolean added = getProductBloomFilter().add(id);

        // 2. 判断是否正在全量重建
        if (buildingFilter != null) {

            // 3. 同时向新过滤器添加 ID，防止构建期间新增的数据丢失
            buildingFilter.add(id);
        }

        // 返回向活动过滤器添加 ID 的结果
        return added;
    }

    /**
     * 从 MySQL 全量重建商品布隆过滤器。
     * 采用“构建新过滤器 -> 全量加载 -> 切换 -> 删除旧过滤器”的方式。
     * 与直接清空旧过滤器相比：
     * 构建期间旧过滤器仍然可以继续提供查询服务，
     * 不会因为全量加载而提前丢失旧过滤器中的数据。
     * 整体流程：
     * 1. 创建一个具有唯一名称的新过滤器。
     * 2. 设置新过滤器的预计元素数量及误判率。
     * 3. 将新过滤器赋值给 buildingFilter，开启双写。
     * 4. 查询数据库中所有商品 ID，写入新过滤器。
     * 5. 修改 ACTIVE_KEY，切换到新过滤器。
     * 6. 关闭双写，并删除旧过滤器。
     * 7. 如果重建失败，清理新过滤器。
     * 注意：
     * 这里没有给整个方法添加 synchronized，
     * 只对关键操作使用 synchronized(this)。
     * 这样可以避免长时间加载数据库时，
     * 始终占用当前对象的锁。
     */
    public void rebuild() {

        // 即将构建的新布隆过滤器
        RBloomFilter<Long> next;

        // ========== 第一阶段：初始化新过滤器 ==========

        synchronized (this) {

            // 如果当前实例已经有线程正在重建，则直接返回
            // 避免在同一个实例内重复创建多个新过滤器
            if (buildingFilter != null) {
                return;
            }

            // 使用 UUID 生成唯一名称
            // 例如：product:bloom:filter:550e8400-...
            // 避免覆盖当前正在使用的过滤器
            next = redissonClient.getBloomFilter(PRODUCT_BLOOM_FILTER + ":" + UUID.randomUUID());

            // 初始化布隆过滤器 selectCount(null)：
            // 查询数据库当前商品总数。
            // 2：预留一定容量，方便后续新增商品。
            // Math.max(10000L, ...)：
            // 预计元素数量至少为 10000。
            // 0.01：目标误判概率为 1%。
            // tryInit()：
            // 尝试初始化该布隆过滤器。
            next.tryInit(
                    Math.max(10000L, productMapper.selectCount(null) * 2),
                    0.01
            );

            // 标记正在构建的新过滤器
            // 从此刻开始，本实例的 add() 会尝试执行双写
            buildingFilter = next;
        }

        // 标记是否成功完成新旧过滤器切换
        // 默认 false，表示尚未完成切换
        boolean switched = false;

        try {

            // ========== 第二阶段：从数据库全量加载商品 ID ==========
            // 只查询商品 ID，避免加载商品名称、价格、描述等无关字段
            // 包含上架和下架商品。
            // 这样商品重新上架时，不需要因为上架操作重新加入过滤器。
            // 商品是否真正上架，仍然需要业务层自行判断。
            var products = productMapper.selectList(
                    Wrappers.<Product>lambdaQuery()
                            .select(Product::getId)
            );

            // 将数据库中所有商品 ID 逐个加入新过滤器
            for (Product product : products) {
                next.add(product.getId());
            }

            // ========== 第三阶段：切换新旧过滤器 ==========
            synchronized (this) {

                // 1. 读取当前活动过滤器名称
                // 保存下来，方便切换完成后删除旧过滤器
                String previous = redissonClient
                        .<String>getBucket(ACTIVE_KEY)
                        .get();

                // 2. 将 ACTIVE_KEY 修改为新过滤器名称
                // 后续查询会根据 ACTIVE_KEY 获取新过滤器
                redissonClient.<String>getBucket(ACTIVE_KEY)
                        .set(next.getName());

                // 3. 标记切换成功
                switched = true;

                // 4. 关闭双写
                // 后续新增商品只需要写当前活动过滤器
                buildingFilter = null;

                // 5. 删除旧过滤器，释放 Redis 存储空间
                // 如果之前没有活动过滤器名称，则删除默认过滤器
                redissonClient.getBloomFilter(
                        previous == null ? PRODUCT_BLOOM_FILTER : previous
                ).delete();
            }

        } finally {

            // ========== 第四阶段：异常处理与资源清理 ==========

            // 无论重建成功还是发生异常，finally 都会执行
            synchronized (this) {

                // 如果 buildingFilter 仍然指向本次新建的过滤器，
                // 说明本次构建尚未正常解除构建标记。
                //
                // 将其设置为 null，允许后续重新构建。
                if (buildingFilter == next) {
                    buildingFilter = null;
                }

                // 如果没有成功切换活动过滤器，
                // 则本次新建的过滤器不应该继续保留。
                //
                // 删除新过滤器，避免 Redis 中产生无用数据。
                if (!switched) {
                    next.delete();
                }
            }
        }
    }
}
