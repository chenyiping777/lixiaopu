package com.lixiaopu.service.impl;

import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.lixiaopu.aop.annotation.ParamCheckAnnotation;
import com.lixiaopu.common.constant.DataConstant;
import com.lixiaopu.common.constant.MessageConstant;
import com.lixiaopu.common.mapstruct.CopyMapper;
import com.lixiaopu.common.result.*;
import com.lixiaopu.common.util.BloomFilterUtils;
import com.lixiaopu.infrastructure.rocketmq.product.ProductSyncOutbox;
import com.lixiaopu.infrastructure.rocketmq.product.ProductSyncService;
import com.lixiaopu.common.util.JacksonUtils;
import com.lixiaopu.infrastructure.es.document.ProductDocument;
import com.lixiaopu.infrastructure.es.mapstruct.EsCopyMapper;
import com.lixiaopu.service.ProductDocumentService;
import com.lixiaopu.infrastructure.redis.connect.RedisConnector;
import com.lixiaopu.infrastructure.redis.generator.RedisKeyGenerator;
import com.lixiaopu.infrastructure.redis.properties.RedisCacheCountProperties;
import com.lixiaopu.infrastructure.redis.properties.RedisCacheTtlProperties;
import com.lixiaopu.infrastructure.redis.service.ProductRedisCacheService;
import com.lixiaopu.infrastructure.rocketmq.constant.MqProductConstant;
import com.lixiaopu.pojo.entity.Product;
import com.lixiaopu.pojo.entity.CollectionProduct;
import com.lixiaopu.mapper.ProductMapper;
import com.lixiaopu.mapper.CategoryMapper;
import com.lixiaopu.service.admin.ProductRelationsService;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.lixiaopu.pojo.enums.CommonStatus;
import com.lixiaopu.pojo.enums.ProductSortTypeEnum;
import com.lixiaopu.pojo.vo.SimpleProductVO;
import com.lixiaopu.service.CollectionService;
import lombok.extern.slf4j.Slf4j;
import com.lixiaopu.service.ProductService;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;



@Slf4j
@Service
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product>
    implements ProductService{

    @Autowired
    private com.lixiaopu.service.ProductHistoryService productHistoryService;

    @Autowired
    private ProductMapper productMapper;
    @Autowired
    private CategoryMapper categoryMapper;
    @Autowired
    private ProductRelationsService productRelationsService;

    @Autowired
    private CollectionService collectionService;

    @Autowired
    private ProductDocumentService productDocumentService;

    @Autowired
    private ProductRedisCacheService productRedisCacheService;

    @Autowired
    private RedisCacheCountProperties redisCacheCountProperties;

    @Autowired
    private CopyMapper copyMapper;

    @Autowired
    private EsCopyMapper esCopyMapper;

    @Autowired(required = false)
    private RocketMQTemplate rocketMQTemplate;

    @Autowired
    private BloomFilterUtils bloomFilterUtils;
    @Autowired
    private ProductSyncOutbox productSyncOutbox;
    @Autowired
    private ProductSyncService productSyncService;


    /** 新增商品，主表、图片、规格和同步事件在同一事务中保存。 */
    @Override
    @Transactional
    public Long createProduct(Product product) {
        return persistProduct(null, product);
    }

    /** 修改商品，使用路径 ID，保留未提供字段。 */
    @Override
    @Transactional
    public Long updateProduct(Long id, Product product) {
        return persistProduct(id, product);
    }

    private Long persistProduct(Long id, Product request) {
        // 1：如果是修改（id不为空），先检查商品是否存在
        if (id != null && productMapper.lockById(id) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "商品不存在");
        }

        // 2：把id塞到请求对象里面
        request.setId(id);
        // 总库存由规格维护；已有商品资料编辑不能覆盖库存。
        request.setStock(id == null ? 0L : null);

        // 3：参数校验（validate）
        validateProductWrite(id, request);

        // 4：只有【新增】的时候，才填充默认值
        if (id == null) setProductDefaults(request);

        // 5：新增 或者 更新商品主表
        boolean saved = id == null ? save(request) : updateById(request);
        if (!saved) throw new IllegalStateException("商品保存失败");

        // 6：保存关联数据（图片、规格）
        productRelationsService.save(request);

        // 7：返回商品id
        return request.getId();
    }
    /**
     * 保存商品，并在同一事务内记录同步事件。
     * 事务提交后，由后台消费事件更新布隆过滤器、ES 和相关缓存。
     * */
    @Override
    @Transactional
    public boolean save(Product product) {
        // 调用父类方法，插入 MySQL
        boolean saved = super.save(product);

        // 插入成功，在当前事务内记录商品同步事件
        if (saved) {
            productSyncOutbox.record(product.getId());
        }

        return saved;
    }

    @Override
    @Transactional
    public boolean updateById(Product product) {
        product.setStock(null);
        boolean updated = super.updateById(product);
        if (updated) productSyncOutbox.record(product.getId());
        return updated;
    }

    /**
     * 新增商品必须提供分类、名称、价格；不管新增还是修改，只要传入了名称 / 价格 / 库存等字段，就要检查格式合法性；商品只能绑定启用状态的二级分类。
    * */
    private void validateProductWrite(Long id, Product request) {
        if (id == null && (request.getCategoryId() == null || request.getName() == null || request.getPrice() == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "新增商品需要分类、名称和价格");
        }
        if (request.getName() != null && (request.getName().isBlank() || request.getName().length() > 128)) invalidProduct("商品名称不合法");
        if (request.getPrice() != null && request.getPrice().signum() < 0) invalidProduct("价格不能为负数");
        if (request.getStock() != null && request.getStock() < 0) invalidProduct("库存不能为负数");
        if (request.getSalesCount() != null && request.getSalesCount() < 0) invalidProduct("销量不能为负数");
        if (request.getViewCount() != null && request.getViewCount() < 0) invalidProduct("浏览量不能为负数");
        if (request.getCategoryId() != null) {
            var category = categoryMapper.selectById(request.getCategoryId());
            if (category == null || category.getParentId() == 0 || category.getStatus() != 1) invalidProduct("请选择启用的二级分类");
        }
    }

    /**
     * 1. 如果传入的商品状态 `status` 为 null → 默认设为 未启用（INACTIVE）
     * 2. 如果库存 `stock` 为 null → 默认库存 `0`
     * 3. 如果销量 `salesCount` 为 null → 默认销量 `0`
     * 4. 如果浏览量 `viewCount` 为 null → 默认浏览量 `0`
    * */
    private void setProductDefaults(Product request) {
        if (request.getStatus() == null) request.setStatus(CommonStatus.INACTIVE);
        if (request.getStock() == null) request.setStock(0L);
        if (request.getSalesCount() == null) request.setSalesCount(0L);
        if (request.getViewCount() == null) request.setViewCount(0L);
    }

    @Override
    @Transactional
    public void deleteProduct(Long id) {
        if (productMapper.lockById(id) == null){
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "商品不存在");
        }
        // 删除商品关联数据
        productRelationsService.delete(id);
        // 删除商品主表数据
        boolean res = removeById(id);
        // 检查删除结果
        if (!res) {
            throw new IllegalStateException("商品删除失败");
        }
    }

    @Override
    @Transactional
    public boolean removeById(java.io.Serializable id) {
        boolean removed = super.removeById(id);
        if (removed) productSyncOutbox.record(Long.valueOf(id.toString()));
        return removed;
    }

    //参数非法
    private static void invalidProduct(String message) {

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }



    @Autowired
    private RedisCacheTtlProperties redisCacheTtlProperties;



    /**
     * 从redis获取热门商品
     * 按照销量进行排名
     * @param limit 展示数量
     * @return 返回商品实体类
     */
    @Override
    public List<ProductDocument> getHotProduct(Integer limit) {
        int count = limit == null ? 10 : limit;
        if (count < 1 || count > redisCacheCountProperties.getHotProductCacheSize()) {
            throw new IllegalArgumentException("热门商品数量必须为 1 到 " + redisCacheCountProperties.getHotProductCacheSize());
        }
        return productRedisCacheService.getHotProduct().stream().limit(count).toList();
    }

    /**
     * /**
     *  * 根据逗号分隔的商品ID字符串批量查询商品简略信息
     *  * 优先从ES查询，ES查不到的商品，降级走MySQL查询并发送MQ异步同步到ES
     *  * @param productIds 逗号分隔的商品id字符串，例如 "1001,1002,1003"
     *  * @return 保持传入ID顺序的商品简略VO列表
     *
     * 查询商品简介时，发现 ES 缺少商品文档”之后执行 asyncSaveProductDocumentBySendMqMessage()，用于补齐 ES 数据
     */
    @Override
    public Result getBriefProduct(String productIds) {
        // 校验入参：字符串为空/空白字符，直接返回空集合
        if (StringUtils.isBlank(productIds)) {
            return Result.success(new ArrayList<>(0));
        }

        // 将逗号分隔的字符串切割，转成Long类型的商品ID列表
        List<Long> productIdList = Arrays.stream(StringUtils.split(productIds, ","))
                .map(Long::valueOf)
                .toList();

        // resultMap：用于保存最终结果，key=商品ID，value=商品VO；预先初始化，保证ID顺序映射
        Map<Long, SimpleProductVO> resultMap = new HashMap<>(productIdList.size());
        // resultList：最终返回给前端的列表，需要维持入参商品ID的原始顺序
        List<SimpleProductVO> resultList = new ArrayList<>(productIdList.size());

        // 先把所有id放入map，value初始化为null，标记这些id待填充数据
        for (Long id : productIdList) {
            resultMap.put(id, null);
        }

        // ========== 第一步：优先查询ES商品索引 ==========
        List<SimpleProductVO> simpleProductVOListByEs =
                productDocumentService.getProductDocumentByIdList(productIdList)
                        .stream()
                        .map(esCopyMapper::toSimpleProduct) // ES文档对象转SimpleProductVO
                        .toList();

        // 将ES查询出来的数据回填到resultMap，覆盖对应ID的null值
        for (SimpleProductVO s : simpleProductVOListByEs) {
            resultMap.put(s.getId(), s);
        }

        // 场景1：ES查到的数量 和 请求ID总数完全相等 → 全部命中ES，直接按原始ID顺序组装返回
        if (productIdList.size() == simpleProductVOListByEs.size()) {
            for (Long id : productIdList) {
                SimpleProductVO simpleProductVO = resultMap.get(id);
                resultList.add(simpleProductVO);
            }
            return Result.success(resultList);
        }

        // 场景2：ES部分缺失，找出ES没查到的商品ID（map中value为null的id），需要去MySQL查兜底
        List<Long> needQueryBySQLIdList = new ArrayList<>();
        resultMap.forEach((key, value) -> {
            if (Objects.isNull(value)) {
                needQueryBySQLIdList.add(key);
            }
        });

        // MySQL查询缺失商品的简要信息
        List<Product> list = productMapper.getBriefProduct(needQueryBySQLIdList);
        // 发送MQ消息，异步把这批MySQL查到的数据同步写入ES（异步补偿ES索引）
        asyncSaveProductDocumentBySendMqMessage(list, 0);

        // MySQL查询结果转为VO，回填到resultMap
        list.stream()
                .map(copyMapper::productToSimpleProductVO)
                .forEach(simpleProductVO -> resultMap.put(simpleProductVO.getId(), simpleProductVO));

        // 按照【入参productIdList的原始顺序】组装返回结果，保证返回顺序和请求ID顺序一致
        for (int i = 0; i < productIdList.size(); i++) {
            Long id = productIdList.get(i);
            SimpleProductVO simpleProductVO = resultMap.get(id);
            resultList.add(i, simpleProductVO);
        }

        return Result.success(resultList);

    }

    /**
     * 发送MQ消息，异步触发商品文档同步到ES
     * 发送失败支持本地重试；重试耗尽后记录 ERROR 日志及异常堆栈
     * @param productList 待同步的商品数据库实体列表
     * @param retryCount 当前已经重试的次数
     */
    void asyncSaveProductDocumentBySendMqMessage(List<Product> productList, int retryCount) {
        // 将MySQL商品实体Product，转换为ES文档实体ProductDocument
        List<ProductDocument> productDocumentList = productList.stream()
                .map(esCopyMapper::toDocument)
                .toList();

        // 如果RocketMQ模板实例为空（MQ不可用），降级方案：直接同步调用syncProduct，不走MQ
        // 直接调用syncProduct，也就是前面那套：更新缓存、同步ES的业务逻辑
        if (rocketMQTemplate == null) {
            productList.stream()
                    .map(Product::getId)
                    .distinct() // 去重，避免同一个商品多次执行同步
                    .forEach(productSyncService::syncProduct);
            return;
        }

        // 组装MQ目标地址：topic:tag
        String destination = MqProductConstant.TOPIC_PRODUCT + ":" + MqProductConstant.TAG_PRODUCT_DOCUMENT_SYNC;
        // 最大重试次数：最多重试2次
        int maxRetry = 2;

        // RocketMQ异步发送消息，带发送回调
        rocketMQTemplate.asyncSend(destination, productDocumentList, new SendCallback() {
            /**
             * 消息发送成功回调：消息成功投递到MQ broker
             */
            @Override
            public void onSuccess(SendResult sendResult) {
                // 发送成功无需额外处理，交给消费端去消费同步ES
            }

            /**
             * 消息发送异常回调：网络异常、Broker不可达等，消息没能投递进MQ
             * @param throwable 异常信息
             */
            @Override
            public void onException(Throwable throwable) {
                // 判断：当前重试次数 < 最大重试次数，递归调用自己，再次尝试发送MQ消息
                if (retryCount < maxRetry) {
                    asyncSaveProductDocumentBySendMqMessage(productList, retryCount + 1);
                } else {
                    log.error("商品 ES 补同步 MQ 发送失败，重试已耗尽：topic={}, tag={}, productIds={}, retryCount={}",
                            MqProductConstant.TOPIC_PRODUCT, MqProductConstant.TAG_PRODUCT_DOCUMENT_SYNC,
                            productList.stream().map(Product::getId).toList(), retryCount, throwable);
                }
            }
        });
    }


    /**
     * 获取商品详情
     * 查询商品详情的接口，采用 Redis 缓存 + 布隆过滤器 + 缓存空值（缓存穿透解决方案），核心功能：根据商品 id 查询商品详情，并判断当前用户是否收藏该商品。
     * @param productId 商品 id
     * @param userId 用户 id，未登录时为 null，查询时使用 -1L 代表匿名用户
     * @return 商品实体类
     */
    @Override
    @SuppressWarnings("unchecked")
    public Result getProductDetail(String productId, Long userId) {
        // 参数校验
        if (StringUtils.isBlank(productId)) {
            return Result.error(MessageConstant.TOM_CAT_ERROR);
        }
        // 判断商品是否存在
        if (!bloomFilterUtils.contains(Long.valueOf(productId))) {
            return Result.error(MessageConstant.DATA_ERROR);
        }
        // 判断用户是否登录
        if (userId == null) {
            userId = -1L;
        }
        //获取商品详情缓存
        String productDetailKey = RedisKeyGenerator.productDetail(Long.valueOf(productId));
        // 获取商品收藏缓存
        String productCollectionKey = RedisKeyGenerator.productCollection(Long.valueOf(productId));
        // 从缓存获取商品详情
        Map<String, Object> productDetailMap = RedisConnector.opsForHash().entries(productDetailKey);
        // 从缓存获取商品收藏
        Set<Object> userIdSet = (Set<Object>) (RedisConnector.opsForValue().get(productCollectionKey));
        if (!productDetailMap.isEmpty()) {
            long ttl = productDetailMap.size() == 1 ? redisCacheTtlProperties.getProductEmptyTtl()
                    : redisCacheTtlProperties.getProductDetailTtl();
            RedisConnector.expireIfPersistent(productDetailKey, ttl);
        }
        if (userIdSet != null) {

            RedisConnector.expireIfPersistent(productCollectionKey, redisCacheTtlProperties.getProductCollectionTtl());
        }
        // 判断商品详情缓存是否为空
        if (productDetailMap.isEmpty()) {
            Product product = productMapper.selectByProductId(productId, userId);
            //空对象
            if (Objects.isNull(product)) {
                RedisConnector.setHashWithTtl(productDetailKey, Map.of(Product.Fields.id, productId),
                        redisCacheTtlProperties.getProductEmptyTtl());
                return Result.error(MessageConstant.DATA_ERROR);
            }
            if (!StringUtils.equals(product.getIsCollection().toString(), CommonStatus.INACTIVE.getNumber().toString())) {
                product.setIsCollection(
                        CommonStatus.ACTIVE.getNumber());
            }
            Map<String, Object> productDetailResultMap = JacksonUtils.toMap(product);
            productDetailResultMap.put(Product.Fields.isCollection, CommonStatus.INACTIVE.getNumber());
            RedisConnector.setHashWithTtl(productDetailKey, productDetailResultMap,
                    redisCacheTtlProperties.getProductDetailTtl());

            productHistoryService.record(product.getId());
            return Result.success(copyMapper.productToProductDetailVO(product));

        }
        //对空对象二次访问拦截
        if (productDetailMap.size() == 1) {
            return Result.error(MessageConstant.DATA_ERROR);

        }

        Product resultProduct = JacksonUtils.fromMap(productDetailMap, Product.class);
        if (resultProduct.getStatus() != CommonStatus.ACTIVE) {
            return Result.error(MessageConstant.DATA_ERROR);
        }

        if (userIdSet == null) {
            // null 表示缓存未命中；空 Set 表示已查询且无人收藏，不应再次查库。
            var productCollectionList = collectionService.lambdaQuery().eq(CollectionProduct::getProductId, productId).list();
            userIdSet = productCollectionList.stream().map(CollectionProduct::getUserId).collect(Collectors.toSet());
            RedisConnector.opsForValue().set(productCollectionKey, userIdSet,
                    redisCacheTtlProperties.getProductCollectionTtl(), TimeUnit.SECONDS);

        }

        if (userIdSet.contains(userId)) {
            resultProduct.setIsCollection(CommonStatus.ACTIVE.getNumber());
        } else {
            resultProduct.setIsCollection(CommonStatus.INACTIVE.getNumber());

        }
        productHistoryService.record(resultProduct.getId());
        return Result.success(copyMapper.productToProductDetailVO(resultProduct));
    }


    /**
     * 游标分页查询分类下的简易商品列表
     * @param cursorCommonEntity 游标分页通用实体，包含排序类型、游标ID、游标值、查询条数等信息
     * @param categoryId 分类ID
     * @param isFirstCategoryId 是否为一级分类标识 true-一级分类 false-非一级分类
     * @return CursorCommonResult 游标分页通用返回结果（商品数据 + 下一页游标信息）
     */
    @Override
    public CursorCommonResult getCategorySimpleProduct(CursorCommonEntity cursorCommonEntity, Long categoryId, boolean isFirstCategoryId) {
        // 获取前端传入的排序类型
        String sortType = cursorCommonEntity.getSortType();
        // 获取游标ID（上一页最后一条数据主键）
        Long sortId = cursorCommonEntity.getSortId();
        // 获取游标值（排序字段的值，如价格、时间等）
        String sortValue = cursorCommonEntity.getSortValue();
        // 根据排序类型字符串，转换为排序枚举
        ProductSortTypeEnum productSortTypeEnum = ProductSortTypeEnum.getByValue(sortType);
        // 对游标值做过滤、格式化处理，防止非法参数
        sortValue = ProductSortTypeEnum.filterFormatSortValue(productSortTypeEnum, sortValue);
        // 每页查询数量
        Integer querySize = cursorCommonEntity.getQuerySize();
        // 调用ES搜索服务，根据分类ID进行游标分页检索商品文档
        List<ProductDocument> productDocuments = productDocumentService.searchByCursorByCategoryId(
                querySize, productSortTypeEnum, sortValue, sortId, categoryId, isFirstCategoryId);
        // 组装游标分页返回结果，封装商品列表和下一页游标信息
        return getCursorCommonResult(productDocuments, querySize, productSortTypeEnum, sortType);
    }




    /**
     * 游标分类查询简介商品列表
     * @param cursorCommonEntity 分类游标通用实体
     * @param keyword 关键词
     * @return 分类游标通用实体
     */
    @Override
    @ParamCheckAnnotation
    public CursorCommonResult searchProductList(CursorCommonEntity cursorCommonEntity, String keyword) {
        // 获取查询参数
        Integer querySize = cursorCommonEntity.getQuerySize();
        String sortType = cursorCommonEntity.getSortType();
        Long sortId = cursorCommonEntity.getSortId();
        String sortValue = cursorCommonEntity.getSortValue();
        // 获取排序类型
        ProductSortTypeEnum productSortTypeEnum = ProductSortTypeEnum.getByValue(sortType);
        //对前端传过来的游标 sortValue 做校验 + (只有【最新上架】排序，才需要处理时间格式)格式标准化，
        //得到一个格式合法统一的游标字符串，供后面 ES search_after 查询使用。
        sortValue = ProductSortTypeEnum.filterFormatSortValue(productSortTypeEnum, sortValue);
        List<ProductDocument> productDocuments = productDocumentService.searchByCursorByName(
                querySize, productSortTypeEnum, sortValue, sortId, keyword);
        // 根据 productSortTypeEnum 定义的排序规则，使用 [sortValue, sortId] 双游标查询下一页；
        // 当主排序字段值等于 sortValue 时，继续比较兜底id，取排在游标记录之后的数据。

        return getCursorCommonResult(productDocuments, querySize, productSortTypeEnum, sortType);

    }

    /**
     * 游标结果封装方法
     * @param productDocuments 在商品文档服务 查询出来的 原始商品文档
     * @param querySize 查询数量
     * @param productSortTypeEnum 商品排序种类枚举
     * @param sortType 排序种类字符串
     * @return 游标结果
     */
    private CursorCommonResult getCursorCommonResult(List<ProductDocument> productDocuments, Integer querySize, ProductSortTypeEnum productSortTypeEnum, String sortType) {
        boolean isEnd = false;
        if (productDocuments.isEmpty()) {
            return CursorCommonResult.builder()
                    .isEnd(true)
                    .list(Collections.emptyList())
                    .build();
        }
        if (querySize > productDocuments.size()) {
            isEnd = true;
        }
        ProductDocument productDocument = productDocuments.get(productDocuments.size() - 1);
        String sortValueByProductDocument = ProductSortTypeEnum.getSortValueByProductDocument(productSortTypeEnum, productDocument);
        CursorCommonEntity cursorCommonEntityResult = CursorCommonEntity.builder()
                .sortType(sortType)
                .querySize(querySize)
                .sortId(productDocument.getId())
                .sortValue(sortValueByProductDocument)
                .build();
        List<SimpleProductVO> simpleProductVOS = productDocuments.stream().map(esCopyMapper::toSimpleProduct).toList();
        return CursorCommonResult.builder()
                .isEnd(isEnd)
                .list(simpleProductVOS)
                .cursorCommonEntity(cursorCommonEntityResult)
                .build();
    }

    /**
     * 基于商品名字, 进行es查询
     * @param productName 商品名字
     * @param limit 查询数
     * @return 返回相关推荐商品的简略展示列表
     */
    @Override
    public List<SimpleProductVO> getProductRelated(String productName, Integer limit) {
        List<ProductDocument> allMatchProductDocument = productDocumentService.getProductDocumentByProductNameKeyword(productName, limit + 1);
        for (ProductDocument productDocument : allMatchProductDocument) {
            if (productDocument.getName().equals(productName)) {
                allMatchProductDocument.remove(productDocument);
                break;
            }
        }
        return allMatchProductDocument.stream().map(esCopyMapper::toSimpleProduct).toList();


    }

    /**
     * 滚动查询的商品列表
     * 判断是否是首次查询,是获取随机开始id ,然后进行es 游标查询
     * @return 游标返回实体
     */
    @Override
    public SimpleCursorCommonResult getSimpleProductByScrollQuery(Long beginId , Integer querySize) {
        if (Objects.isNull(beginId)) {
            Long maxProductId = productRedisCacheService.getMaxProductId();
            long maxBeginId = Math.round(maxProductId * DataConstant.QUERY_SECURITY_NUMBER);
            maxBeginId = Math.max(maxBeginId, 2);
            beginId = ThreadLocalRandom.current().nextLong(1, maxBeginId);
        }
        List<SimpleProductVO> resultList = productDocumentService.searchLimitAfterProductId(querySize, beginId).stream()
                .map(esCopyMapper::toSimpleProduct)
                .collect(Collectors.toList());
        if (resultList.isEmpty()) {
            return SimpleCursorCommonResult.builder().list(Collections.emptyList())
                    .isEnd(true)
                    .build();
        }
        Long endId = resultList.get(resultList.size() - 1).getId();
        boolean isEnd = resultList.size() < querySize;
        Collections.shuffle(resultList);
        SimpleCursorCommonEntity simpleCursorCommonEntity = SimpleCursorCommonEntity.builder()
                .querySize(querySize)
                .sortId(endId)
                .build();
        return SimpleCursorCommonResult.builder()
                .list(resultList)
                .isEnd(isEnd)
                .simpleCursorCommonEntity(simpleCursorCommonEntity)
                .build();

    }
}
