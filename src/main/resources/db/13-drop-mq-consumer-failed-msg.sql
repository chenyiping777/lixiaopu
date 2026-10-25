-- 已有库在部署移除失败表写入的新版本后执行；新库无需执行本脚本。
-- 删除表及其历史记录；商品 ES 补同步发送重试耗尽后改为记录 ERROR 日志。
DROP TABLE IF EXISTS `mq_consumer_failed_msg`;
