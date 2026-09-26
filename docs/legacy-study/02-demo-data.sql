-- 可选练习数据；仅给空的分类表与商品表添加样例，不覆盖已有数据。
USE lixiaopu;
SET NAMES utf8mb4;
START TRANSACTION;
INSERT INTO category (name, sort_order, status)
SELECT '栗子零食', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM category);

INSERT INTO product (category_id, name, description, price, stock, status)
SELECT c.id, '原味栗仁', '商品查询练习数据，100克装', 12.90, 100, 1
FROM category c
WHERE c.name = '栗子零食' AND NOT EXISTS (SELECT 1 FROM product);
COMMIT;
