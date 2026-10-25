package com.lixiaopu.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.lixiaopu.common.context.CurrentHolder;
import com.lixiaopu.common.result.Result;
import com.lixiaopu.mapper.CollectionMapper;
import com.lixiaopu.mapper.ProductMapper;
import com.lixiaopu.infrastructure.redis.connect.RedisConnector;
import com.lixiaopu.infrastructure.redis.generator.RedisKeyGenerator;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.lixiaopu.pojo.vo.CollectionProductVO;
import com.lixiaopu.pojo.entity.CollectionProduct;
import com.lixiaopu.pojo.entity.Product;
import com.lixiaopu.pojo.query.PageQuery;
import com.lixiaopu.pojo.vo.PageVO;
import com.lixiaopu.service.CollectionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CollectionServiceImpl extends ServiceImpl<CollectionMapper, CollectionProduct>
		implements CollectionService {

	@Autowired
	private ProductMapper productMapper;

	@Override
	@Transactional
	public Result addCollection(Long productId) {
		//添加商品到收藏
		long currentUserId = CurrentHolder.getCurrentUser().getId();
		CollectionProduct collection = new CollectionProduct();
		collection.setProductId(productId);
		collection.setUserId(currentUserId);
		if (!save(collection)) return Result.error();
		invalidateCollectionCache(List.of(productId));
		return Result.success();
	}

	@Override
	@Transactional
	public Result deleteCollection(String productIds) {
		//商品id之间以逗号分隔,取出商品id组合成list
		String[] idStrArray = productIds.split(",");
		//转成list
        List<Long> productIdList = Arrays
				.stream(idStrArray)
				.map(Long::valueOf)
				.toList();
		long currentUserId = CurrentHolder.getCurrentUser().getId();
		LambdaQueryWrapper<CollectionProduct> wrapper = Wrappers.lambdaQuery();
		wrapper.eq(CollectionProduct::getUserId,currentUserId)
				.in(CollectionProduct::getProductId,productIdList);
		remove(wrapper);
		invalidateCollectionCache(productIdList);
		return Result.success();
 	}

    /** 事务提交后失效缓存，避免其他请求在提交前回填旧收藏数据。 */
    private void invalidateCollectionCache(List<Long> productIds) {
        Runnable invalidate = () -> productIds.stream().distinct()
                .forEach(id -> RedisConnector.delete(RedisKeyGenerator.productCollection(id)));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { invalidate.run(); }
            });
        } else {
            invalidate.run();
        }
    }

	@Override
	public Result getCollectionPage(PageQuery pageQuery) {
		//封装分页对象
		//只需要在分页对象里每一条记录里有个商品id就行吗，
		//那加载分页的时候，在收藏里展示的商品，
		//是又通过什么接口展示的，后续点开又会再根据商品id查看商品详情
		//所以这里封装一下需要在前端展示的商品概要（商品名，图片，价格）

		long pageSize = pageQuery.getPageSize();
		long pageNo = pageQuery.getPageNo();
		//构建分页对象
		Page<CollectionProduct> pageParam = new Page<>(pageNo,pageSize);

		long currentUserId = CurrentHolder.getCurrentUser().getId();

		//查看商品收藏列表，不需要分类，按创建时间时间从大到小排序
		LambdaQueryWrapper<CollectionProduct> wrapper = Wrappers.lambdaQuery(CollectionProduct.class)
				.eq(CollectionProduct::getUserId,currentUserId)
				.orderByDesc(CollectionProduct::getCreateTime);

		//开始分页,停止分页的逻辑是什么我们前端只传过来了页码和一页的大小
		//分页本身不会自动停止；前端如果继续发请求（继续传 pageNo=6、pageNo=7），
		//后端依旧会执行 count+limit 查询，只是返回的 records 是空数组。
		//分页终止是前端控制的：前端拿到返回的`total`/`pages`，
		//当 `pageNo >= pages`，前端就不再发起下一页请求。
		IPage<CollectionProduct> page = page(pageParam,wrapper);
		List<CollectionProduct> records = page.getRecords();
		if(CollectionUtils.isEmpty(records)){
			return Result.success(page);
		}
		//提取所有商品id
		List<Long> productIdList = records.stream()
				.map(CollectionProduct::getProductId)
				.toList();
		//批量查询商品
		List<Product> productList = productMapper.selectBatchIds(productIdList);

		//List 查找需要遍历 `O(n)`；Map 根据 id 查找是 `O(1)`。
		//一页 10 条感觉差别不大，一页几十条、批量数据多时差距明显。
		Map<Long, Product> productMap = productList.stream()
				.collect(Collectors.toMap(Product::getId, Function.identity()));
		//组装收藏商品展示对象
		List<CollectionProductVO> voList = records.stream().map(item->{
			CollectionProductVO vo = new CollectionProductVO();
			vo.setProductId(item.getProductId());
			Product product = productMap.get(item.getProductId());
			if(product != null){
				vo.setProductName(product.getName());
				vo.setProductImage(product.getImage());
				vo.setProductPrice(product.getPrice());
			}
			return vo;
		}).collect(Collectors.toList());
		//构建新分页对象
		PageVO<CollectionProductVO> resultPage = new PageVO<>();
		resultPage.setTotal(page.getTotal());
		resultPage.setPages(page.getPages());
		resultPage.setList(voList);
		return Result.success(resultPage);


	}
}
