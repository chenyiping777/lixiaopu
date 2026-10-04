-- 以当前 pojo/entity/Cart.java、Category.java 的默认值为准。
-- 可重复执行；仅修改列默认值，不覆盖已有购物车勾选状态或分类图标。
USE lixiaopu;

ALTER TABLE `cart` ALTER COLUMN `checked` SET DEFAULT 0;
ALTER TABLE `category` ALTER COLUMN `icon_url` SET DEFAULT '/static/images/default-category.png';

SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, COLUMN_DEFAULT, IS_NULLABLE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND ((TABLE_NAME = 'cart' AND COLUMN_NAME = 'checked')
    OR (TABLE_NAME = 'category' AND COLUMN_NAME = 'icon_url'))
ORDER BY TABLE_NAME, COLUMN_NAME;
