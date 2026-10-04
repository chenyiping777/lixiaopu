package com.lixiaopu.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.lixiaopu.aop.annotation.UpdateCategoryTreeRedisCacheAnnotation;
import com.lixiaopu.common.constant.DataConstant;
import com.lixiaopu.common.constant.MessageConstant;
import com.lixiaopu.common.result.Result;
import com.lixiaopu.common.result.ResultCode;
import com.lixiaopu.common.util.CaffeineUtils;
import com.lixiaopu.infrastructure.redis.connect.RedisConnector;
import com.lixiaopu.infrastructure.redis.generator.RedisKeyGenerator;
import com.lixiaopu.mapper.CategoryMapper;
import com.lixiaopu.mapper.ProductMapper;
import com.lixiaopu.pojo.dto.CategoryDTO;
import com.lixiaopu.pojo.entity.Category;
import com.lixiaopu.pojo.entity.Product;
import com.lixiaopu.pojo.enums.CommonStatus;
import com.lixiaopu.service.CategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, Category>
        implements CategoryService {
    
    @Autowired
    private ProductMapper productMapper;
    @Autowired
    private CaffeineUtils caffeineUtils;
    @Autowired
    private RedisConnector redisConnector;

    @Override
    public Result getCategoryTree() {
        //返回分类树缓存
        List<Category> rootCategories = caffeineUtils.getCategoryTree();
        return Result.success(rootCategories);
    }

    /**
     * loadingCache 存储分类树缓存
     * @return
     * 谁调用 getCategoryTreeCache()？
     * 项目的 [CaffeineConfig.java创建缓存时，设置了这段加载逻辑：
     * 是 Caffeine 在缓存未命中时执行这个函数，进而调用 getCategoryTreeCache()。
     */
    public List<Category> getCategoryTreeCache() {
        //查询全部 category
        //查出所有启用分类，得到allCategories = [水果, 零食, 苹果, 香蕉, 饼干]
        //此时还没有树
        List<Category> allCategories = lambdaQuery()
                .eq(Category::getStatus, 1)
                .orderByAsc(Category::getSort).list();
        //从allCategories中筛选出根分类，得到rootCategories = [水果, 零食]
        List<Category> rootCategories = allCategories.stream()
                .filter(category -> category.getParentId().equals(DataConstant.ZERO_LONG))
                .collect(Collectors.toList());
        buildCategoryTree(allCategories, rootCategories);
        return rootCategories;//[水果, 零食]
        //为什么说rootCategories还带着children，以怎样的方式带着
        //因为Category里有List<Category> children
    }

    /**
     * 递归 为分类树 set List<Category> children
     * @param allCategories
     * @param parentCategories
     */
    private void buildCategoryTree(List<Category> allCategories, List<Category> parentCategories) {
        if (CollectionUtils.isEmpty(allCategories) || CollectionUtils.isEmpty(parentCategories)) {
            return;
        }
        //把父分类编号categoryId相同的分类放在一组。
        /*
        *父分类编号 → 属于它的分类
            0     → [水果, 零食]
            1     → [苹果, 香蕉]
            2     → [饼干]
        * */
        Map<Long, List<Category>> groupMap = allCategories.stream()
                .collect(Collectors.groupingBy(Category::getParentId));
        //为每个父分类设置子分类
        List<Category> nextCategoriesList = new ArrayList<>();
        //遍历父分类列表
        //category.getId()          // 1
        //groupMap.get(1L)          // [苹果, 香蕉]
        //category.setChildren(...) // 水果.children = [苹果, 香蕉]
        //getOrDefault 方法用于获取指定键的值，如果不存在则返回默认值new ArrayList<>()空列表[]
        parentCategories.forEach(category -> {
            List<Category> childrenCategories = groupMap.getOrDefault(category.getId(), new ArrayList<>());
            category.setChildren(childrenCategories);
            nextCategoriesList.addAll(childrenCategories);
        });
        buildCategoryTree(allCategories, nextCategoriesList);
    }

    /**
     * 获取分类树子节点
     * @param categoryId
     * @return
     */
    @Override
    public Result getCategoryChildren(String categoryId) {
        //Caffeine是一个Java缓存库，可以把数据保存在当前Java程序的内存中，供后续请求复用。
        //它提供的 LoadingCache 能在缓存缺失时调用预先配置的加载方法。
        List<Category> categoryTree = caffeineUtils.getCategoryTree();
        //从树中先找到父分类，根据分类编号categoryId
        List<Category> list = categoryTree
                .stream()
                .filter(category -> StringUtils.equals
                        (categoryId, category.getId().toString()))
                .toList();
        if (list.isEmpty()||list.size()>DataConstant.ONE_INT){
            return Result.error(MessageConstant.DATA_ERROR);
        }
        List<Category> categoryChildrenList = list.get(DataConstant.ZERO_INT).getChildren();
        return Result.success(categoryChildrenList);
    }

    /**
     * 添加分类,支持添加一级分类，也支持添加二级分类，由 parentId 决定。
     *
     * @param categoryDTO
     * @return
     * Caffeine 保存“完整分类树”，Redis 保存“一级分类 → 二级分类 ID 列表”。
     */
    @Override
    @UpdateCategoryTreeRedisCacheAnnotation
    public Result addCategory(CategoryDTO categoryDTO) {

        //参数校验 → 父分类校验 → DTO 转实体（按需赋值）→ 入库保存 → 成功则清空 Caffeine 本地分类树缓存，返回结果
        if (categoryDTO == null || StringUtils.isBlank(categoryDTO.getName())) {
            return Result.error(400, "分类名称不能为空");
        }

        // 1.校验parentId，要求不能为空且必须是非负整数
        // 使用分支避免三元表达式把解析失败的null自动拆箱成long，引发空指针。
        Long parentId = DataConstant.ZERO_LONG;//默认父分类编号为0，表示一级分类。
        if (categoryDTO.getParentId() != null) {
            //把 parentId 字符串转换为（非负整数） Long 类型
            parentId = parseNonNegativeId(categoryDTO.getParentId());
        }
        if (parentId == null) {
            return Result.error(400, "父分类 ID 必须为非负整数");
        }

        //2.校验父分类是否存在,并且不能是自己，
        //且父分类不能是自己的同分类级，只能是一级分类，当前项目只支持两个级的分类
        String parentError = validateParentCategory(parentId, null);
        if (parentError != null) {
            return Result.error(400, parentError);
        }

        //3.设置分类名称等属性
        //只设置请求实际提供的字段，让实体上的默认值继续生效。
        Category category = new Category();
        // 拷贝DTO中非null字段：iconUrl、sort、status（前端传了才赋值，null不拷贝）
        BeanUtil.copyProperties(categoryDTO, category, true);
        // 手动处理特殊字段：name需要trim，parentId是解析校验后的结果，不能直接拷DTO的
        category.setName(categoryDTO.getName().trim());
        category.setParentId(parentId);

        boolean isSuccess = save(category);
        if (!isSuccess) {
            return Result.error(MessageConstant.SQL_MESSAGE_SAVE_ERROR);
        }
        //4.清除 Caffeine 本地分类树缓存，使下次查询时重新构建缓存，避免缓存数据过期
        caffeineUtils.invalidateCategoryTree();
        return Result.success(category);

    }

    /**
     * 删除分类
     * @param categoryId
     * @return
     */
    @Override
    @UpdateCategoryTreeRedisCacheAnnotation
    public Result deleteCategory(String categoryId) {
        //删除的前置条件：分类ID必须为正整数，且不能有子分类，且不能有商品
        Long id = parsePositiveId(categoryId);
        if (id == null) {
            return Result.error(400, "分类 ID 必须为正整数");
        }
        if (hasChildren(id)) {
            return Result.error(400, "该分类还有子分类，请先处理子分类");
        }
        if (productMapper.selectCount(new LambdaQueryWrapper<Product>()
                .eq(Product::getCategoryId, id)) > 0) {
            return Result.error(400, "该分类下还有商品，请先处理关联商品");
        }
        boolean isSuccess = removeById(id);
        if (!isSuccess) {
            return Result.error(MessageConstant.DELETE_ERROR);
        }
        //清除 Caffeine 本地分类树缓存，使下次查询时重新构建缓存，避免缓存数据过期
        caffeineUtils.invalidateCategoryTree();
        return Result.success();
    }


    /**
     * 更新分类
     *
     * @param id
     * @return
     * 分类修改成功
     *     ↓
     * 清除当前应用的 Caffeine 分类树
     *     ↓
     * 切面调用 updateCategoryTreeRedisCache()
     *     ↓
     * 读取 Caffeine → 此时缓存为空 → 查数据库重新构建树
     *     ↓
     * 完整树存入 Caffeine
     *     ↓
     * 从树中提取分类 ID 映射，写入 Redis
     */
    @Override
    @UpdateCategoryTreeRedisCacheAnnotation
    public Result updateCategoryInfo(String id, CategoryDTO categoryDTO) {
        //更新的前置条件：分类ID必须为正整数，且不能有子分类，且不能有商品
        //1.校验分类ID是否为正整数
        Long categoryId = parsePositiveId(id);
        if (categoryId == null) {
            return Result.error(400, "分类 ID 必须为正整数");
        }
        //2.校验待更新字段是否为空
        if (categoryDTO == null || !hasAnyUpdateField(categoryDTO)) {
            return Result.error(400, "至少提供一个待修改字段");
        }
        //3.校验分类名称是否为空
        if (categoryDTO.getName() != null && StringUtils.isBlank(categoryDTO.getName())) {
            return Result.error(400, "分类名称不能为空");
        }
        //4.校验父分类ID是否为非负整数
        Long parentId = categoryDTO.getParentId() == null ? null : parseNonNegativeId(categoryDTO.getParentId());
        if (categoryDTO.getParentId() != null && parentId == null) {
            return Result.error(400, "父分类 ID 必须为非负整数");
        //5.校验父分类是否为当前分类的祖先分类
        }
        if (parentId != null) {
            String parentError = validateParentCategory(parentId, categoryId);
            //6.校验是否有子分类
            if (parentError != null) {
                return Result.error(400, parentError);
            }
            if (parentId > 0 && hasChildren(categoryId)) {
                return Result.error(400, "有子分类的一级分类不能移动到其他分类下");
            }
        }

        // updateById 默认跳过 null：未传入的字段不会被实体默认值覆盖。
        Category category = new Category();
        // 3个参数：source, target, ignoreNullValue（true=忽略源中null字段）
        BeanUtil.copyProperties(categoryDTO, category, true);
        // 手动覆盖特殊字段
        category.setId(categoryId);
        category.setParentId(parentId);
        category.setName(categoryDTO.getName() == null ? null : categoryDTO.getName().trim());

        boolean isSuccess = updateById(category);
        if (!isSuccess) {
            return Result.error(MessageConstant.SQL_MESSAGE_SAVE_ERROR);
        }
        caffeineUtils.invalidateCategoryTree();
        return Result.success(category);
    }

    /**
     * 更新分类状态
     *
     * @param id
     * @param status
     * @return
     */
    @Override
    @UpdateCategoryTreeRedisCacheAnnotation
    public Result updateCategoryStatus(String id, String status) {
        //更新的前置条件：分类ID必须为正整数，状态只能为0或1
        //1.校验分类ID是否为正整数
        Long categoryId = parsePositiveId(id);
        if (categoryId == null) {
            return Result.error(400, "分类 ID 必须为正整数");
        }
        //2.校验状态是否为0或1
        if (!"0".equals(status) && !"1".equals(status)) {
            return Result.error(400, "状态只能为 0 或 1");
        }
        //3.更新分类状态
        int statusNumber = Integer.parseInt(status);
        String statusValue = CommonStatus.getValueByNumber(statusNumber);
        boolean isSuccess = lambdaUpdate().eq(Category::getId, categoryId).set(Category::getStatus, statusNumber).update();
        if (!isSuccess) {
            return Result.error(MessageConstant.SQL_MESSAGE_SAVE_ERROR);
        }
        //4.清除 Caffeine 本地分类树缓存，使下次查询时重新构建缓存，避免缓存数据过期
        Map<String, Object> map = new HashMap<>(2);
        map.put(Category.Fields.id, id);
        map.put(Category.Fields.status, statusValue);
        caffeineUtils.invalidateCategoryTree();
        return Result.success(map);
    }

    /**
     * 将分类 ID 字符串转换为正整数，分类自身的 ID 不允许为 0。
     *
     * @param value 分类 ID 字符串
     * @return 转换后的 ID；格式不合法、超出 Long 范围或为 0 时返回 null
     */
    private static Long parsePositiveId(String value) {
        Long parsed = parseNonNegativeId(value);
        return parsed != null && parsed > 0 ? parsed : null;
    }

    /**
     * 将 ID 字符串转换为非负整数，允许用 0 表示一级分类的父节点。
     * @param value ID 字符串，仅允许 0 或不带前导零的正整数
     * @return 转换后的 ID；为空、格式不合法或超出 Long 范围时返回 null
     */
    private static Long parseNonNegativeId(String value) {
        if (value == null || !value.matches("^(0|[1-9][0-9]*)$")) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 判断更新请求是否提供了至少一个待修改字段，避免执行空更新。
     * 字段为 null 表示未提供；空字符串也算提供，内容是否合法由对应业务校验判断。
     *
     * @param categoryDTO 非 null 的分类更新请求
     * @return 提供了任意一个待修改字段时返回 true
     */
    private static boolean hasAnyUpdateField(CategoryDTO categoryDTO) {
        return categoryDTO.getName() != null
                || categoryDTO.getParentId() != null
                || categoryDTO.getIconUrl() != null
                || categoryDTO.getSort() != null
                || categoryDTO.getStatus() != null;
    }

    /**
     * 校验父分类是否符合两级分类规则：0 表示一级分类；其他值必须指向已有的一级分类。
     * 更新时还要防止分类把自己设为父分类。
     *
     * @param parentId 已解析的非负父分类 ID
     * @param categoryId 当前分类 ID；新增分类时为 null
     * @return 校验通过时返回 null，否则返回业务错误信息
     */
    private String validateParentCategory(Long parentId, Long categoryId) {
        if (parentId == 0L) {
            return null;
        }
        // 1.校验父分类 ID 是否与分类 ID 相同
        if (parentId.equals(categoryId)) {
            return "分类不能以自身作为父分类";
        }
        // 2.校验父分类是否存在
        Category parent = getById(parentId);
        if (parent == null) {
            return "父分类不存在";
        }
        // 3.校验父分类是否是一级分类(直接父类)
        Long zeroObj = DataConstant.ZERO_LONG;//当前parentId=0L表示一级分类。
        Long parentParentId = parent.getParentId();
        boolean result = zeroObj.equals(parentParentId);
        if (!result) {
            return "只能选择一级分类作为父分类";//当前项目的业务规则最多支持两级分类，不允许出现孙子分类。
        }
        return null;
    }

    /**
     * 查询分类是否存在直接子分类，包含启用和禁用的子分类。
     * 删除分类或把一级分类移动到其他分类下之前使用，避免破坏分类层级。
     *
     * @param categoryId 当前分类 ID
     * @return 存在直接子分类时返回 true
     */
    private boolean hasChildren(Long categoryId) {
        return lambdaQuery().eq(Category::getParentId, categoryId).count() > 0;
    }


    /**
     * 更新分类树Redis缓存映射
     * 逻辑：从本地Caffeine缓存读取最新分类树，组装一级分类ID -> 二级分类ID列表的Hash结构，写入Redis
     * 先删除旧的Redis Hash，再写入新数据；分类树为空时仅清理旧缓存，不写入空Hash
     *1.
     * key：lxp:category:tree
     * 类型：Hash
     * field                 value
     * firstCategory:1       [11, 12]
     * firstCategory:2       [21, 22]
     *2.
     * Map.of(
     *     "firstCategory:1", List.of(11L, 12L),
     *     "firstCategory:2", List.of(21L, 22L)
     * );
     */
    @Override
    public void updateCategoryTreeRedisCache() {
        // 获取分类树在Redis中的key
        String key = RedisKeyGenerator.categoryTreeKey();
        // 从Caffeine本地缓存获取最新完整分类树数据
        List<Category> categoryTree = caffeineUtils.getCategoryTree();
        // 构建HashMap，用于存放Hash的field-value：field=一级分类hashKey，value=该一级下所有二级分类ID集合
        Map<String, Object> map = new HashMap<>(categoryTree.size());

        // 遍历一级分类，组装Hash映射关系
        for (Category category : categoryTree) {
            // 获取当前一级分类ID
            Long firstCategoryId = category.getId();
            // 根据一级分类ID生成Hash的field键
            String hashKey = RedisKeyGenerator.categoryTreeHashKey(firstCategoryId);
            // 收集该一级分类下所有二级分类ID，转为List
            List<Long> secondCategoryIdList = category.getChildren().stream()
                    .map(Category::getId)
                    .collect(Collectors.toList());
            // 放入待写入map
            map.put(hashKey, secondCategoryIdList);
        }

        // 先删除旧的Hash缓存，避免残留旧数据
        redisConnector.delete(key);
        // 如果map不为空，将组装好的一级-二级ID映射批量写入Redis Hash；空树只清理，不写入空Hash
        if (!map.isEmpty()) {
            redisConnector.opsForHash().putAll(key, map);
        }
    }

}
