-- 现有完整建表脚本已有两张聊天表。本迁移仅补聊天历史查询索引；已有库执行一次。
ALTER TABLE chat_message ADD INDEX idx_chat_pair_history(from_user_id,to_user_id,create_time,id);
