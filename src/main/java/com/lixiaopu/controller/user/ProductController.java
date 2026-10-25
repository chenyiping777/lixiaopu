package com.lixiaopu.controller.user;

import com.lixiaopu.common.result.CursorCommonEntity;
import com.lixiaopu.common.result.CursorCommonResult;
import com.lixiaopu.common.result.Result;
import com.lixiaopu.common.result.SimpleCursorCommonResult;
import com.lixiaopu.infrastructure.es.document.ProductDocument;
import com.lixiaopu.infrastructure.es.mapstruct.EsCopyMapper;
import com.lixiaopu.pojo.dto.AppendProductFirstCommentDTO;
import com.lixiaopu.pojo.dto.FirstProductCommentDTO;
import com.lixiaopu.pojo.dto.SecondProductCommentDTO;
import com.lixiaopu.pojo.vo.SimpleProductVO;
import com.lixiaopu.service.ProductCommentService;
import com.lixiaopu.service.ProductHistoryService;
import com.lixiaopu.service.ProductSearchKeywordService;
import com.lixiaopu.service.ProductService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/products")
public class ProductController {
    @Autowired
    private ProductService productService;
    @Autowired
    private ProductSearchKeywordService productSearchKeywordService;
    @Autowired
    private ProductCommentService productCommentService;
    @Autowired
    private EsCopyMapper esCopyMapper;

    @Autowired
    private ProductHistoryService productHistoryService;

    /** 当前登录用户最近浏览的商品，按最近浏览时间倒序。 */
    @GetMapping("/history")
    public Result getHistory(CursorCommonEntity cursor) {
        return Result.success(productHistoryService.recent(cursor));
    }

    /**
     * 根据id获取商品详情
     * @param id
     * @return
     */
    @GetMapping("/{id}")
    public Result getProductById(@PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        var currentUser = com.lixiaopu.common.context.CurrentHolder.getCurrentUser();
        if (currentUser != null) userId = currentUser.getId();
        return productService.getProductDetail(id.toString(), userId);
    }

    /**
     * 获取热门商品
     * @return
     */
    @GetMapping("/product/hot")
    public Result getHotProduct(@RequestParam(value = "limit", defaultValue = "10") Integer limit) {
        List<ProductDocument> hotProduct = productService.getHotProduct(limit);
        List<SimpleProductVO> simpleProductVOS =
                hotProduct.stream()
                        .map(esCopyMapper::toSimpleProduct)
                        .toList();
        return Result.success(simpleProductVOS);
    }

    /**
     * 获取列表商品简单介绍
     * 未登录用户查看“最近浏览”时，前端先读取 localStorage 中保存的商品 ID，再请求：
     */
    @GetMapping("/product/brief/list")
    public Result getBriefProduct(@RequestParam(value = "productIds", defaultValue = "") String productIds) {
        return productService.getBriefProduct(productIds);
    }

    /**
     * 获取分类商品简单介绍
     * @param isFirstCategoryId 是否一级分类
     * @return
     *
     * 前端携带 categoryId = 1 查询商品
     * → 后端从 Redis 获取其下二级分类 ID：[11, 12]
     * → ES 查询 categoryId 属于 [11, 12] 的商品
     * → 返回苹果、香蕉分类下的商品
     */
    @GetMapping({"/product/category/list", "/product/categroy/list"})
    public Result getCategorySimpleProduct(@Valid CursorCommonEntity cursorCommonEntity
            , Long categoryId, boolean isFirstCategoryId) {
        CursorCommonResult cursorCommonResult = productService.getCategorySimpleProduct(cursorCommonEntity, categoryId,isFirstCategoryId);
        return Result.success(cursorCommonResult);
    }

    /**
     * 搜索商品列表
     * ES match 分词查询，不是商品名称完全相等查询，也不是 SQL 的任意子串匹配，具体匹配结果取决于索引分词。
     * 商品名称关键词匹配	        支持
     * 按销量、价格、更新时间排序	支持
     * 游标分页、下拉加载	        支持
     * 搜索商品卖点、描述、规格	    不支持
     */
    @GetMapping("/product/search")
    public Result searchProductList(@Valid CursorCommonEntity cursorCommonEntity , String keyword) {
        CursorCommonResult cursorCommonResult = productService.searchProductList(cursorCommonEntity, keyword);
        return Result.success(cursorCommonResult);
    }

    /**
     * 获取相关商品
     *
     * @param productName 商品名
     * @param limit       查询数 默认 10
     * @return 简单商品列表
     */
    @GetMapping("/product/related")
    public Result getProductRelated(@RequestParam("productName") String productName
            , @RequestParam(value = "limit", defaultValue = "10") Integer limit) {
        return Result.success(productService.getProductRelated(productName, limit));
    }


    /**
     * 滚动查询商品列表
     *
     * @param beginId
     * @param querySize
     * @return
     */
    @GetMapping("/product/scroll/query/list")
    public Result getSimpleProductByScrollQuery(Long beginId , @RequestParam(defaultValue = "80") Integer querySize) {
        SimpleCursorCommonResult simpleProductByScrollQuery = productService.getSimpleProductByScrollQuery(beginId, querySize);
        return Result.success(simpleProductByScrollQuery);
    }

    /**
     * 用户获取热门搜索关键词列表
     * @return
     */
    @GetMapping("/product/user/keyword/list")
    public Result getProductSearchKeywordListUser() {
        return productSearchKeywordService.getProductSearchKeywordListUser();
    }

    /**
     * 用户做分类查询商品一级评论
     * @param cursorCommonEntity
     * @param productId
     * @return
     */
    @PostMapping("/user/product/comment/firstComment/show")
    public Result getProductCommentBySortType(@RequestBody @Valid CursorCommonEntity cursorCommonEntity
            , @RequestParam @NotBlank String productId) {
        return productCommentService.getProductCommentBySortType(cursorCommonEntity, productId);
    }

    /**
     * 查询指定一级评论下二级评论
     * @param firstCommentId
     * @return
     */
    @PostMapping("/user/product/comment/secondComment/show")
    public Result getSecondComment(@RequestParam @NotBlank String firstCommentId
            , @RequestBody @NotNull CursorCommonEntity cursorCommonEntity) {
        return productCommentService.getSecondComment(firstCommentId, cursorCommonEntity);
    }

    /**
     *查询指定一级评论下的用户追评
     * @return
     */
    @GetMapping("/user/product/comment/appendComment/show")
    public Result getAppendComment(@RequestParam @NotBlank String firstCommentId) {
        return productCommentService.getAppendComment(firstCommentId);
    }

    /**
     * 用户发表一级商品评论
     * @param firstProductCommentDTO
     * @return
     */
    @PostMapping("/user/product/comment/firstComment/save")
    public Result saveProductFirstComment(@RequestBody @Valid FirstProductCommentDTO firstProductCommentDTO) {
        return productCommentService.saveProductFirstComment(firstProductCommentDTO);
    }

    /**
     * 用户发表二级以上商品评论
     */
    @PostMapping("/user/product/comment/secondComment/save")
    public Result saveProductSecondComment(@RequestBody @Valid SecondProductCommentDTO secondProductCommentDTO) {
        return productCommentService.saveProductSecondComment(secondProductCommentDTO);
    }

    /**
     * 用户对一级评论进行追加
     * @param appendProductFirstCommentDTO
     * @return
     */
    @PostMapping("/user/product/comment/firstComment/append")
    public Result appendProductFirstComment(@Valid @RequestBody @NotNull AppendProductFirstCommentDTO appendProductFirstCommentDTO) {
        return productCommentService.appendProductFirstComment(appendProductFirstCommentDTO);
    }

    /**
     * 查询商品评论总数
     * @param productId
     * @return
     */
    @GetMapping("/user/product/comment/count/show")
    public Result getProductCommentCount(@RequestParam @NotBlank String productId) {
        return productCommentService.getProductCommentCount(productId);
    }

    /**
     * 用户对商品评论点赞
     * 将当前用户对某条评论的点赞状态设为点赞或取消点赞，同时更新 Redis 中的点赞数和点赞用户集合。
     * 1 点赞，0 取消点赞
     */
    @PutMapping("/user/product/comment/like")
    public Result updateProductCommentLike(@RequestParam @NotBlank String productCommentId
            , @RequestParam @NotNull Integer isLike
            , @RequestParam @NotNull Integer isFirstComment) {
        return productCommentService.updateProductCommentLike(productCommentId, isLike, isFirstComment);
    }
}
