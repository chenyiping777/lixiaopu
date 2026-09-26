package com.lixiaopu.service;

import com.lixiaopu.common.result.Result;
import com.lixiaopu.model.entity.Collection;
import com.baomidou.mybatisplus.spring.service.IService;
import com.lixiaopu.model.query.PageQuery;

/**
* @author Mlpnk
* @description 针对表【collection(栗小铺 collection)】的数据库操作Service
* @createDate 2026-09-23 18:02:25
*/
public interface CollectionService extends IService<Collection> {

    Result addCollection(Long productId);

    Result deleteCollection(String productIds);

    Result getCollectionPage(PageQuery pageQuery);
}
