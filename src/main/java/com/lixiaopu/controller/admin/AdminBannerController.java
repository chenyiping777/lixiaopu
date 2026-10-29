package com.lixiaopu.controller.admin;

import com.lixiaopu.common.result.Result;
import com.lixiaopu.pojo.dto.*;
import com.lixiaopu.service.BannerService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/banner")
@RequiredArgsConstructor
public class AdminBannerController {
    private final BannerService bannerService;

    @GetMapping("/list")
    public Result list(@RequestParam(defaultValue = "1") @Min(1) int pageNum,
                       @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize) {
        return bannerService.getBannerListAdmin(pageNum, pageSize);
    }

    @PostMapping("/add")
    public Result add(@Valid @RequestBody BannerDTO dto) {
        return bannerService.addBanner(dto);
    }

    @PutMapping("/update")
    public Result update(@Valid @RequestBody BannerDTO dto) {
        return bannerService.updateBanner(dto);
    }

    @DeleteMapping("/delete")
    public Result delete(@RequestParam @Positive Long id) {
        return bannerService.deleteBanner(id);
    }

    @PutMapping("/updateSort")
    public Result updateSort(@Valid @RequestBody BannerSortDTO dto) {
        return bannerService.updateSort(dto);
    }

    @PutMapping("/updateStatus")
    public Result updateStatus(@Valid @RequestBody BannerStatusDTO dto) {
        return bannerService.updateStatus(dto);
    }
}
