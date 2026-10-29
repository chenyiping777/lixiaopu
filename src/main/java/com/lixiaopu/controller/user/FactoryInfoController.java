package com.lixiaopu.controller.user;

import com.lixiaopu.common.result.Result;
import com.lixiaopu.service.FactoryInfoService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/about/us")
@RequiredArgsConstructor
public class FactoryInfoController {
    private final FactoryInfoService factoryInfoService;

    @GetMapping("/introduce")
    public Result getFactoryInfo() {
        return factoryInfoService.getFactoryInfo();
    }
}
