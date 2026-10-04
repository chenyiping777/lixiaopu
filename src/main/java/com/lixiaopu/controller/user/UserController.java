package com.lixiaopu.controller.user;

import com.lixiaopu.common.result.Result;
import com.lixiaopu.pojo.dto.UserDetailDTO;
import com.lixiaopu.pojo.query.PageQuery;
import com.lixiaopu.service.CollectionService;
import com.lixiaopu.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private CollectionService collectionService;

    //获取用户详情：昵称，头像，电话
    @GetMapping("/detail")
    public Result getUserDetailById(){
        return userService.getUserDetailById();
    }
    //更改用户详情：昵称，头像，电话
    @PutMapping("/detail/update")
    public Result updateUserInfo(@RequestBody UserDetailDTO userDetailDTO){
        return userService.updateUserInfo(userDetailDTO);
    }

    //新增商品收藏
    @PostMapping("/collection/add")
    public Result addCollection(@RequestBody Long productId){

        return collectionService.addCollection(productId);
    }

    //删除商品收藏（可批量）
    @DeleteMapping("/collection/delete")
    //支持单个或批量删除收藏的商品（商品ID以逗号分隔
    public Result deleteCollection(@RequestBody String productIds){
        return collectionService.deleteCollection(productIds);
    }

    //分页获取收藏列表
    @GetMapping("/collection/list")
    public Result getCollectionList(@RequestBody PageQuery pageQuery){
        return collectionService.getCollectionPage(pageQuery);
    }
}
