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
import com.lixiaopu.pojo.dto.CollectionDTO;
import com.lixiaopu.pojo.entity.Collection;
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
public class CollectionServiceImpl extends ServiceImpl<CollectionMapper, Collection>
		implements CollectionService {

	@Autowired
	private ProductMapper productMapper;

	@Override
	public Result addCollection(Long productId) {
		//添加商品到收藏
		long currentUserId = CurrentHolder.getCurrentUser().getId();
		Collection collection = new Collection();
		collection.setProductId(productId);
		collection.setUserId(currentUserId);
		save(collection);
		return Result.success();
	}

	@Override
	public Result deleteCollection(String productIds) {
		//商品id之间以逗号分隔,取出商品id组合成list
		String[] idStrArray = productIds.split(",");
		//转成list
        List<Long> productIdList = Arrays
				.stream(idStrArray)
				.map(Long::valueOf)
				.toList();
		long currentUserId = CurrentHolder.getCurrentUser().getId();
		LambdaQueryWrapper<Collection> wrapper = Wrappers.lambdaQuery();
		wrapper.eq(Collection::getUserId,currentUserId)
				.in(Collection::getProductId,productIdList);
		remove(wrapper);
		return Result.success();
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
		Page<Collection> pageParam = new Page<>(pageNo,pageSize);

		long currentUserId = CurrentHolder.getCurrentUser().getId();

		//查看商品收藏列表，不需要分类，按创建时间时间从大到小排序
		LambdaQueryWrapper<Collection> wrapper = Wrappers.lambdaQuery(Collection.class)
				.eq(Collection::getUserId,currentUserId)
				.orderByDesc(Collection::getCreateTime);

		//开始分页,停止分页的逻辑是什么我们前端只传过来了页码和一页的大小
		//分页本身不会自动停止；前端如果继续发请求（继续传 pageNo=6、pageNo=7），
		//后端依旧会执行 count+limit 查询，只是返回的 records 是空数组。
		//分页终止是前端控制的：前端拿到返回的`total`/`pages`，
		//当 `pageNo >= pages`，前端就不再发起下一页请求。
		IPage<Collection> page = page(pageParam,wrapper);
		List<Collection> records = page.getRecords();
		if(CollectionUtils.isEmpty(records)){
			return Result.success(page);
		}
		//提取所有商品id
		List<Long> productIdList = records.stream()
				.map(Collection::getProductId)
				.toList();
		//批量查询商品
		List<Product> productList = productMapper.selectBatchIds(productIdList);

		//List 查找需要遍历 `O(n)`；Map 根据 id 查找是 `O(1)`。
		//一页 10 条感觉差别不大，一页几十条、批量数据多时差距明显。
		Map<Long, Product> productMap = productList.stream()
				.collect(Collectors.toMap(Product::getId, Function.identity()));
		//组装DTO
		List<CollectionDTO> dtoList = records.stream().map(item->{
			CollectionDTO dto = new CollectionDTO();
			dto.setProductId(item.getProductId());
			Product product = productMap.get(item.getProductId());
			if(product != null){
				dto.setProductName(product.getName());
				dto.setProductImage(product.getImage());
				dto.setProductPrice(product.getPrice());
			}
			return dto;
		}).collect(Collectors.toList());
		//构建新分页对象
		PageVO<CollectionDTO> resultPage = new PageVO<>();
		resultPage.setTotal(page.getTotal());
		resultPage.setPages(page.getPages());
		resultPage.setList(dtoList);
		return Result.success(resultPage);


	}
}
