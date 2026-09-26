-- 仅在完整建表后执行一次；保留示例商品与 ID，方便原有页面继续查看。
USE lixiaopu;
INSERT INTO category(id,name,parent_id,sort,status,create_time,update_time)
SELECT id,name,0,sort_order,status,create_time,update_time FROM category_study;
INSERT INTO product(id,category_id,name,description,cover_image,price,stock,status,create_time,update_time)
SELECT id,category_id,name,description,cover_image,price,stock,status,create_time,update_time FROM product_study;
