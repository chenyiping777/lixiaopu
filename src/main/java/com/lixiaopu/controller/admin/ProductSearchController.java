package com.lixiaopu.controller.admin;

import com.lixiaopu.common.result.Result;
import com.lixiaopu.service.admin.ProductSearchService;
import com.lixiaopu.pojo.query.ProductQuery;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/products")
@RequiredArgsConstructor
public class ProductSearchController {

    @Autowired
    private  ProductSearchService productSearchService;

    @PostMapping("/search")
    public Result search(@Valid @RequestBody ProductQuery query) {
        return Result.success(productSearchService.search(query));
    }
}
