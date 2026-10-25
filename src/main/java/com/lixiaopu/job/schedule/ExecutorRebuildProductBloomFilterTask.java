package com.lixiaopu.job.schedule;

import com.lixiaopu.common.util.BloomFilterUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lixiaopu.mapper.ProductMapper;
import com.lixiaopu.pojo.entity.Product;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class ExecutorRebuildProductBloomFilterTask implements ApplicationRunner {
    private final BloomFilterUtils bloomFilterUtils;
    private final ProductMapper productMapper;

    // 项目启动完成后立刻执行
    @Override
    public void run(ApplicationArguments args) {
        bloomFilterUtils.getProductBloomFilter();
        // 复用已有过滤器时也补入数据库现有 ID，覆盖停机期间新增的数据。
        // 保留当前项目对上下架商品的支持；是否启用由业务查询判断。
        var products = productMapper.selectList(Wrappers.<Product>lambdaQuery().select(Product::getId));
        products.forEach(product -> bloomFilterUtils.add(product.getId()));
        log.info("启动时补充商品布隆过滤器，共加载 {} 个商品 ID", products.size());
    }

    // 每天凌晨3点执行
    @Scheduled(cron = "${product.bloom.rebuild-cron:0 0 3 * * *}", zone = "${product.bloom.schedule-zone:Asia/Shanghai}")
    public void rebuild() {
        try {
            bloomFilterUtils.rebuild();
        } catch (Exception failure) {
            log.error("商品布隆过滤器重建失败，保留原过滤器", failure);
        }
    }
}
