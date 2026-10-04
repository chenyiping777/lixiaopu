-- 栗小铺完整业务模型；字段与 ChestnutShopWX 的实体、Mapper 名称对齐。
-- MySQL 8.0；全新库直接执行。已有学习版库先执行 docs/sql/00-preserve-study.sql。
CREATE DATABASE IF NOT EXISTS lixiaopu CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE lixiaopu;
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `sys_user` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(64) NULL,
  password VARCHAR(100) NULL,
  nickname VARCHAR(64) NOT NULL DEFAULT '',
  avatar VARCHAR(1024) NULL,
  openid VARCHAR(128) NULL,
  phone VARCHAR(20) NULL,
  user_type TINYINT NOT NULL DEFAULT 3,
  is_enable TINYINT NOT NULL DEFAULT 1,
  auth_version BIGINT NOT NULL DEFAULT 0,
  first_login_time DATETIME NULL,
  last_login_time DATETIME NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_sys_user_username(username),
  UNIQUE KEY uk_sys_user_openid(openid),
  UNIQUE KEY uk_sys_user_phone(phone),
  CONSTRAINT ck_sys_user_enable CHECK(is_enable IN(0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='栗小铺 sys_user';

CREATE TABLE IF NOT EXISTS `sys_role` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  role_code VARCHAR(64) NOT NULL,
  role_name VARCHAR(64) NOT NULL,
  sort INT NOT NULL DEFAULT 0,
  is_enable TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_sys_role_code(role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='栗小铺 sys_role';

CREATE TABLE IF NOT EXISTS `sys_permission` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  perm_code VARCHAR(128) NOT NULL,
  perm_name VARCHAR(100) NOT NULL,
  perm_type TINYINT NOT NULL DEFAULT 2,
  parent_id BIGINT NOT NULL DEFAULT 0,
  sort INT NOT NULL DEFAULT 0,
  is_enable TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_sys_permission_code(perm_code),
  KEY idx_permission_parent(parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='栗小铺 sys_permission';

CREATE TABLE IF NOT EXISTS `sys_user_role` (
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  PRIMARY KEY(user_id,role_id),
  KEY idx_ur_role(role_id),
  FOREIGN KEY(user_id) REFERENCES sys_user(id),
  FOREIGN KEY(role_id) REFERENCES sys_role(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='栗小铺 sys_user_role';

CREATE TABLE IF NOT EXISTS `sys_role_permission` (
  role_id BIGINT NOT NULL,
  perm_id BIGINT NOT NULL,
  PRIMARY KEY(role_id,perm_id),
  KEY idx_rp_perm(perm_id),
  FOREIGN KEY(role_id) REFERENCES sys_role(id),
  FOREIGN KEY(perm_id) REFERENCES sys_permission(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='栗小铺 sys_role_permission';

CREATE TABLE IF NOT EXISTS `category` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(100) NOT NULL,
  parent_id BIGINT NOT NULL DEFAULT 0,
  icon_url VARCHAR(1024) NULL DEFAULT '/static/images/default-category.png',
  sort INT NOT NULL DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_category_parent_sort(parent_id,sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='栗小铺 category';

CREATE TABLE IF NOT EXISTS `product` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  category_id BIGINT NOT NULL,
  name VARCHAR(128) NOT NULL,
  sell_point VARCHAR(255) NULL,
  price DECIMAL(10,2) NOT NULL,
  enterprise_price DECIMAL(10,2) NULL,
  stock BIGINT NOT NULL DEFAULT 0,
  cover_image VARCHAR(1024) NULL,
  description LONGTEXT NULL,
  view_count BIGINT NOT NULL DEFAULT 0,
  sales_count BIGINT NOT NULL DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_product_category_status(category_id,status),
  KEY idx_product_name(name),
  FOREIGN KEY(category_id) REFERENCES category(id),
  CHECK(price>=0 AND stock>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='栗小铺 product';

CREATE TABLE IF NOT EXISTS `product_image` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  product_id BIGINT NOT NULL,
  image_url VARCHAR(1024) NOT NULL,
  sort INT NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_product_image_product_sort(product_id,sort),
  FOREIGN KEY(product_id) REFERENCES product(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品图册';

CREATE TABLE IF NOT EXISTS `product_spec` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  product_id BIGINT NOT NULL,
  spec_text VARCHAR(255) NOT NULL,
  price DECIMAL(10,2) NOT NULL,
  enterprise_price DECIMAL(10,2) NULL,
  stock INT NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_spec_product(product_id),
  FOREIGN KEY(product_id) REFERENCES product(id),
  CHECK(price>=0 AND stock>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='栗小铺 product_spec';

CREATE TABLE IF NOT EXISTS `address` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  receiver VARCHAR(64) NOT NULL,
  phone VARCHAR(20) NOT NULL,
  province VARCHAR(64) NOT NULL,
  city VARCHAR(64) NOT NULL,
  district VARCHAR(64) NOT NULL,
  detail_address VARCHAR(255) NOT NULL,
  is_default TINYINT NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_address_user(user_id),
  FOREIGN KEY(user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='栗小铺 address';

CREATE TABLE IF NOT EXISTS `cart` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  spec_id BIGINT NULL,
  quantity INT NOT NULL DEFAULT 1,
  checked TINYINT NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_cart_user(user_id),
  KEY idx_cart_product(product_id),
  FOREIGN KEY(user_id) REFERENCES sys_user(id),
  FOREIGN KEY(product_id) REFERENCES product(id),
  FOREIGN KEY(spec_id) REFERENCES product_spec(id),
  CHECK(quantity>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='栗小铺 cart';

CREATE TABLE IF NOT EXISTS `order` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_no VARCHAR(64) NOT NULL,
  user_id BIGINT NOT NULL,
  address_id BIGINT NULL,
  total_goods_amount DECIMAL(12,2) NOT NULL,
  freight DECIMAL(12,2) NOT NULL DEFAULT 0,
  total_amount DECIMAL(12,2) NOT NULL,
  status TINYINT NOT NULL DEFAULT 0,
  pay_type TINYINT NULL,
  pay_time DATETIME NULL,
  deliver_time DATETIME NULL,
  receive_time DATETIME NULL,
  cancel_time DATETIME NULL,
  cancel_reason VARCHAR(255) NULL,
  remark VARCHAR(255) NULL,
  is_evaluate TINYINT NOT NULL DEFAULT 0,
  is_deleted TINYINT NOT NULL DEFAULT 0,
  logistics_company VARCHAR(128) NULL,
  logistics_no VARCHAR(128) NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_order_no(order_no),
  KEY idx_order_user_time(user_id,create_time),
  KEY idx_order_status(status),
  FOREIGN KEY(user_id) REFERENCES sys_user(id),
  FOREIGN KEY(address_id) REFERENCES address(id),
  CHECK(total_goods_amount>=0 AND freight>=0 AND total_amount>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='栗小铺 order';

CREATE TABLE IF NOT EXISTS `order_item` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  spec_id BIGINT NULL,
  product_name VARCHAR(128) NOT NULL,
  spec_text VARCHAR(255) NULL,
  product_image VARCHAR(1024) NULL,
  price DECIMAL(10,2) NOT NULL,
  quantity INT NOT NULL,
  subtotal DECIMAL(12,2) NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_order_item_order(order_id),
  FOREIGN KEY(order_id) REFERENCES `order`(id),
  FOREIGN KEY(product_id) REFERENCES product(id),
  FOREIGN KEY(spec_id) REFERENCES product_spec(id),
  CHECK(price>=0 AND quantity>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='栗小铺 order_item';

CREATE TABLE IF NOT EXISTS `order_tracking` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  logistics_no VARCHAR(128) NULL,
  order_id BIGINT NOT NULL,
  logistics_status TINYINT NULL,
  location VARCHAR(255) NULL,
  description VARCHAR(500) NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_tracking_order(order_id),
  FOREIGN KEY(order_id) REFERENCES `order`(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='栗小铺 order_tracking';

CREATE TABLE IF NOT EXISTS `banner` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(128) NOT NULL,
  image_url VARCHAR(1024) NOT NULL,
  link_url VARCHAR(1024) NULL,
  link_type VARCHAR(32) NULL,
  sort INT NOT NULL DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_banner_status_sort(status,sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='栗小铺 banner';

CREATE TABLE IF NOT EXISTS `factory_info` (
  id INT PRIMARY KEY AUTO_INCREMENT,
  image VARCHAR(1024) NULL,
  factory_name VARCHAR(128) NOT NULL,
  introduction LONGTEXT NULL,
  service_hotline VARCHAR(32) NULL,
  official_wechat VARCHAR(128) NULL,
  address VARCHAR(500) NULL,
  copyright_info VARCHAR(255) NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='栗小铺 factory_info';

CREATE TABLE IF NOT EXISTS `notice` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(128) NOT NULL,
  content LONGTEXT NOT NULL,
  status TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='栗小铺 notice';

CREATE TABLE IF NOT EXISTS `feedback` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  content TEXT NOT NULL,
  image_urls JSON NULL,
  contact VARCHAR(128) NULL,
  reply_content TEXT NULL,
  reply_admin_id BIGINT NULL,
  status TINYINT NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  reply_time DATETIME NULL,
  KEY idx_feedback_user(user_id),
  FOREIGN KEY(user_id) REFERENCES sys_user(id),
  FOREIGN KEY(reply_admin_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='栗小铺 feedback';

CREATE TABLE IF NOT EXISTS `collection` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_collection_user_product(user_id,product_id),
  FOREIGN KEY(user_id) REFERENCES sys_user(id),
  FOREIGN KEY(product_id) REFERENCES product(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='栗小铺 collection';

CREATE TABLE IF NOT EXISTS `product_search_keyword` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  keyword VARCHAR(128) NOT NULL,
  is_hot TINYINT NOT NULL DEFAULT 0,
  is_show TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_search_keyword(keyword)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='栗小铺 product_search_keyword';

CREATE TABLE IF NOT EXISTS `product_comment` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  product_id BIGINT NOT NULL,
  product_spec_id BIGINT NULL,
  product_spec_text VARCHAR(255) NULL,
  order_no VARCHAR(64) NULL,
  user_id BIGINT NOT NULL,
  user_nickname VARCHAR(64) NULL,
  user_avatar VARCHAR(1024) NULL,
  parent_id BIGINT NOT NULL DEFAULT 0,
  reply_user_id BIGINT NULL,
  is_buyer TINYINT NOT NULL DEFAULT 1,
  is_append_comment TINYINT NOT NULL DEFAULT 0,
  is_anonymous TINYINT NOT NULL DEFAULT 0,
  is_good_review TINYINT NOT NULL DEFAULT 1,
  reply_user_nickname VARCHAR(64) NULL,
  rating TINYINT NOT NULL DEFAULT 5,
  content TEXT NOT NULL,
  image_urls JSON NULL,
  like_count INT NOT NULL DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_comment_product_time(product_id,create_time),
  KEY idx_comment_user(user_id),
  FOREIGN KEY(product_id) REFERENCES product(id),
  FOREIGN KEY(user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='栗小铺 product_comment';

CREATE TABLE IF NOT EXISTS `product_comment_append` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  comment_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  product_spec_id BIGINT NULL,
  order_no VARCHAR(64) NULL,
  user_id BIGINT NOT NULL,
  content TEXT NOT NULL,
  image_urls JSON NULL,
  status TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_append_comment(comment_id),
  FOREIGN KEY(comment_id) REFERENCES product_comment(id),
  FOREIGN KEY(user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='栗小铺 product_comment_append';

CREATE TABLE IF NOT EXISTS `product_comment_like` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  comment_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  status TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_comment_like(comment_id,user_id),
  FOREIGN KEY(comment_id) REFERENCES product_comment(id),
  FOREIGN KEY(user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='栗小铺 product_comment_like';

CREATE TABLE IF NOT EXISTS `coupon_mutex_group` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  group_name VARCHAR(128) NOT NULL,
  group_code BIGINT NOT NULL,
  remark VARCHAR(255) NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_coupon_mutex_code(group_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='栗小铺 coupon_mutex_group';

CREATE TABLE IF NOT EXISTS `coupon` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  coupon_no VARCHAR(64) NOT NULL,
  activity_name VARCHAR(128) NOT NULL,
  coupon_type TINYINT NOT NULL,
  face_value DECIMAL(10,2) NULL,
  discount_rate DECIMAL(6,4) NULL,
  max_discount DECIMAL(10,2) NULL,
  min_spend DECIMAL(10,2) NOT NULL DEFAULT 0,
  total_quota INT NOT NULL DEFAULT 0,
  used_quota INT NOT NULL DEFAULT 0,
  receive_quota INT NOT NULL DEFAULT 0,
  valid_mode TINYINT NOT NULL DEFAULT 1,
  valid_start DATETIME NULL,
  valid_end DATETIME NULL,
  receive_valid_days INT NULL,
  limit_per_person INT NOT NULL DEFAULT 1,
  user_limit_type TINYINT NOT NULL DEFAULT 0,
  use_scope TINYINT NOT NULL DEFAULT 0,
  mutex_group_code BIGINT NULL,
  status TINYINT NOT NULL DEFAULT 0,
  is_elimination TINYINT NOT NULL DEFAULT 0,
  release_time DATETIME NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_coupon_no(coupon_no),
  KEY idx_coupon_status_time(status,release_time),
  FOREIGN KEY(mutex_group_code) REFERENCES coupon_mutex_group(group_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='栗小铺 coupon';

CREATE TABLE IF NOT EXISTS `coupon_scope_detail` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  coupon_id BIGINT NOT NULL,
  scope_type TINYINT NOT NULL,
  target_id BIGINT NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_coupon_scope(coupon_id,scope_type,target_id),
  FOREIGN KEY(coupon_id) REFERENCES coupon(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='栗小铺 coupon_scope_detail';

CREATE TABLE IF NOT EXISTS `coupon_user` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  coupon_id BIGINT NOT NULL,
  valid_start DATETIME NULL,
  valid_end DATETIME NULL,
  use_status TINYINT NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_coupon_user_user(user_id,use_status),
  FOREIGN KEY(user_id) REFERENCES sys_user(id),
  FOREIGN KEY(coupon_id) REFERENCES coupon(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='栗小铺 coupon_user';

CREATE TABLE IF NOT EXISTS `coupon_order_rel` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  order_item_id BIGINT NULL,
  user_coupon_id BIGINT NOT NULL,
  activity_id BIGINT NULL,
  discount_amount DECIMAL(10,2) NOT NULL,
  rel_status TINYINT NOT NULL DEFAULT 0,
  use_time DATETIME NULL,
  refund_time DATETIME NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_coupon_order(order_id),
  FOREIGN KEY(order_id) REFERENCES `order`(id),
  FOREIGN KEY(order_item_id) REFERENCES order_item(id),
  FOREIGN KEY(user_coupon_id) REFERENCES coupon_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='栗小铺 coupon_order_rel';

CREATE TABLE IF NOT EXISTS `chat_session` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  contact_id BIGINT NOT NULL,
  last_msg_content VARCHAR(500) NULL,
  last_msg_time DATETIME NULL,
  unread_count_a INT NOT NULL DEFAULT 0,
  unread_count_b INT NOT NULL DEFAULT 0,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_chat_pair(user_id,contact_id),
  FOREIGN KEY(user_id) REFERENCES sys_user(id),
  FOREIGN KEY(contact_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='栗小铺 chat_session';

CREATE TABLE IF NOT EXISTS `chat_message` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  from_user_id BIGINT NOT NULL,
  to_user_id BIGINT NOT NULL,
  content TEXT NOT NULL,
  msg_type TINYINT NOT NULL DEFAULT 1,
  product_id BIGINT NULL,
  is_read TINYINT NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_chat_recipient(to_user_id,is_read,create_time),
  FOREIGN KEY(from_user_id) REFERENCES sys_user(id),
  FOREIGN KEY(to_user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='栗小铺 chat_message';

CREATE TABLE IF NOT EXISTS `mq_consumer_failed_msg` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  msg_id VARCHAR(128) NOT NULL,
  biz_id VARCHAR(128) NULL,
  topic VARCHAR(128) NOT NULL,
  tag VARCHAR(128) NULL,
  body LONGTEXT NOT NULL,
  retry_count INT NOT NULL DEFAULT 0,
  error_msg TEXT NULL,
  status TINYINT NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_failed_msg(msg_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='栗小铺 mq_consumer_failed_msg';
