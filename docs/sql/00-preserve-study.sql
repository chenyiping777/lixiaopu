-- 仅供从此前 7 张学习表升级的现有库执行一次；先备份数据库。
-- 旧表统一改名为 *_study，数据原样保留；之后执行 shop-common/.../db/01-full-schema.sql。
USE lixiaopu;
RENAME TABLE
  category TO category_study,
  product TO product_study,
  shop_user TO shop_user_study,
  user_address TO user_address_study,
  cart_item TO cart_item_study,
  shop_order TO shop_order_study,
  order_item TO order_item_study;
