package com.lixiaopu.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lixiaopu.common.mapstruct.CopyMapper;
import com.lixiaopu.common.result.Result;
import com.lixiaopu.mapper.BannerMapper;
import com.lixiaopu.pojo.dto.*;
import com.lixiaopu.pojo.entity.Banner;
import com.lixiaopu.pojo.vo.PageVO;
import com.lixiaopu.service.BannerService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BannerServiceImpl implements BannerService {
    private final BannerMapper bannerMapper;
    private final CopyMapper copyMapper;

    // 两个独立应用共用数据库，直接读取可使后台修改立即反映到首页。
    @Override
    public Result getBannerList() {
        return Result.success(bannerMapper.selectList(Wrappers.<Banner>lambdaQuery()
                .eq(Banner::getStatus, 1).orderByAsc(Banner::getSort, Banner::getId)));
    }

    @Override
    public Result getBannerListAdmin(int pageNum, int pageSize) {
        if (pageNum < 1 || pageSize < 1 || pageSize > 100) {
            return Result.error(400, "页码须为正整数，每页数量为 1～100");
        }
        Page<Banner> page = bannerMapper.selectPage(new Page<>(pageNum, pageSize),
                Wrappers.<Banner>lambdaQuery().orderByAsc(Banner::getSort, Banner::getId));
        return Result.success(new PageVO<>(page.getTotal(), page.getPages(), page.getRecords()));
    }

    @Override
    public Result addBanner(BannerDTO dto) {
        Banner banner = copyMapper.bannerDTOToBanner(dto);
        banner.setId(null);
        if (banner.getSort() == null) banner.setSort(0);
        if (banner.getStatus() == null) banner.setStatus(1);
        return bannerMapper.insert(banner) == 1 ? Result.success(banner)
                : Result.error("轮播图保存失败");
    }

    @Override
    public Result updateBanner(BannerDTO dto) {
        if (dto.getId() == null || dto.getId() <= 0) return Result.error(400, "修改时必须提供有效的轮播图 ID");
        Banner banner = copyMapper.bannerDTOToBanner(dto);
        return bannerMapper.updateById(banner) == 1 ? Result.success(banner)
                : Result.error(404, "轮播图不存在或未更新");
    }

    @Override
    public Result deleteBanner(Long id) {
        return bannerMapper.deleteById(id) == 1 ? Result.success(Map.of("deleteId", id))
                : Result.error(404, "轮播图不存在");
    }

    @Override
    public Result updateSort(BannerSortDTO dto) {
        Banner banner = new Banner();
        banner.setId(dto.getId());
        banner.setSort(dto.getSort());
        return bannerMapper.updateById(banner) == 1
                ? Result.success(Map.of("bannerId", dto.getId(), "sort", dto.getSort()))
                : Result.error(404, "轮播图不存在或未更新");
    }

    @Override
    public Result updateStatus(BannerStatusDTO dto) {
        Banner banner = new Banner();
        banner.setId(dto.getId());
        banner.setStatus(dto.getStatus());
        return bannerMapper.updateById(banner) == 1
                ? Result.success(Map.of("bannerId", dto.getId(), "status", dto.getStatus()))
                : Result.error(404, "轮播图不存在或未更新");
    }
}
