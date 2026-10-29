package com.lixiaopu.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lixiaopu.common.context.CurrentHolder;
import com.lixiaopu.common.result.CursorCommonEntity;
import com.lixiaopu.common.result.CursorCommonResult;
import com.lixiaopu.common.mapstruct.CopyMapper;
import com.lixiaopu.mapper.ProductMapper;
import com.lixiaopu.pojo.entity.Product;
import com.lixiaopu.pojo.enums.CommonStatus;
import com.lixiaopu.pojo.vo.SimpleProductVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.List;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 登录用户浏览历史：按最后浏览时间去重，保留最近100件和30天内的记录。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductHistoryService {
    static final long RETENTION_MILLIS = Duration.ofDays(30).toMillis();
    private static final int MAX_HISTORY_COUNT = 100;
    private final StringRedisTemplate redisTemplate;
    private final ProductMapper productMapper;
    private final CopyMapper copyMapper;

    /** 原子完成记录去重、过期清理、数量限制和 Key 续期。 */
    private static final DefaultRedisScript<Long> RECORD = new DefaultRedisScript<>("""
            redis.call('ZADD', KEYS[1], ARGV[1], ARGV[2])
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[3])
            redis.call('ZREMRANGEBYRANK', KEYS[1], 0, -(tonumber(ARGV[5]) + 1))
            redis.call('PEXPIRE', KEYS[1], ARGV[4])
            return 1
            """, Long.class);

    static String key(Long userId) {
        return "user:" + userId + ":product:browse";
    }

    //执行脚本,只由商品详情业务 getProductDetail() 调用
    //【写入 / 更新浏览记录】的 Lua 脚本，不是查询脚本，只做新增、去重、裁剪清理，不会读取数据返回浏览列表。
    public void record(Long productId) {
        var user = CurrentHolder.getCurrentUser();
        if (user == null || user.getId() == null) {
            return;
        }
        long now = System.currentTimeMillis();
        String userKey = key(user.getId());
        try {
            redisTemplate.execute(RECORD, List.of(userKey),
                    Long.toString(now), productId.toString(),
                    Long.toString(now - RETENTION_MILLIS), Long.toString(RETENTION_MILLIS),
                    Integer.toString(MAX_HISTORY_COUNT));
        } catch (RuntimeException ex) {
            // 浏览历史写入失败不影响商品详情展示。
            log.warn("记录商品浏览历史失败，userId={}, productId={}", user.getId(), productId, ex);
        }
    }

    //获取浏览历史
    public CursorCommonResult recent(CursorCommonEntity cursor) {
        // 获取每页查询条数
        Integer limit = cursor.getQuerySize();
        // 校验分页数量：不能为null，范围 1 ~ MAX_HISTORY_COUNT(100)
        if (limit == null || limit < 1 || limit > MAX_HISTORY_COUNT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "数量必须为1到" + MAX_HISTORY_COUNT);
        }
        // 浏览历史只支持【浏览时间倒序】这一种排序方式，不允许其他排序类型
        if (cursor.getSortType() != null && !"browseTimeDesc".equals(cursor.getSortType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "浏览历史仅支持browseTimeDesc排序");
        }
        Long cursorTime = null;
        // 只要传入了游标字段sortValue或者sortId，就必须校验两个游标参数完整性
        if (cursor.getSortValue() != null || cursor.getSortId() != null) {
            try {
                // 将游标携带的时间字符串转为时间戳Long
                cursorTime = Long.valueOf(cursor.getSortValue());
                // 校验：时间不能负数，商品ID必须存在且大于0
                if (cursorTime < 0 || cursor.getSortId() == null || cursor.getSortId() <= 0) {
                    throw new IllegalArgumentException();
                }
            } catch (IllegalArgumentException ex) {
                // 数字转换失败 / 参数不合法，抛出参数异常
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "游标需要有效的浏览时间和商品ID");
            }
        }

        // 服务层也校验身份，避免匿名调用出现空指针或访问无效用户的历史。
        var user = CurrentHolder.getCurrentUser();
        if (user == null || user.getId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
        // 组装当前用户浏览历史的Redis Key
        String userKey = key(user.getId());

        // Key的TTL只是整个集合的过期时间，不等于单条浏览记录有效期
        // 查询时兜底清理：删除ZSet中30天之前的浏览记录（应对只查询不新增浏览，Lua脚本长期不执行的场景）
        redisTemplate.opsForZSet().removeRangeByScore(userKey, Double.NEGATIVE_INFINITY,
                System.currentTimeMillis() - RETENTION_MILLIS);

        // 从ZSet中按score倒序取出全部浏览记录，最多取出MAX_HISTORY_COUNT条（0~99），同时带出score（浏览时间戳）
        //只按 score 排序的话：对相同 score 的那些条目，Java 自己排序 和 Redis 返回的顺序，有可能不一样
        var members = redisTemplate.opsForZSet().reverseRangeWithScores(userKey, 0, MAX_HISTORY_COUNT - 1);
        // Redis无浏览记录，直接返回空分页结果
        if (members == null || members.isEmpty()) {
            return page(List.of(), limit);
        }

        // 游标过滤阈值：游标携带的上一页最后一条记录的浏览时间
        Long afterTime = cursorTime;
        List<BrowseEntry> entries = members.stream()
                // 转换：redis member转productId，score转为浏览时间，封装成实体
                .map(member -> {
                            String valueStr = member.getValue();
                            Long id = Long.valueOf(valueStr);
                            long time = member.getScore().longValue();
                            return new BrowseEntry(id, time);
                        }
                )
                /**
                 * 内存重新排序
                 * 1. 优先浏览时间time降序（最新浏览排在前面）
                 * 2. 如果同一毫秒（time相同），按商品ID数字降序
                 * 原因：Redis ZSet在score相同时，member按照字典序排序，不是数字大小，会破坏业务排序规则
                 */
                .sorted(Comparator.comparingLong(BrowseEntry::time).reversed()
                        .thenComparing(Comparator.comparingLong(BrowseEntry::id).reversed()))
                /**
                 * 游标过滤：筛选【上一页游标之后】的数据
                 * 无游标（第一页）：afterTime=null，保留全部
                 * 有游标：
                 *  ① 当前记录时间 < 游标时间 → 符合（时间更早）
                 *  ② 时间相等 && 当前商品id < 游标商品id → 同毫秒内继续向下翻页
                 */
                .filter(entry -> afterTime == null || entry.time() < afterTime
                        || (entry.time() == afterTime && entry.id() < cursor.getSortId()))
                .toList();

        // 游标过滤后没有剩余浏览记录，直接返回空结果
        if (entries.isEmpty()) return page(List.of(), limit);

        // 提取过滤后的所有商品ID，用于批量查询数据库商品信息
        List<Long> ids = entries.stream().map(BrowseEntry::id).toList();

        // 批量查询数据库，只查询状态为上架ACTIVE的商品，转Map方便快速查找
        // 目的：过滤Redis中残留但已下架/删除的商品，防止脏数据展示
        Map<Long, Product> productMap = productMapper.selectList(Wrappers.<Product>lambdaQuery()
                        .in(Product::getId, ids).eq(Product::getStatus, CommonStatus.ACTIVE.getNumber()))
                .stream().collect(Collectors.toMap(Product::getId, Function.identity()));

        // 过滤：只保留数据库中存在且上架的商品；limit+1技巧，多取1条用来判断是否存在下一页
        // 注意：是先过滤下架商品之后，再limit+1，避免误判分页结束标记
        List<BrowseEntry> valid = entries.stream().filter(entry -> productMap.containsKey(entry.id()))
                .limit(limit + 1L).toList();

        // 构建分页游标信息、是否末尾标记
        CursorCommonResult result = page(valid, limit);
        // 截取limit条，将entity转为VO，设置返回列表
        result.setList(valid.stream().limit(limit).map(entry -> productMap.get(entry.id()))
                .map(copyMapper::productToSimpleProductVO).toList());
        return result;
    }


    //浏览记录
    private record BrowseEntry(long id, long time) { }

    //游标分页
    private CursorCommonResult page(List<BrowseEntry> entries, int size) {
        CursorCommonEntity next = null;
        if (!entries.isEmpty()) {
            BrowseEntry last = entries.get(Math.min(entries.size(), size) - 1);
            // 构造下一页游标对象，返回给前端，前端下拉加载时携带这个游标请求接口
            next = CursorCommonEntity
                    .builder()
                    .sortType("browseTimeDesc")
                    .sortValue(Long.toString(last.time()))
                    .sortId(last.id())
                    .querySize(size)
                    .build();
        }
        // 组装返回结果
        return CursorCommonResult
                .builder()
                .list(List.of())// 当前页数据列表（这里示例是空，实际业务填查询出来的VO列表）
                .cursorCommonEntity(next)      // 下一页游标，前端拿到，下一次请求携带
                .isEnd(entries.size() <= size) // 判断是否到底：剩下的数据条数 ≤ 每页size，代表没有更多数据了
                .build();
    }
}
