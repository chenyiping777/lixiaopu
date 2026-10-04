package com.lixiaopu.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.lixiaopu.common.result.Result;
import com.lixiaopu.pojo.entity.Product;
import com.lixiaopu.mapper.ProductMapper;
import com.lixiaopu.pojo.enums.CommonStatus;
import com.lixiaopu.pojo.query.ProductQuery;
import com.lixiaopu.pojo.vo.PageVO;
import com.lixiaopu.pojo.vo.ProductVO;
import com.lixiaopu.service.ProductService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
* @author Mlpnk
* @description 针对表【product(商品，学习版每个商品只有一种规格)】的数据库操作Service实现
* @createDate 2026-09-22 11:17:08
*/
@Service
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product>
    implements ProductService{

    @Override
    public Result getProductById(Long id) {

        Product product = getById(id);
        if (product == null || product.getStatus()!=CommonStatus.ACTIVE) return Result.error("商品不存在");
        return Result.success(toVO(product));
    }

    @Override
    public Result getProductsByCategoryId(ProductQuery productQuery) {
        long pageNo = productQuery.getPageNo();
        long pageSize = productQuery.getPageSize();

        //构建查询对象
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(productQuery.getCategoryId() != null, Product::getCategoryId, productQuery.getCategoryId())
                .eq(Product::getStatus, CommonStatus.ACTIVE);
        //排序
        String sortBy = productQuery.getSortBy();
        Boolean asc = productQuery.getAsc();
        if(sortBy!=null && !sortBy.isBlank()){
            if(Boolean.TRUE.equals(asc)){
                wrapper.orderByAsc(getSortColumn(sortBy));
            }else {
                wrapper.orderByDesc(getSortColumn(sortBy));
            }
        }
        //初始化分页参数,创建分页对象
        Page<Product> pageParam = new Page<>(pageNo,pageSize);
        IPage<Product> page = page(pageParam,wrapper);
        //将查询结果转换为ProductVO对象
        List<ProductVO> productVOList = page.getRecords().stream().map(this::toVO).toList();
        //封装自定义分页对象返回给前端
        PageVO<ProductVO> pageVO = new PageVO<>();
        //设置分页参数
        pageVO.setTotal(page.getTotal());
        pageVO.setPages(page.getPages());
        pageVO.setList(productVOList);
        return Result.success(pageVO);

    }

    @Override
    public Result searchProducts(ProductQuery productQuery) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<Product>()
                .eq(Product::getStatus, CommonStatus.ACTIVE);
        String keyword = productQuery.getKeyword();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(Product::getName, keyword.trim())
                    .or().like(Product::getDescription, keyword.trim()));
        }
        IPage<Product> page=page(new Page<>(productQuery.getPageNo(),productQuery.getPageSize()), wrapper);
        PageVO<ProductVO> result=new PageVO<>();
        result.setTotal(page.getTotal());result.setPages(page.getPages());
        result.setList(page.getRecords().stream().map(this::toVO).toList());
        return Result.success(result);
    }

    @Override
    public Result getHotProducts(Integer limit) {
        int pageSize = limit == null ? 10 : Math.max(1, Math.min(limit, 100));
        List<Product> products = lambdaQuery()
                .eq(Product::getStatus, CommonStatus.ACTIVE)
                .orderByDesc(Product::getSalesCount)
                .orderByDesc(Product::getId)
                .last("LIMIT " + pageSize)
                .list();
        return Result.success(products.stream().map(this::toVO).toList());
    }

    private ProductVO toVO(Product product) {
        ProductVO vo = BeanUtil.copyProperties(product, ProductVO.class);
        vo.setCoverImage(product.getImage());
        vo.setStatus(product.getStatus() == null ? null : product.getStatus().getNumber());
        return vo;
    }

    /**
     * 排序字段白名单映射
     */
    private SFunction<Product, ?> getSortColumn(String sortBy) {
        return switch (sortBy) {
            case "price" -> Product::getPrice;
            case "createTime" -> Product::getCreateTime;
            default -> Product::getId;
        };
    }
}




