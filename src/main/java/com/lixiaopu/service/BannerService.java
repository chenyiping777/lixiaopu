package com.lixiaopu.service;

import com.lixiaopu.common.result.Result;
import com.lixiaopu.pojo.dto.*;

public interface BannerService {
    Result getBannerList();
    Result getBannerListAdmin(int pageNum, int pageSize);
    Result addBanner(BannerDTO dto);
    Result updateBanner(BannerDTO dto);
    Result deleteBanner(Long id);
    Result updateSort(BannerSortDTO dto);
    Result updateStatus(BannerStatusDTO dto);
}
