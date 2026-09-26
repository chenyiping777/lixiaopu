package com.lixiaopu.controller;


import com.lixiaopu.common.result.Result;
import com.lixiaopu.common.utils.AliyunOSSUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
@Slf4j
public class ToolController {

    @Autowired
    private AliyunOSSUtils aliyunOSSUtils;

    @PostMapping("/upload/image")
    public Result uploadAvatar(MultipartFile file) throws Exception {
        log.info("用户头像上传: {}", file.getOriginalFilename());
        if (file.isEmpty()) {
            return Result.error("文件不能为空");
        }
        if (!file.getContentType().startsWith("image/")) {
            return Result.error("只能上传图片");
        }
        // 指定目录 avatar
        String url = aliyunOSSUtils.upload(file, "avatar");
        return Result.success(url);
    }
}
