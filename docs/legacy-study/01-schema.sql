-- 栗小铺学习版，MySQL 8.0.16+；在独立数据库中执行。
-- 不删除已有表；IF NOT EXISTS 不会更新已有表结构。
CREATE DATABASE IF NOT EXISTS lixiaopu CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE lixiaopu;
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS shop_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
    username VARCHAR(64) NOT NULL COMMENT '登录名',
    password_hash VARCHAR(255) NOT NULL COMMENT '密码散列，禁止存储明文',
    nickname VARCHAR(64) NOT NULL DEFAULT '' COMMENT '昵称',
    phone VARCHAR(20) DEFAULT NULL COMMENT '手机号',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '0禁用，1正常',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_username (username),
    CONSTRAINT ck_user_status CHECK (status IN (0, 1))
) ENGINE=InnoDB COMMENT='商城用户';

CREATE TABLE IF NOT EXISTS category (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(64) NOT NULL COMMENT '分类名称，学习版采用单级分类',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序，越小越靠前',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '0停用，1启用',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_category_name (name),
    CONSTRAINT ck_category_status CHECK (status IN (0, 1))
) ENGINE=InnoDB COMMENT='商品分类';

CREATE TABLE IF NOT EXISTS product (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    category_id BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL COMMENT '商品名称',
    description TEXT COMMENT '商品详情',
    cover_image VARCHAR(1024) DEFAULT NULL COMMENT '封面图片地址',
    price DECIMAL(10,2) NOT NULL COMMENT '单价，单位元',
    stock INT NOT NULL DEFAULT 0 COMMENT '可售库存',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0下架，1上架',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_product_category_status (category_id, status),
    CONSTRAINT fk_product_category FOREIGN KEY (category_id) REFERENCES category(id),
    CONSTRAINT ck_product_price CHECK (price >= 0),
    CONSTRAINT ck_product_stock CHECK (stock >= 0),
    CONSTRAINT ck_product_status CHECK (status IN (0, 1))
) ENGINE=InnoDB COMMENT='商品，学习版每个商品只有一种规格';

CREATE TABLE IF NOT EXISTS user_address (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    receiver_name VARCHAR(64) NOT NULL,
    receiver_phone VARCHAR(20) NOT NULL,
    province VARCHAR(64) NOT NULL,
    city VARCHAR(64) NOT NULL,
    district VARCHAR(64) NOT NULL,
    detail_address VARCHAR(255) NOT NULL,
    is_default TINYINT NOT NULL DEFAULT 0 COMMENT '0否，1是',
    default_user_id BIGINT GENERATED ALWAYS AS (CASE WHEN is_default = 1 THEN user_id ELSE NULL END) STORED,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_address_user (user_id),
    UNIQUE KEY uk_address_default_user (default_user_id),
    CONSTRAINT fk_address_user FOREIGN KEY (user_id) REFERENCES shop_user(id),
    CONSTRAINT ck_address_default CHECK (is_default IN (0, 1))
) ENGINE=InnoDB COMMENT='收货地址，每个用户最多一个默认地址';

CREATE TABLE IF NOT EXISTS cart_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL DEFAULT 1,
    selected TINYINT NOT NULL DEFAULT 1 COMMENT '是否勾选',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_cart_user_product (user_id, product_id),
    CONSTRAINT fk_cart_user FOREIGN KEY (user_id) REFERENCES shop_user(id),
    CONSTRAINT fk_cart_product FOREIGN KEY (product_id) REFERENCES product(id),
    CONSTRAINT ck_cart_quantity CHECK (quantity > 0),
    CONSTRAINT ck_cart_selected CHECK (selected IN (0, 1))
) ENGINE=InnoDB COMMENT='购物车条目，同一用户的同一商品合并数量';

CREATE TABLE IF NOT EXISTS shop_order (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_no VARCHAR(64) NOT NULL COMMENT '服务端生成的唯一订单号',
    user_id BIGINT NOT NULL,
    goods_amount DECIMAL(12,2) NOT NULL COMMENT '商品合计',
    freight_amount DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '运费',
    payable_amount DECIMAL(12,2) NOT NULL COMMENT '应付金额',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0待支付，1待发货，2待收货，3已完成，4已取消',
    receiver_name VARCHAR(64) NOT NULL COMMENT '收件人快照',
    receiver_phone VARCHAR(20) NOT NULL COMMENT '手机号快照',
    receiver_address VARCHAR(512) NOT NULL COMMENT '完整收货地址快照',
    remark VARCHAR(255) NOT NULL DEFAULT '',
    paid_time DATETIME DEFAULT NULL,
    shipped_time DATETIME DEFAULT NULL,
    completed_time DATETIME DEFAULT NULL,
    cancelled_time DATETIME DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_order_user_time (user_id, create_time),
    KEY idx_order_status_time (status, create_time),
    CONSTRAINT fk_order_user FOREIGN KEY (user_id) REFERENCES shop_user(id),
    CONSTRAINT ck_order_amount CHECK (goods_amount >= 0 AND freight_amount >= 0 AND payable_amount = goods_amount + freight_amount),
    CONSTRAINT ck_order_status CHECK (status IN (0, 1, 2, 3, 4))
) ENGINE=InnoDB COMMENT='订单，收货信息独立保存，不依赖当前地址';

CREATE TABLE IF NOT EXISTS order_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    product_name VARCHAR(128) NOT NULL COMMENT '下单时商品名称快照',
    product_image VARCHAR(1024) DEFAULT NULL COMMENT '下单时图片快照',
    price DECIMAL(10,2) NOT NULL COMMENT '下单时成交单价',
    quantity INT NOT NULL,
    subtotal DECIMAL(20,2) GENERATED ALWAYS AS (price * quantity) STORED COMMENT '行小计，自动计算',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_order_item_order (order_id),
    CONSTRAINT fk_item_order FOREIGN KEY (order_id) REFERENCES shop_order(id),
    CONSTRAINT fk_item_product FOREIGN KEY (product_id) REFERENCES product(id),
    CONSTRAINT ck_item_price CHECK (price >= 0),
    CONSTRAINT ck_item_quantity CHECK (quantity > 0)
) ENGINE=InnoDB COMMENT='订单明细';
