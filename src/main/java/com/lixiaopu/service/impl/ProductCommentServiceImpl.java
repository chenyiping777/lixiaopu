package com.lixiaopu.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.lixiaopu.common.constant.DataConstant;
import com.lixiaopu.common.constant.DatePatternConstants;
import com.lixiaopu.common.constant.MessageConstant;
import com.lixiaopu.common.context.CurrentHolder;
import com.lixiaopu.common.exception.EmptyObjectException;
import com.lixiaopu.common.mapstruct.CopyMapper;
import com.lixiaopu.common.result.CursorCommonEntity;
import com.lixiaopu.common.result.CursorCommonResult;
import com.lixiaopu.common.result.Result;
import com.lixiaopu.common.util.BloomFilterUtils;
import com.lixiaopu.infrastructure.mp.util.MyBatisBatchExecutor;
import com.lixiaopu.infrastructure.redis.connect.RedisConnector;
import com.lixiaopu.infrastructure.redis.connect.StringRedisConnector;
import com.lixiaopu.infrastructure.redis.generator.RedisKeyGenerator;
import com.lixiaopu.infrastructure.redis.properties.RedisCacheTtlProperties;
import com.lixiaopu.infrastructure.rocketmq.constant.MqOrderConstant;
import com.lixiaopu.infrastructure.rocketmq.consumer.OrderStatusConsumer;
import com.lixiaopu.mapper.ProductCommentAppendMapper;
import com.lixiaopu.mapper.ProductCommentLikeMapper;
import com.lixiaopu.mapper.ProductCommentMapper;
import com.lixiaopu.pojo.dto.AppendProductFirstCommentDTO;
import com.lixiaopu.pojo.dto.FirstProductCommentDTO;
import com.lixiaopu.pojo.dto.SecondProductCommentDTO;
import com.lixiaopu.pojo.entity.ProductComment;
import com.lixiaopu.pojo.entity.ProductCommentAppend;
import com.lixiaopu.pojo.entity.ProductCommentLike;
import com.lixiaopu.pojo.enums.OrderStatusEnum;
import com.lixiaopu.pojo.vo.ProductAppendCommentVO;
import com.lixiaopu.service.OrderService;
import com.lixiaopu.service.ProductCommentService;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class ProductCommentServiceImpl extends ServiceImpl<ProductCommentMapper, ProductComment>
        implements ProductCommentService {

    @Autowired(required = false)
    private RocketMQTemplate rocketMQTemplate;
    @Autowired
    private OrderService orderService;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private CopyMapper copyMapper;

    @Autowired
    private ProductCommentAppendMapper productCommentAppendMapper;

    @Autowired
    private ProductCommentLikeMapper productCommentLikeMapper;

    @Autowired
    private RedisCacheTtlProperties redisCacheTtlProperties;

    @Autowired
    private MyBatisBatchExecutor myBatisBatchExecutor;

    @Autowired
    private BloomFilterUtils bloomFilterUtils;

    // 评论不存在时缓存 id=0 的空对象，让后续请求可以识别“已经查过，但没有数据”。
    private final static ProductComment emptyProductComment = ProductComment
            .builder()
            .id(DataConstant.ZERO_LONG)
            .build();

    // 追评使用独立的空对象标记，查询时根据 id=0 过滤。
    private final static ProductCommentAppend emptyProductCommentAppend = ProductCommentAppend
            .builder()
            .id(DataConstant.ZERO_LONG)
            .build();

    // -1 表示无效的评论数缓存；0 表示商品确实没有评论，两者含义不同。
    private final static String emptyProductCommentCount = "-1";

    /**
     * 保存商品一级评价（买家首次评价，商品主评价，parentId=0）
     *
     * @param firstProductCommentDTO 一级评价入参DTO
     * @return Result 统一返回结果
     */
    @Override
    public Result saveProductFirstComment(FirstProductCommentDTO firstProductCommentDTO) {
        // 入参判空校验，DTO为空返回网络异常
        if (Objects.isNull(firstProductCommentDTO)) {
            return Result.error(MessageConstant.NETWORK_ERROR);
        }
        // 1. 从登录上下文取得用户 id，评论归属以当前登录用户为准。
        long userId = CurrentHolder.getCurrentUser().getId();
        // DTO转换为数据库实体类ProductComment
        ProductComment productComment = copyMapper
                .firstProductCommentDTOToProductComment(firstProductCommentDTO);

        // 2. parentId=0 表示一级评论（一级评价），isBuyer=1标记为买家评价。
        // testIsAnonymous：匿名处理，匿名则替换昵称和头像；链式调用继续填充基础字段
        testIsAnonymous(productComment)
                .setParentId(DataConstant.ZERO_LONG)
                .setUserId(userId)
                .setIsBuyer(1);

        // 插入一级评价记录到数据库
        boolean isSuccess = save(productComment);
        if (!isSuccess) {
            // 数据库保存失败，返回异常
            return Result.error(MessageConstant.TOM_CAT_ERROR);
        }

        // 3. 通过 RocketMQ 发送消息通知订单消费者，将订单状态更新为【已评价】
        String destination = MqOrderConstant.TOPIC_ORDER + ":" + MqOrderConstant.TAG_ORDER_STATUS;

        Map<String, Object> mqMessageMap = new HashMap<>(2);
        mqMessageMap.put(OrderStatusConsumer.ORDER_NO, firstProductCommentDTO.getOrderNo());
        mqMessageMap.put(OrderStatusConsumer.ORDER_STATUS_ENUM, OrderStatusEnum.EVALUATED.getValue());
        // 如果RocketMQ模板可用，发送MQ消息，异步更新订单状态
        if (rocketMQTemplate != null) {
            rocketMQTemplate.convertAndSend(destination, mqMessageMap);
        } else {
            // MQ不可用时，降级方案：直接调用服务同步更新订单状态为已评价
            orderService.updateOrderStatus(null, firstProductCommentDTO.getOrderNo(), OrderStatusEnum.EVALUATED);
        }

        // 删除该商品评论总数的Redis缓存，触发下一次查询重新统计，保证缓存数据与数据库一致
        StringRedisConnector.delete(RedisKeyGenerator.productCommentCount(Long.valueOf(firstProductCommentDTO.getProductId())));

        return Result.success();
    }

    /**
     * 判断用户是否匿名,进行匿名处理
     * 匿名评论替换展示用的昵称和头像，用户 id 仍保留，用于判断评论归属。
     *
     * @param productComment 待处理的评论实体
     * @return 处理后的原评论实体，便于继续链式设置字段
     */
    private ProductComment testIsAnonymous(ProductComment productComment) {
        int isAnonymous = productComment.getIsAnonymous();
        // 如果标记为匿名评价，替换昵称和头像为匿名默认值；userId底层保留不变，用于权限、归属校验
        if (isAnonymous == 1) {
            productComment
                    .setUserNickname(DataConstant.ANONYMOUS_NICKNAME)
                    .setUserAvatar(DataConstant.DEFAULT_AVATAR);
        }
        return productComment;
    }


    /**
     * 保存二级评论（对一级评价的回复评论）
     *
     * @param secondProductCommentDTO 二级评论入参DTO
     * @return Result 统一返回结果
     */
    @Override
    public Result saveProductSecondComment(SecondProductCommentDTO secondProductCommentDTO) {
        // 入参判空
        if (Objects.isNull(secondProductCommentDTO)) {
            return Result.error(MessageConstant.NETWORK_ERROR);
        }
        // 获取当前登录用户ID（回复评论的作者）
        long userId = CurrentHolder.getCurrentUser().getId();
        // DTO转为数据库实体对象
        ProductComment productComment = copyMapper.secondProductCommentDTOToProductComment(secondProductCommentDTO);
        // parentCommentId：父评论ID，这里是一级评价的ID
        Long parentCommentId = productComment.getParentId();
        // 构造一级评价的Redis缓存key
        String firstCommentKey = RedisKeyGenerator.firstCommentKey(parentCommentId);
        // 父评论ID不能为空
        if (Objects.isNull(parentCommentId)) {
            return Result.error(MessageConstant.DATA_ERROR);
        }
        // 从Redis读取父评论（这条二级评论所回复的一级评价）
        ProductComment firstProductComment = RedisConnector.getHashObject(firstCommentKey, ProductComment.class);
        // 缓存未命中，则查询数据库，并且回写Redis缓存
        firstProductComment = getProductCommentIfRedisCacheNull(firstProductComment, parentCommentId, firstCommentKey);
        // 空标记过滤：如果查到的是占位空对象，说明一级评价不存在
        if (firstProductComment.getId().equals(DataConstant.ZERO_LONG)) {
            return Result.error(MessageConstant.DATA_ERROR);
        }
        // 合法性校验：
        // 1.父评论必须是一级评价（一级评价parentId固定=0），不能回复二级评论（不支持回复回复）
        // 2.二级评论的商品ID，必须和父一级评价商品ID一致，不能跨商品回复
        if (firstProductComment.getParentId() != 0L
                || !firstProductComment.getProductId().equals(productComment.getProductId())) {
            return Result.error(MessageConstant.DATA_ERROR);
        }
        // 获取父一级评价作者的userId
        Long firstProductCommentUserId = firstProductComment.getUserId();
        // 判断：回复人 == 这条一级评价的作者本人，则标记isBuyer=1
        if (firstProductCommentUserId.equals(Long.valueOf(userId))) {
            productComment.setIsBuyer(1);
        }
        // 匿名处理 + 设置评论作者userId
        testIsAnonymous(productComment)
                .setUserId(Long.valueOf(userId));
        // 保存二级评论入库
        boolean isSuccess = save(productComment);
        if (!isSuccess) {
            return Result.error(MessageConstant.TOM_CAT_ERROR);
        }
        // 删除商品评论总数的Redis缓存，让评论计数缓存刷新
        StringRedisConnector.delete(RedisKeyGenerator.productCommentCount(Long.valueOf(secondProductCommentDTO.getProductId())));
        return Result.success();
    }


    /**
     * Redis 未命中时，从 MySQL 加载一级评论并回填缓存
     * 缓存已命中就直接返回；数据库也没有评论时写入空对象并抛出异常。
     * 调用方还需要检查 id=0，识别之前缓存的空对象。
     *
     * @param firstProductComment Redis 查询结果，可以为 null
     * @param parentCommentId     待查询的一级评论 id
     * @param firstCommentKey     一级评论在 Redis 中的 Hash key
     * @return 缓存或数据库中的评论实体
     */
    private ProductComment getProductCommentIfRedisCacheNull(ProductComment firstProductComment, Long parentCommentId, String firstCommentKey) {
        if (Objects.isNull(firstProductComment)) {
            firstProductComment = lambdaQuery()
                    .eq(ProductComment::getId, parentCommentId)
                    .one();
            //缓存空对象
            if (Objects.isNull(firstProductComment)) {
                RedisConnector.setHashObject(firstCommentKey, emptyProductComment);
                throw new EmptyObjectException(MessageConstant.DATA_ERROR);
            }
            RedisConnector.setHashObject(firstCommentKey, firstProductComment);
            RedisConnector.expire(firstCommentKey, redisCacheTtlProperties.getProductFirstCommentTtl(), TimeUnit.SECONDS);

        }
        return firstProductComment;
    }

    /**
     * 对一级评论追加评价
     * 根据订单找到原评论，检查关联信息和追评标记后，补全追评的商品、规格和用户信息。
     * 在同一事务中插入追评、更新原评论的追评标记，再清除一级评论缓存并发送订单已追评消息。
     *
     * @param appendProductFirstCommentDTO 订单、商品和追评内容
     * @return 追评保存结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result appendProductFirstComment(AppendProductFirstCommentDTO appendProductFirstCommentDTO) {
        long userId = CurrentHolder.getCurrentUser().getId();
        // 1. 根据订单号找到原评论，再读取对应的一级评论缓存。
        ProductComment productComment = lambdaQuery()
                .eq(ProductComment::getOrderNo, appendProductFirstCommentDTO.getOrderNo())
                .one();
        if (productComment == null) {
            return Result.error(MessageConstant.DATA_ERROR);
        }
        ProductCommentAppend productCommentAppend = copyMapper.appendProductFirstCommentDTOToProductCommentAppend(appendProductFirstCommentDTO);
        Long commentId = productComment.getId();
        String firstCommentKey = RedisKeyGenerator.firstCommentKey(commentId);
        ProductComment firstProductComment = RedisConnector.getHashObject(firstCommentKey, ProductComment.class);
        firstProductComment = getProductCommentIfRedisCacheNull(firstProductComment, commentId, firstCommentKey);
        //空对象过滤
        if (firstProductComment.getId().equals(DataConstant.ZERO_LONG)) {
            return Result.error(MessageConstant.DATA_ERROR);
        }
        //评论伪造过滤
        if (!firstProductComment.getProductId().equals(Long.valueOf(appendProductFirstCommentDTO.getProductId()))
                || !firstProductComment.getUserId().equals(userId)
                || firstProductComment.getParentId() != 0L) {
            return Result.error(MessageConstant.DATA_ERROR);
        }
        //已经追评过滤
        if (firstProductComment.getIsAppendComment() == 1) {
            return Result.error(MessageConstant.HAVE_APPEND);
        }
        // 2. 商品、规格、订单信息从原评论补全，保持追评与原评论关联。
        productCommentAppend
                .setProductId(firstProductComment.getProductId())
                .setProductSpecId(firstProductComment.getProductSpecId())
                .setOrderNo(firstProductComment.getOrderNo())
                .setUserId(Long.valueOf(userId))
                .setCommentId(commentId);
        // 3. 批量执行“插入追评 + 更新原评论标记”，事务异常时一起回滚。
        // 两个 Mapper 都从当前批处理 SqlSession 获取，多条 SQL 仍按顺序执行。
        myBatisBatchExecutor.executeBatch(sqlSession -> {
            ProductCommentAppendMapper batchAppendMapper = sqlSession.getMapper(ProductCommentAppendMapper.class);
            ProductCommentMapper batchCommentMapper = sqlSession.getMapper(ProductCommentMapper.class);
            batchAppendMapper.insert(productCommentAppend);
            LambdaUpdateWrapper<ProductComment> updateWrapper = new LambdaUpdateWrapper<ProductComment>()
                    .eq(ProductComment::getId, commentId)
                    .set(ProductComment::getIsAppendComment, 1);
            batchCommentMapper.update(updateWrapper);
            return null;
        });
        // 4. 原评论的追评标记已经改变，删除缓存，让下次查询读取更新后的数据。
        RedisConnector.delete(firstCommentKey);
        RedisConnector.delete(RedisKeyGenerator.appendCommentKey(commentId));
        String destination = MqOrderConstant.TOPIC_ORDER + ":" + MqOrderConstant.TAG_ORDER_STATUS;
        HashMap<String, Object> mqMessageMap = new HashMap<>(2);
        mqMessageMap.put(OrderStatusConsumer.ORDER_NO, appendProductFirstCommentDTO.getOrderNo());
        mqMessageMap.put(OrderStatusConsumer.ORDER_STATUS_ENUM, OrderStatusEnum.REVIEWED.getValue());
        if (rocketMQTemplate != null) rocketMQTemplate.convertAndSend(destination, mqMessageMap);
        else orderService.updateOrderStatus(null, appendProductFirstCommentDTO.getOrderNo(), OrderStatusEnum.REVIEWED);
        return Result.success();
    }

    /**
     * 游标分类查询一级评论
     * <p>
     * 核心：使用【创建时间 + 评论ID】复合游标实现keyset分页，替代offset分页，解决大偏移量分页性能问题，避免新增/删除数据导致的重复、漏数据。
     * 流程：
     * 1. 校验商品ID格式，布隆过滤器快速过滤一定不存在的商品；
     * 2. 首次查询不传游标，后续查询由前端同时传回时间和评论ID；
     * 3. 解析游标时间，限制单次查询条数区间[1,20]；
     * 4. 根据分类值直接追加好评或追评条件，固定只查询一级评论 parentId=0；
     * 5. 非首次查询，拼接游标条件：时间小于游标时间 OR (时间等于游标时间 AND ID小于游标ID)；
     * 6. 按创建时间、ID倒序查询，关闭count统计提升性能；
     * 7. 填充当前登录用户点赞状态；优先读取Redis缓存点赞数，缓存不存在则保留DB原始点赞数；
     * 8. DO转VO，封装下一页游标信息返回。
     *
     * @param cursorCommonEntity 游标分页实体：包含分类类型、游标时间sortValue、游标ID sortId、查询条数
     * @param productId          商品id（字符串类型）
     * @return 分页结果：评论VO列表 + 下一页游标
     */
    @Override
    public Result getProductCommentBySortType(
            CursorCommonEntity cursorCommonEntity, String productId) {

        Long productIdLong;
        try {
            productIdLong = Long.valueOf(productId);
        } catch (NumberFormatException e) {
            return Result.error(MessageConstant.DATA_ERROR);
        }

        if (!bloomFilterUtils.contains(productIdLong)) {
            return Result.error(MessageConstant.DATA_ERROR);
        }

        // 游标时间字符串、游标评论ID
        String cursorTime = cursorCommonEntity.getSortValue();
        Long sortId = cursorCommonEntity.getSortId();

        // 查询条数保护：默认10条，限制最小1、最大20，防止前端传入超大数量压垮DB/Redis
        Integer requestedSize = cursorCommonEntity.getQuerySize();
        int querySize = requestedSize == null ? 10 : Math.max(1, Math.min(requestedSize, 20));

        //按商品查一级评论，支持 3 种筛选（默认全部 / 好评 / 追评），
        //用「创建时间 + 评论 ID」游标翻页
        //最后补点赞信息，返回下一页游标。
        LambdaQueryChainWrapper<ProductComment> wrapper = lambdaQuery()
                .eq(ProductComment::getProductId, productIdLong)
                .eq(ProductComment::getParentId, 0);

        // 分类筛选：全部、好评、追评。
        String sortTypeStr = cursorCommonEntity.getSortType();
        //- 前端传 `isGoodReview` → 追加条件 `is_good_review =1`，只查好评
        //- 前端传 `isAppendComment` → 追加条件 `is_append_comment =1`，只查追评
        //- 前端传 `default` → 不追加任何筛选条件，查全部一级评论
        //- 前端传别的字符串（随便乱传的值）→ 直接返回参数错误
        if ("isGoodReview".equals(sortTypeStr)) {
            wrapper.eq(ProductComment::getIsGoodReview, 1);
        } else if ("isAppendComment".equals(sortTypeStr)) {
            wrapper.eq(ProductComment::getIsAppendComment, 1);
        } else if (!"default".equals(sortTypeStr)) {
            return Result.error(MessageConstant.DATA_ERROR);
        }

        // 首次查询不带游标；后续查询使用上一页最后一条的时间和 ID。
        if (StringUtils.isNotBlank(cursorTime)) {
            //把前端传过来的字符串格式的时间（cursorTime），转换成 Java 的 `LocalDateTime` 对象；
            //如果字符串格式不对（不是约定好的时间格式），直接返回时间解析错误给前端。
            LocalDateTime cursorCreateTime;
            try {
                cursorCreateTime = LocalDateTime.parse(
                        cursorTime, DatePatternConstants.NORMAL_DATETIME_FORMATTER);
            } catch (DateTimeParseException e) {
                return Result.error(MessageConstant.DATE_TIME_PARSE_ERROR);
            }
            /*
            *AND (
                    create_time < ?
                    OR ( create_time = ? AND id < ? )
                  )
            * */
            wrapper.and(w -> w
                    .lt(ProductComment::getCreateTime, cursorCreateTime)
                    .or(w2 -> w2
                            .eq(ProductComment::getCreateTime, cursorCreateTime)
                            .lt(ProductComment::getId, sortId)));
        }

        List<ProductComment> comments = wrapper
                .orderByDesc(ProductComment::getCreateTime)
                //按创建时间倒序，新评论排在前面
                .orderByDesc(ProductComment::getId)//时间相同的时候，按主键 id倒序，id 大的排前面
                .page(new Page<>(1, querySize, false))
                //- 第 1 个参数`1`：只是 MP 要求的占位，游标分页这里没有 offset，不起页码偏移作用
                //第 2 个参数`querySize`：限制本次最多查多少条，最终 SQL 末尾加上 `LIMIT querySize`
                //第 3 个参数`false`：关闭 count (*) 统计，不去查询总共有多少条评论，提升性能
                //MyBatisPlus 执行分页查询，返回 Page 对象
                .getRecords();//从 Page 对象取出查询出来的评论实体列表（DO），赋值给 comments

        if (!CollectionUtils.isEmpty(comments)) {
            setProductCommentIsLike(comments);//给评论设置 `isLike`（布尔值：当前登录用户有没有点赞这条评论）
            //1. 取出所有评论 id
            //2. 根据当前登录用户 id，批量去点赞表查询：该用户对这些评论哪些点过赞
            //3. 循环给每一条 `ProductComment` 对象填充 `isLike`（布尔标记：true = 已点赞，false = 未点赞）
            // 作用：给前端展示【我有没有给这条评论点赞】的状态，是当前登录人维度，不是点赞总数。
            for (ProductComment comment : comments) {
                Integer likeCount = RedisConnector.getHashField(
                        RedisKeyGenerator.firstCommentKey(comment.getId()),
                        ProductComment.Fields.likeCount,
                        Integer.class);

                if (likeCount != null) {
                    //Redis 里的是实时最新点赞数。优先读 Redis，Redis 拿不到，才保留数据库的值。
                    comment.setLikeCount(likeCount);//给评论设置 `likeCount`（数字：这条评论总共有多少人点赞）
                }
            }
        }

        return getCursorCommonResult(cursorCommonEntity, comments,
                comment -> copyMapper.productCommentToProductFirstCommentVO(comment));
    }


    /**
     * 将评论列表转换为 VO，并封装游标查询结果
     * 查询结果为空时返回 isEnd=true；有数据时取最后一条评论更新游标。
     * 一级、二级评论共用这个方法，由调用方传入各自的 MapStruct 转换方法。
     *
     * @param cursorCommonEntity 本次查询的游标对象，会更新为最后一条评论的位置
     * @param productCommentList 数据库查出的评论列表
     * @param copyMapperFunction 评论实体转换为 VO 的方法
     * @param <T>                转换后的评论 VO 类型，由传入的映射方法推断
     * @return 评论 VO 列表、游标或查询结束标记
     */

    private <T> Result getCursorCommonResult(CursorCommonEntity cursorCommonEntity,
                                             List<ProductComment> productCommentList,
                                             Function<ProductComment, T> copyMapperFunction) {
        if (productCommentList.isEmpty()) {
            return Result.success(CursorCommonResult.builder().isEnd(true).build());
        }
        ProductComment productComment = productCommentList.get(productCommentList.size() - 1);
        List<T> resultList = productCommentList.stream().map(copyMapperFunction).toList();
        // 下一次查询从本页最后一条评论继续，时间和 id 必须一起传回前端。
        cursorCommonEntity
                .setSortId(productComment.getId())
                .setSortValue(productComment.getCreateTime().format(DatePatternConstants.NORMAL_DATETIME_FORMATTER));
        CursorCommonResult cursorResult = CursorCommonResult.builder()
                .cursorCommonEntity(cursorCommonEntity)
                .list(resultList)
                .build();
        return Result.success(cursorResult);
    }

    /**
     * 根据指定一级评论 id 查询二级评论
     * 按 parentId 筛选回复，使用创建时间和 id 向后翻页，再补全点赞信息并转换为二级评论 VO。
     *
     * @param firstCommentId     所关联的一级评论 id
     * @param cursorCommonEntity 上一页游标和本次查询条数
     * @return 二级评论列表和下一次查询使用的游标
     */
    @Override
    public Result getSecondComment(String firstCommentId, CursorCommonEntity cursorCommonEntity) {
        String endCommentCreateTimeText = cursorCommonEntity.getSortValue();
        LocalDateTime endCommentCreateTime;
        if (StringUtils.isNotBlank(endCommentCreateTimeText)) {
            try {
                endCommentCreateTime = LocalDateTime.parse(cursorCommonEntity.getSortValue(), DatePatternConstants.NORMAL_DATETIME_FORMATTER);
            } catch (DateTimeParseException e) {
                log.error(MessageConstant.DATE_TIME_PARSE_ERROR);
                return Result.error(MessageConstant.DATE_TIME_PARSE_ERROR);
            }
        } else {
            endCommentCreateTime = LocalDateTime.now();
        }
        Long endCommentId = cursorCommonEntity.getSortId();
        Integer querySize = cursorCommonEntity.getQuerySize();
        // parentId 限定回复归属，时间 + id 限定本次查询从哪里继续。
        Page<ProductComment> pageResult = lambdaQuery()
                .eq(ProductComment::getParentId, firstCommentId)
                .and(wrapper ->
                        wrapper.lt(ProductComment::getCreateTime, endCommentCreateTime)
                                .or(wrapper2 -> {
                                    wrapper2.eq(ProductComment::getCreateTime, endCommentCreateTime)
                                            .lt(ProductComment::getId, endCommentId);
                                })
                )
                .orderByDesc(ProductComment::getCreateTime)
                .orderByDesc(ProductComment::getId)
                .page(new Page<>(1, querySize));
        List<ProductComment> productCommentList = pageResult.getRecords();
        setProductCommentIsLike(productCommentList).forEach(productComment -> {
                    String key = RedisKeyGenerator.secondCommentKey(productComment.getId());
                    if (RedisConnector.hasKey(key)) {
                        Integer likeCount = RedisConnector.getHashField(key, ProductComment.Fields.likeCount, Integer.class);
                        productComment.setLikeCount(likeCount);
                    }
                }
        );
        return getCursorCommonResult(cursorCommonEntity, productCommentList, copyMapper::productCommentToProductSecondCommentVO);
    }


    /**
     * 查询一级评论下的追评
     * 优先读取 Redis；缓存未命中时查询 MySQL，查不到也缓存空对象，避免反复查库。
     *
     * @param firstCommentId 原一级评论 id
     * @return 追评 VO，追评不存在时返回数据错误
     */
    @Override
    public Result getAppendComment(String firstCommentId) {
        Long aboutFirstCommentId = Long.valueOf(firstCommentId);
        String appendCommentKey = RedisKeyGenerator.appendCommentKey(aboutFirstCommentId);
        ProductCommentAppend productCommentAppend = RedisConnector.getHashObject(appendCommentKey, ProductCommentAppend.class);
        // 缓存未命中，按 commentId 查询追评；追评自身的 id 与原评论 id 不同。
        if (Objects.isNull(productCommentAppend)) {
            ProductCommentAppend productCommentAppendSelectOne = productCommentAppendMapper
                    .selectOne(new LambdaQueryWrapper<>(ProductCommentAppend.class)
                            .eq(ProductCommentAppend::getCommentId, aboutFirstCommentId)
                    );
            if (Objects.isNull(productCommentAppendSelectOne)) {
                RedisConnector.setHashObject(appendCommentKey, emptyProductCommentAppend);
                RedisConnector.expire(appendCommentKey, redisCacheTtlProperties.getProductAppendCommentTtl(), TimeUnit.SECONDS);
                return Result.error(MessageConstant.DATA_ERROR);
            }
            RedisConnector.setHashObject(appendCommentKey, productCommentAppendSelectOne);
            RedisConnector.expire(appendCommentKey, redisCacheTtlProperties.getProductAppendCommentTtl(), TimeUnit.SECONDS);
            ProductAppendCommentVO productAppendCommentVO = copyMapper.productCommentAppendToProductCommentAppendVO(productCommentAppendSelectOne);
            return Result.success(productAppendCommentVO);
        }
        //空对象过滤
        if (productCommentAppend.getId().equals(DataConstant.ZERO_LONG)) {
            return Result.error(MessageConstant.DATA_ERROR);
        }
        ProductAppendCommentVO productAppendCommentVO = copyMapper.productCommentAppendToProductCommentAppendVO(productCommentAppend);
        return Result.success(productAppendCommentVO);

    }

    /**
     * 获取商品下评论数
     * 布隆过滤器校验通过后优先读取 Redis，未命中时按商品 id 统计 MySQL 并缓存。
     * 当前统计条件包含一级评论和回复；追评存在独立的表中，不计入这里的数量。
     *
     * @param productId 商品 id
     * @return 字符串形式的评论数，无效缓存标记返回数据错误
     */
    @Override
    public Result getProductCommentCount(String productId) {
        boolean isContain = bloomFilterUtils.contains(Long.valueOf(productId));
        if (!isContain) {
            return Result.error(MessageConstant.DATA_ERROR);
        }
        Long productIdLong = Long.valueOf(productId);
        String key = RedisKeyGenerator.productCommentCount(productIdLong);
        String productCommentCount = StringRedisConnector.opsForValue().get(key);
        if (StringUtils.isBlank(productCommentCount)) {
            // count 返回 0 也是正常结果，只有 null 才写入 -1 标记。
            Long productCommentCountLong = lambdaQuery()
                    .eq(ProductComment::getProductId, productIdLong)
                    .count();
            if (Objects.isNull(productCommentCountLong)) {
                StringRedisConnector.opsForValue().set(key, emptyProductCommentCount);
                StringRedisConnector.expire(key, redisCacheTtlProperties.getProductCommentCountTtl(), TimeUnit.SECONDS);
                return Result.error(MessageConstant.DATA_ERROR);
            }
            productCommentCount = productCommentCountLong.toString();
            StringRedisConnector.opsForValue().set(key, productCommentCount);
            StringRedisConnector.expire(key, redisCacheTtlProperties.getProductCommentCountTtl(), TimeUnit.SECONDS);
            return Result.success(productCommentCount);
        }
        if (StringUtils.equals(productCommentCount, emptyProductCommentCount)) {
            return Result.error(MessageConstant.DATA_ERROR);
        }
        return Result.success(productCommentCount);

    }

    /**
     * 同步保存点赞状态和计数，事务提交后清除展示缓存。
     */
    @Override
    public Result updateProductCommentLike(String productCommentId, Integer isLike, Integer isFirstComment) {
        long userId = CurrentHolder.getCurrentUser().getId();
        Long commentId = Long.valueOf(productCommentId);
        RLock lock = redissonClient.getLock("product:comment:like:lock:" + commentId);
        lock.lock();
        try {
            Result result = transactionTemplate.execute(transaction -> {
                if (getById(commentId) == null) {
                    return Result.error(MessageConstant.DATA_ERROR);
                }
                ProductCommentLike record = productCommentLikeMapper.selectOne(
                        new LambdaQueryWrapper<ProductCommentLike>()
                                .eq(ProductCommentLike::getCommentId, commentId)
                                .eq(ProductCommentLike::getUserId, userId));
                if (record == null) {
                    record = new ProductCommentLike();
                    record.setCommentId(commentId);
                    record.setUserId(userId);
                    record.setStatus(isLike);
                    productCommentLikeMapper.insert(record);
                } else {
                    record.setStatus(isLike);
                    productCommentLikeMapper.updateById(record);
                }
                // 按前端操作直接增减，SQL 在数据库中完成计数更新。
                baseMapper.update(new LambdaUpdateWrapper<ProductComment>()
                        .eq(ProductComment::getId, commentId)
                        .setSql(isLike == 1 ? "like_count = like_count + 1"
                                : "like_count = GREATEST(0, like_count - 1)")
                        .set(ProductComment::getUpdatedTime, LocalDateTime.now()));
                return Result.success();
            });
            // execute 返回时事务已提交，避免查询在提交前回填旧数据。
            if (result.getSuccess()) {
                RedisConnector.delete(isFirstComment == 1
                        ? RedisKeyGenerator.firstCommentKey(commentId)
                        : RedisKeyGenerator.secondCommentKey(commentId));
                RedisConnector.delete(RedisKeyGenerator.productCommentUserIdList(commentId));
            }
            return result;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 为评论列表补全当前用户的 like 状态，并回填点赞用户 Set 缓存
     * 登录认证由过滤器完成；优先查询 Redis，未命中的评论一次性批量查库。
     * 将点赞记录按评论 id 分组，得到 {@code Map<评论 id, 点赞用户 id 集合>}，再按原列表下标回填。
     * 批量给一页评论填充「当前登录用户有没有点赞这条评论」的标记`isLike`，采用「Redis 优先，缺失数据批量查数据库，
     * 并且把查到的数据回写到 Redis 做缓存」的策略；没有点赞记录的评论也会往 Redis 写入占位数据，防止缓存击穿。
     */
    private List<ProductComment> setProductCommentIsLike(List<ProductComment> productCommentList) {
        if (productCommentList.isEmpty()) {
            return productCommentList;
        }
        long userId = CurrentHolder.getCurrentUser().getId();
        // 1. 收集缓存未命中的评论：key=评论 id，value=评论在原列表中的下标。
        HashMap<Long, Integer> needQueryEntityMap = new HashMap<>();
        for (int i = 0; i < productCommentList.size(); i++) {
            ProductComment productComment = productCommentList.get(i);
            String key = RedisKeyGenerator.productCommentUserIdList(productComment.getId());
            if (!RedisConnector.hasKey(key)) {
                needQueryEntityMap.put(productComment.getId(), i);
            } else {
                boolean isLike = Boolean.TRUE.equals(RedisConnector.opsForSet().isMember(key, userId));
                productComment.setLike(isLike);
            }
        }
        if (needQueryEntityMap.isEmpty()) {
            return productCommentList;
        }
        // 2. 批量查询缺失评论的有效点赞记录，避免每条评论单独查一次数据库。
        // keySet 是原 Map 的视图，从 Set 移除评论 id 时，Map 中对应的条目也会移除。
        Set<Long> needQueryEntityIdSet = needQueryEntityMap.keySet();
        LambdaQueryWrapper<ProductCommentLike> lambdaQueryWrapper = new LambdaQueryWrapper<ProductCommentLike>()
                .in(ProductCommentLike::getCommentId, needQueryEntityIdSet)
                .eq(ProductCommentLike::getStatus, 1);
        List<ProductCommentLike> allLike = productCommentLikeMapper.selectList(lambdaQueryWrapper);
        Map<Long, Set<Long>> commentUserMap = allLike.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(
                        ProductCommentLike::getCommentId,
                        Collectors.mapping(ProductCommentLike::getUserId, Collectors.toSet())
                ));
        // 3. 处理有点赞记录的评论：回填当前用户点赞状态，并缓存全部点赞用户 id。
        commentUserMap.forEach((commentId, userIdSet) -> {
            boolean isLike = userIdSet.contains(userId);
            Integer index = needQueryEntityMap.get(commentId);
            productCommentList.get(index).setLike(isLike);
            String key = RedisKeyGenerator.productCommentUserIdList(commentId);
            RedisConnector.opsForSet().add(key, userIdSet.toArray(new Long[0]));
            RedisConnector.expire(key, redisCacheTtlProperties.getProductCommentUserIdSetTtl(), TimeUnit.SECONDS);
            needQueryEntityIdSet.remove(commentId);
        });
        // 4. 剩余评论没有有效点赞记录，用 0L 占位，让 Redis 可以保留这个 Set 和过期时间。
        // 0L 不是实际点赞用户，当前用户的点赞状态设为 false。
        if (!needQueryEntityMap.isEmpty()) {
            needQueryEntityMap.forEach((commentId, index) -> {
                ProductComment productComment = productCommentList.get(index);
                productComment.setLike(false);
                String key = RedisKeyGenerator.productCommentUserIdList(commentId);
                RedisConnector.opsForSet().add(key, DataConstant.ZERO_LONG);
                RedisConnector.expire(key, redisCacheTtlProperties.getProductCommentUserIdSetTtl(), TimeUnit.SECONDS);

            });
        }
        return productCommentList;

    }

}
