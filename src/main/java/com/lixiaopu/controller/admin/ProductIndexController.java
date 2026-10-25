package com.lixiaopu.controller.admin;

import com.lixiaopu.common.result.Result;
import com.lixiaopu.service.admin.ProductIndexSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/** /api/admin 路径由已有认证过滤器保护。 */
@RestController
@RequestMapping("/api/admin/search-index")
@RequiredArgsConstructor
public class ProductIndexController {
    @Autowired
    private ProductIndexSyncService productIndexSyncService;
    @PostMapping("/import")
    public Result importProducts() {
        return Result.success(Map.of("indexed", productIndexSyncService.importAll())); }
}
