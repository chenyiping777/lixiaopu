package com.lixiaopu.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 商品一级评论提交 DTO
 * 提交商品的一级评价，包含订单号、评分、图片等。保存时固定 parentId = 0。
 *
 * 一级评价 A：刚收到，挺好吃。
 *   追评：吃了一周，准备回购。
 *   二级回复 B：请问保质期多久？
 *   二级回复 C（回复 B）：保质期三个月。
 * B、C 的 parentId 都填 一级评价 A 的 ID；C 再通过 replyUserId、replyUserNickname 指定回复 B 的作者，所以不会增加第三层展示。
 * 如果是回复那条追评：现有参数没有专门的“被回复追评 ID”，因此可以挂在 A 下面交流，但无法明确记录“这条回复针对哪条追评”。
 */
@Data
public class FirstProductCommentDTO {

    @NotBlank(message = "商品 ID不能为空")
    private String productId;

    @NotBlank(message = "商品规格 ID不能为空")
    private String productSpecId;

    @NotBlank(message = "商品规格文本不能为空")
    private String productSpecText;

    @NotBlank(message = "订单单号不能为空")
    private String orderNo;

    @NotBlank(message = "用户昵称不能为空")
    private String userNickname;

    @NotBlank(message = "用户头像不能为空")
    private String userAvatar;

    @NotBlank(message = "评论内容不能为空")
    private String content;

    private String imageUrls;

    private int rating;

    //匿名只影响展示身份，真实 userId 仍会保存，用于判断评论归属、追评权限等
    private int isAnonymous = 0;
}