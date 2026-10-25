package com.lixiaopu.controller.tool;


import com.lixiaopu.common.result.Result;
import com.lixiaopu.common.util.AliyunOSSUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
@Slf4j
public class ToolController {

    @Autowired
    private AliyunOSSUtil aliyunOSSUtils;

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

    /** 单张封面，返回 URL，保存到 Product.image。 */
    @PostMapping(value = "/admin/products/upload/cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result uploadCover(@RequestParam("file") MultipartFile file) throws IOException {
        validateImage(file);
        return Result.success(aliyunOSSUtils.upload(file, "goods/cover"));
    }

    /** 展示图支持单张或多张，按上传顺序返回 URL 列表，保存到 Product.imageUrls。 */
    @PostMapping(value = "/admin/products/upload/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result uploadImages(@RequestParam("files") List<MultipartFile> files) throws IOException {
        if (files.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "展示图片不能为空");
        // 先校验整批，避免后面的无效文件导致前面的文件已经上传。
        files.forEach(ToolController::validateImage);
        List<String> urls = new ArrayList<>(files.size());
        for (MultipartFile file : files) urls.add(aliyunOSSUtils.upload(file, "goods/images"));
        return Result.success(urls);
    }

    private static void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "图片不能为空");
        if (file.getContentType() == null || !file.getContentType().startsWith("image/"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "只能上传图片");
    }
}
