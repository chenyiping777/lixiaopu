window.LEGACY_API_CATALOG = [
  {
    "id": 1,
    "title": "账户密码登录 — 匿名可访问",
    "group": "account",
    "method": "POST",
    "path": "/api/user/login/account",
    "params": [],
    "body": "{\n  \"username\": \"demo_user\",\n  \"password\": \"DemoPass123!\"\n}",
    "auth": false,
    "optionalAuth": false,
    "notes": [
      "refreshToken 实际是 UUID，不是 JWT。密码错误返回业务码500；未使用 ResultCode 中的20001。"
    ],
    "upload": false,
    "browserUnsupported": false
  },
  {
    "id": 2,
    "title": "微信快捷登录 — 匿名可访问",
    "group": "account",
    "method": "POST",
    "path": "/api/user/login/wechat",
    "params": [],
    "body": "{\n  \"code\": \"WECHAT_LOGIN_CODE\",\n  \"nickName\": \"栗子用户\",\n  \"avatarUrl\": \"https://example.com/avatar.png\"\n}",
    "auth": false,
    "optionalAuth": false,
    "notes": [
      "code 必须是微信端取得的有效临时凭证，示例占位值不能直接登录。"
    ],
    "upload": false,
    "browserUnsupported": false
  },
  {
    "id": 3,
    "title": "获取用户信息 — 需要登录",
    "group": "account",
    "method": "GET",
    "path": "/api/user/info",
    "params": [],
    "body": "",
    "auth": true,
    "optionalAuth": false,
    "notes": [],
    "upload": false,
    "browserUnsupported": false
  },
  {
    "id": 4,
    "title": "退出登录 — 需要登录",
    "group": "account",
    "method": "POST",
    "path": "/api/user/logout",
    "params": [],
    "body": "",
    "auth": true,
    "optionalAuth": false,
    "notes": [
      "当前方法未删除 refreshToken 对应的 Redis 键，不等同于撤销所有刷新凭证。"
    ],
    "upload": false,
    "browserUnsupported": false
  },
  {
    "id": 5,
    "title": "创建用户账户 — 匿名可访问",
    "group": "account",
    "method": "POST",
    "path": "/api/user/create/account",
    "params": [
      [
        "username",
        "demo_user"
      ],
      [
        "password",
        "DemoPass123!"
      ],
      [
        "phone",
        "13800138000"
      ]
    ],
    "body": "",
    "auth": false,
    "optionalAuth": false,
    "notes": [],
    "upload": false,
    "browserUnsupported": false
  },
  {
    "id": 6,
    "title": "忘记密码重置 — 匿名可访问",
    "group": "account",
    "method": "PUT",
    "path": "/api/user/forget/password",
    "params": [
      [
        "username",
        "demo_user"
      ],
      [
        "phone",
        "13800138000"
      ],
      [
        "passwordNew",
        "NewDemoPass456!"
      ]
    ],
    "body": "",
    "auth": false,
    "optionalAuth": false,
    "notes": [
      "当前仅匹配用户名与手机号，没有短信验证码校验；返回 data 为数值用户ID。"
    ],
    "upload": false,
    "browserUnsupported": false
  },
  {
    "id": 7,
    "title": "修改密码 — 匿名可访问",
    "group": "account",
    "method": "PUT",
    "path": "/api/user/change/password",
    "params": [
      [
        "username",
        "demo_user"
      ],
      [
        "passwordOld",
        "DemoPass123!"
      ],
      [
        "passwordNew",
        "NewDemoPass456!"
      ]
    ],
    "body": "",
    "auth": false,
    "optionalAuth": false,
    "notes": [
      "当前 Shiro 配置允许匿名访问，由用户名和旧密码校验身份。"
    ],
    "upload": false,
    "browserUnsupported": false
  },
  {
    "id": 8,
    "title": "刷新 token — 匿名可访问",
    "group": "account",
    "method": "POST",
    "path": "/api/user/refresh/token",
    "params": [
      [
        "refreshToken",
        "REFRESH_TOKEN"
      ]
    ],
    "body": "",
    "auth": false,
    "optionalAuth": false,
    "notes": [
      "过期示例：HTTP 200，success=false，code=10003，message=登录已过期,请重新登录。"
    ],
    "upload": false,
    "browserUnsupported": false
  },
  {
    "id": 9,
    "title": "查询用户详情 — 需要登录",
    "group": "account",
    "method": "GET",
    "path": "/api/user/detail/get",
    "params": [],
    "body": "",
    "auth": true,
    "optionalAuth": false,
    "notes": [],
    "upload": false,
    "browserUnsupported": false
  },
  {
    "id": 10,
    "title": "修改用户详情 — 需要登录",
    "group": "account",
    "method": "PUT",
    "path": "/api/user/detail/update",
    "params": [],
    "body": "{\n  \"nickname\": \"栗子用户\",\n  \"avatar\": \"https://example.com/avatar.png\",\n  \"phone\": \"13800138000\"\n}",
    "auth": true,
    "optionalAuth": false,
    "notes": [],
    "upload": false,
    "browserUnsupported": false
  },
  {
    "id": 11,
    "title": "新增商品收藏 — 需要登录",
    "group": "products",
    "method": "POST",
    "path": "/api/user/collect/add",
    "params": [
      [
        "productId",
        "10001"
      ]
    ],
    "body": "",
    "auth": true,
    "optionalAuth": false,
    "notes": [],
    "upload": false,
    "browserUnsupported": false
  },
  {
    "id": 12,
    "title": "删除商品收藏 — 需要登录",
    "group": "products",
    "method": "DELETE",
    "path": "/api/user/collect/delete",
    "params": [
      [
        "productIds",
        "10001,10002"
      ]
    ],
    "body": "",
    "auth": true,
    "optionalAuth": false,
    "notes": [
      "data 是传入商品ID列表的数量，不是数据库实际删除行数。"
    ],
    "upload": false,
    "browserUnsupported": false
  },
  {
    "id": 13,
    "title": "查询收藏列表 — 需要登录",
    "group": "products",
    "method": "GET",
    "path": "/api/user/collect/list",
    "params": [
      [
        "querySize",
        "20"
      ]
    ],
    "body": "",
    "auth": true,
    "optionalAuth": false,
    "notes": [
      "sortId 是收藏记录ID，不是商品ID；继续查询必须原样回传响应游标。"
    ],
    "upload": false,
    "browserUnsupported": false
  },
  {
    "id": 14,
    "title": "查询热门商品 — 匿名可访问",
    "group": "products",
    "method": "GET",
    "path": "/api/product/hot",
    "params": [
      [
        "limit",
        "10"
      ]
    ],
    "body": "",
    "auth": false,
    "optionalAuth": false,
    "notes": [
      "limit 默认10，超过配置的热门缓存容量会报错。"
    ],
    "upload": false,
    "browserUnsupported": false
  },
  {
    "id": 15,
    "title": "查询商品简单信息列表 — 匿名可访问",
    "group": "products",
    "method": "GET",
    "path": "/api/product/brief/list",
    "params": [
      [
        "productIds",
        "10001,10002"
      ]
    ],
    "body": "",
    "auth": false,
    "optionalAuth": false,
    "notes": [
      "productIds 为空返回 []；无法补齐的ID可能对应 null 元素。"
    ],
    "upload": false,
    "browserUnsupported": false
  },
  {
    "id": 16,
    "title": "查询商品详情 — 匿名可访问",
    "group": "products",
    "method": "GET",
    "path": "/api/product/detail",
    "params": [
      [
        "productId",
        "10001"
      ]
    ],
    "body": "",
    "auth": false,
    "optionalAuth": false,
    "notes": [
      "可选 X-User-Id 控制收藏展示；该头不经过身份校验。当前返回 Product 实体，不是 ProductVO。"
    ],
    "upload": false,
    "browserUnsupported": false
  },
  {
    "id": 17,
    "title": "查询分类下的商品列表 — 匿名可访问",
    "group": "products",
    "method": "GET",
    "path": "/api/product/categroy/list",
    "params": [
      [
        "sortType",
        "default"
      ],
      [
        "querySize",
        "20"
      ],
      [
        "categoryId",
        "101"
      ],
      [
        "isFirstCategoryId",
        "true"
      ]
    ],
    "body": "",
    "auth": false,
    "optionalAuth": false,
    "notes": [
      "路径必须保持源码拼写 /product/categroy/list；sortType 实际必传，没有 default 初始化。"
    ],
    "upload": false,
    "browserUnsupported": false
  },
  {
    "id": 18,
    "title": "关键词搜索商品 — 匿名可访问",
    "group": "products",
    "method": "GET",
    "path": "/api/product/search",
    "params": [
      [
        "sortType",
        "default"
      ],
      [
        "querySize",
        "20"
      ],
      [
        "keyword",
        "板栗"
      ]
    ],
    "body": "",
    "auth": false,
    "optionalAuth": false,
    "notes": [
      "首屏不传 sortId/sortValue，续页传回 cursorCommonEntity；不要用 pageNum。"
    ],
    "upload": false,
    "browserUnsupported": false
  },
  {
    "id": 19,
    "title": "查询相关商品 — 匿名可访问",
    "group": "products",
    "method": "GET",
    "path": "/api/product/related",
    "params": [
      [
        "productName",
        "糖炒板栗"
      ],
      [
        "limit",
        "10"
      ]
    ],
    "body": "",
    "auth": false,
    "optionalAuth": false,
    "notes": [
      "实际参数为 productName，不是 Swagger 描述中的 productId；没有精确重名项时可能返回 limit+1 条。"
    ],
    "upload": false,
    "browserUnsupported": false
  },
  {
    "id": 20,
    "title": "查询商品规格价格 — 匿名可访问",
    "group": "products",
    "method": "GET",
    "path": "/api/product/spec/price",
    "params": [
      [
        "productId",
        "10001"
      ],
      [
        "specId",
        "20001"
      ]
    ],
    "body": "",
    "auth": false,
    "optionalAuth": false,
    "notes": [
      "布隆过滤不通过时直接 return null，可能得到空响应体；正常成功才是下方 JSON。"
    ],
    "upload": false,
    "browserUnsupported": false
  },
  {
    "id": 21,
    "title": "滚动查询商品列表 — 匿名可访问",
    "group": "products",
    "method": "GET",
    "path": "/api/product/scroll/query/list",
    "params": [
      [
        "querySize",
        "80"
      ]
    ],
    "body": "",
    "auth": false,
    "optionalAuth": false,
    "notes": [
      "下一次 beginId 取返回的 simpleCursorCommonEntity.sortId，不能取打乱后的最后一条商品ID。"
    ],
    "upload": false,
    "browserUnsupported": false
  },
  {
    "id": 22,
    "title": "查询用户端热门搜索关键词 — 匿名可访问",
    "group": "products",
    "method": "GET",
    "path": "/api/product/user/keyword/list",
    "params": [],
    "body": "",
    "auth": false,
    "optionalAuth": false,
    "notes": [],
    "upload": false,
    "browserUnsupported": false
  },
  {
    "id": 23,
    "title": "管理员查询搜索关键词列表 — 匿名可访问",
    "group": "products",
    "method": "GET",
    "path": "/api/product/admin/keyword/list",
    "params": [],
    "body": "",
    "auth": false,
    "optionalAuth": false,
    "notes": [
      "虽然名称是管理员查询，但 /api/product/** 当前匿名放行。"
    ],
    "upload": false,
    "browserUnsupported": false
  },
  {
    "id": 24,
    "title": "管理员修改搜索关键词 — 匿名可访问",
    "group": "products",
    "method": "PUT",
    "path": "/api/product/admin/keyword/update",
    "params": [],
    "body": "[\n  {\n    \"id\": 1,\n    \"keyword\": \"板栗\",\n    \"isHot\": \"active\",\n    \"isShow\": \"active\",\n    \"createTime\": \"2026-09-21 10:00:00\",\n    \"updateTime\": \"2026-09-21 10:00:00\"\n  }\n]",
    "auth": false,
    "optionalAuth": false,
    "notes": [
      "这是全量覆盖，不是按ID增量修改；当前匿名放行，联调前需知晓这一实现。"
    ],
    "upload": false,
    "browserUnsupported": false
  },
  {
    "id": 25,
    "title": "用户分类查询商品一级评论 — 可选登录",
    "group": "reviews",
    "method": "POST",
    "path": "/api/user/product/comment/firstComment/show",
    "params": [
      [
        "productId",
        "10001"
      ]
    ],
    "body": "{\n  \"sortType\": \"default\",\n  \"sortValue\": null,\n  \"sortId\": null,\n  \"querySize\": 20\n}",
    "auth": false,
    "optionalAuth": true,
    "notes": [
      "sortType 可选 default/isGoodReview/isAppendComment；sortValue 使用 yyyy-MM-dd HH:mm:ss。非空页 builder 未赋 isEnd，因此该字段可能省略；空页 data={\"isEnd\":true}。"
    ],
    "upload": false,
    "browserUnsupported": false
  },
  {
    "id": 26,
    "title": "查看二级评价 — 可选登录",
    "group": "reviews",
    "method": "POST",
    "path": "/api/user/product/comment/secondComment/show",
    "params": [
      [
        "firstCommentId",
        "50001"
      ]
    ],
    "body": "{\n  \"sortType\": \"default\",\n  \"sortValue\": null,\n  \"sortId\": null,\n  \"querySize\": 20\n}",
    "auth": false,
    "optionalAuth": true,
    "notes": [
      "body 中 sortType 保留 default 即可，此方法实际不使用它；空页只有 isEnd=true。"
    ],
    "upload": false,
    "browserUnsupported": false
  },
  {
    "id": 27,
    "title": "用户查看追评 — 可选登录",
    "group": "reviews",
    "method": "GET",
    "path": "/api/user/product/comment/appendComment/show",
    "params": [
      [
        "firstCommentId",
        "50001"
      ]
    ],
    "body": "",
    "auth": false,
    "optionalAuth": true,
    "notes": [],
    "upload": false,
    "browserUnsupported": false
  },
  {
    "id": 28,
    "title": "用户发表一级商品评论 — 需要登录",
    "group": "reviews",
    "method": "POST",
    "path": "/api/user/product/comment/firstComment/save",
    "params": [],
    "body": "{\n  \"productId\": \"10001\",\n  \"productSpecId\": \"20001\",\n  \"productSpecText\": \"500g/袋\",\n  \"orderNo\": \"260921100000123456\",\n  \"userNickname\": \"栗子用户\",\n  \"userAvatar\": \"https://example.com/avatar.png\",\n  \"content\": \"香甜软糯，包装完好。\",\n  \"imageUrls\": \"[\\\"https://example.com/review.jpg\\\"]\",\n  \"rating\": 5,\n  \"isAnonymous\": 0\n}",
    "auth": true,
    "optionalAuth": false,
    "notes": [
      "服务中直接设置 isBuyer=1，未核验购买记录；rating 的1~5目前只在说明中，未配置范围校验。"
    ],
    "upload": false,
    "browserUnsupported": false
  },
  {
    "id": 29,
    "title": "用户发表二级以上商品评论 — 需要登录",
    "group": "reviews",
    "method": "POST",
    "path": "/api/user/product/comment/secondComment/save",
    "params": [],
    "body": "{\n  \"productId\": \"10001\",\n  \"productSpecId\": \"20001\",\n  \"parentId\": \"50001\",\n  \"userNickname\": \"栗子用户\",\n  \"userAvatar\": \"https://example.com/avatar.png\",\n  \"content\": \"口感很好。\",\n  \"replyUserId\": \"1002\",\n  \"replyUserNickname\": \"买家小李\",\n  \"isAnonymous\": 0\n}",
    "auth": true,
    "optionalAuth": false,
    "notes": [
      "parentId 应传一级评论ID；同父评论作者仅据用户ID设置买家标记。"
    ],
    "upload": false,
    "browserUnsupported": false
  },
  {
    "id": 30,
    "title": "用户对一级评论进行追评 — 需要登录",
    "group": "reviews",
    "method": "POST",
    "path": "/api/user/product/comment/firstComment/append",
    "params": [],
    "body": "{\n  \"productId\": \"10001\",\n  \"orderNo\": \"260921100000123456\",\n  \"content\": \"回购依然很好吃。\",\n  \"imageUrls\": \"[]\"\n}",
    "auth": true,
    "optionalAuth": false,
    "notes": [
      "当前按 orderNo 查单条评论，多商品订单可能不适配；归属校验条件也未构成完整的权限保证。"
    ],
    "upload": false,
    "browserUnsupported": false
  },
  {
    "id": 31,
    "title": "统计商品下评论数 — 可选登录",
    "group": "reviews",
    "method": "GET",
    "path": "/api/user/product/comment/count/show",
    "params": [
      [
        "productId",
        "10001"
      ]
    ],
    "body": "",
    "auth": false,
    "optionalAuth": true,
    "notes": [
      "data 是字符串；统计该商品全部评论，包括回复，不只一级评论。"
    ],
    "upload": false,
    "browserUnsupported": false
  },
  {
    "id": 32,
    "title": "对商品进行点赞和取消点赞 — 需要登录",
    "group": "reviews",
    "method": "PUT",
    "path": "/api/user/product/comment/like",
    "params": [
      [
        "productCommentId",
        "50001"
      ],
      [
        "isLike",
        "1"
      ],
      [
        "isFirstComment",
        "1"
      ]
    ],
    "body": "",
    "auth": true,
    "optionalAuth": false,
    "notes": [
      "isLike=1点赞、0取消；isFirstComment=1一级、0二级。未提供重复请求幂等保证。"
    ],
    "upload": false,
    "browserUnsupported": false
  },
  {
    "id": 33,
    "title": "查询分类树 — 匿名可访问",
    "group": "products",
    "method": "GET",
    "path": "/api/category/tree",
    "params": [],
    "body": "",
    "auth": false,
    "optionalAuth": false,
    "notes": [],
    "upload": false,
    "browserUnsupported": false
  },
  {
    "id": 34,
    "title": "管理员新增分类 — 需要登录",
    "group": "admin",
    "method": "POST",
    "path": "/api/admin/category/add",
    "params": [],
    "body": "{\n  \"name\": \"坚果零食\",\n  \"parentId\": \"0\",\n  \"sort\": 1,\n  \"iconUrl\": \"https://example.com/chestnut.jpg\",\n  \"status\": 1\n}",
    "auth": true,
    "optionalAuth": false,
    "notes": [
      "返回的是新分类的子分类列表，不是新分类对象；新增二级分类可能保存后返回业务错误，因为 getCategoryChildren 仅在根节点中查找。"
    ],
    "upload": false,
    "browserUnsupported": false
  },
  {
    "id": 35,
    "title": "管理员删除分类 — 需要登录",
    "group": "admin",
    "method": "DELETE",
    "path": "/api/admin/category/101",
    "params": [],
    "body": "",
    "auth": true,
    "optionalAuth": false,
    "notes": [],
    "upload": false,
    "browserUnsupported": false
  },
  {
    "id": 36,
    "title": "管理员修改分类信息 — 需要登录",
    "group": "admin",
    "method": "PUT",
    "path": "/api/admin/category/101",
    "params": [],
    "body": "{\n  \"name\": \"坚果零食\",\n  \"parentId\": \"0\",\n  \"sort\": 1,\n  \"iconUrl\": \"https://example.com/chestnut.jpg\",\n  \"status\": 1\n}",
    "auth": true,
    "optionalAuth": false,
    "notes": [],
    "upload": false,
    "browserUnsupported": false
  },
  {
    "id": 37,
    "title": "管理员修改分类状态 — 需要登录",
    "group": "admin",
    "method": "PUT",
    "path": "/api/admin/category/101/status",
    "params": [
      [
        "status",
        "1"
      ]
    ],
    "body": "",
    "auth": true,
    "optionalAuth": false,
    "notes": [
      "请求 status=1/0，响应 status=active/inactive。"
    ],
    "upload": false,
    "browserUnsupported": false
  },
  {
    "id": 38,
    "title": "查询分类商品列表 — 匿名可访问",
    "group": "products",
    "method": "GET",
    "path": "/api/category/product/list/101/0",
    "params": [
      [
        "sortType",
        "default"
      ]
    ],
    "body": "",
    "auth": false,
    "optionalAuth": false,
    "notes": [
      "首屏 beginProductId=0；该旧接口是批内排序而非全局排序，末条ID游标可能与排序不一致，优先使用 /product/categroy/list。空结果 data=[]。"
    ],
    "upload": false,
    "browserUnsupported": false
  },
  {
    "id": 39,
    "title": "查询地址列表 — 需要登录",
    "group": "cart",
    "method": "GET",
    "path": "/api/address/list",
    "params": [],
    "body": "",
    "auth": true,
    "optionalAuth": false,
    "notes": [],
    "upload": false,
    "browserUnsupported": false
  },
  {
    "id": 40,
    "title": "新增地址 — 需要登录",
    "group": "cart",
    "method": "POST",
    "path": "/api/address/add",
    "params": [],
    "body": "{\n  \"id\": null,\n  \"receiver\": \"张三\",\n  \"phone\": \"13800138000\",\n  \"province\": \"浙江省\",\n  \"city\": \"杭州市\",\n  \"district\": \"西湖区\",\n  \"detailAddress\": \"示例路1号\",\n  \"isDefault\": true\n}",
    "auth": true,
    "optionalAuth": false,
    "notes": [
      "新增 id 传 null；isDefault 应传布尔值。依赖 AopContext.currentProxy()，代理暴露配置是否生效需运行验证。"
    ],
    "upload": false,
    "browserUnsupported": false
  },
  {
    "id": 41,
    "title": "修改地址 — 需要登录",
    "group": "cart",
    "method": "PUT",
    "path": "/api/address/update",
    "params": [],
    "body": "{\n  \"id\": \"30001\",\n  \"receiver\": \"张三\",\n  \"phone\": \"13800138000\",\n  \"province\": \"浙江省\",\n  \"city\": \"杭州市\",\n  \"district\": \"西湖区\",\n  \"detailAddress\": \"示例路1号\",\n  \"isDefault\": true\n}",
    "auth": true,
    "optionalAuth": false,
    "notes": [
      "id 必须传已有地址；目前 updateById 未把旧记录的用户归属作为条件。"
    ],
    "upload": false,
    "browserUnsupported": false
  },
  {
    "id": 42,
    "title": "删除地址 — 需要登录",
    "group": "cart",
    "method": "DELETE",
    "path": "/api/address/delete",
    "params": [
      [
        "id",
        "30001"
      ]
    ],
    "body": "",
    "auth": true,
    "optionalAuth": false,
    "notes": [
      "当前 removeById 未校验地址归属。"
    ],
    "upload": false,
    "browserUnsupported": false
  },
  {
    "id": 43,
    "title": "查询购物车列表 — 需要登录",
    "group": "cart",
    "method": "GET",
    "path": "/api/cart/list",
    "params": [],
    "body": "",
    "auth": true,
    "optionalAuth": false,
    "notes": [],
    "upload": false,
    "browserUnsupported": false
  },
  {
    "id": 44,
    "title": "新增购物车商品 — 需要登录",
    "group": "cart",
    "method": "POST",
    "path": "/api/cart/add",
    "params": [],
    "body": "{\n  \"productId\": \"10001\",\n  \"specId\": \"20001\",\n  \"quantity\": 2\n}",
    "auth": true,
    "optionalAuth": false,
    "notes": [],
    "upload": false,
    "browserUnsupported": false
  },
  {
    "id": 45,
    "title": "清空购物车 — 需要登录",
    "group": "cart",
    "method": "DELETE",
    "path": "/api/cart/clear",
    "params": [],
    "body": "",
    "auth": true,
    "optionalAuth": false,
    "notes": [
      "属于旧接口；当前直接删数据库，未同步清理已有 Redis 内容，延迟同步可能重新写回；空车返回 []。"
    ],
    "upload": false,
    "browserUnsupported": false
  },
  {
    "id": 46,
    "title": "删除购物车商品 — 需要登录",
    "group": "cart",
    "method": "DELETE",
    "path": "/api/cart/products",
    "params": [
      [
        "productIds",
        "10001,10002"
      ],
      [
        "specIds",
        "20001,20002"
      ]
    ],
    "body": "",
    "auth": true,
    "optionalAuth": false,
    "notes": [
      "属于旧接口，同样存在 Redis 与直接删库流程不一致；deletedIds 实际返回商品ID列表。"
    ],
    "upload": false,
    "browserUnsupported": false
  },
  {
    "id": 47,
    "title": "更新购物车数据 — 需要登录",
    "group": "cart",
    "method": "PUT",
    "path": "/api/cart/update",
    "params": [],
    "body": "{\n  \"cartItems\": [\n    {\n      \"productId\": \"10001\",\n      \"specId\": \"20001\",\n      \"quantity\": 2\n    }\n  ]\n}",
    "auth": true,
    "optionalAuth": false,
    "notes": [
      "这是覆盖，不是累加；cartItems=[] 直接成功，不会清空购物车。"
    ],
    "upload": false,
    "browserUnsupported": false
  },
  {
    "id": 48,
    "title": "创建订单 — 需要登录",
    "group": "orders",
    "method": "POST",
    "path": "/api/order/create",
    "params": [],
    "body": "{\n  \"addressId\": \"30001\",\n  \"remark\": \"请尽快发货\",\n  \"freight\": \"6.00\",\n  \"totalAmount\": \"65.80\",\n  \"orderItems\": [\n    {\n      \"productId\": \"10001\",\n      \"specId\": \"20001\",\n      \"quantity\": 2,\n      \"price\": \"29.90\",\n      \"productName\": \"糖炒板栗\",\n      \"productImage\": \"https://example.com/chestnut.jpg\",\n      \"specText\": \"500g/袋\"\n    }\n  ]\n}",
    "auth": true,
    "optionalAuth": false,
    "notes": [
      "金额、单价与运费当前直接采用客户端数据，未见服务端重算或扣库存。延迟任务的 threadPool 字段仅有 @Qualifier 未注入，创建链路可能异常；下方为成功分支结构示例。"
    ],
    "upload": false,
    "browserUnsupported": false
  },
  {
    "id": 49,
    "title": "查询订单列表 — 需要登录",
    "group": "orders",
    "method": "GET",
    "path": "/api/order/list",
    "params": [
      [
        "pageNum",
        "1"
      ],
      [
        "pageSize",
        "10"
      ],
      [
        "status",
        "pendingPayment"
      ]
    ],
    "body": "",
    "auth": true,
    "optionalAuth": false,
    "notes": [],
    "upload": false,
    "browserUnsupported": false
  },
  {
    "id": 50,
    "title": "查询指定页面订单列表  — 需要登录",
    "group": "orders",
    "method": "GET",
    "path": "/api/order/page/list",
    "params": [
      [
        "pageName",
        "allPage"
      ]
    ],
    "body": "",
    "auth": true,
    "optionalAuth": false,
    "notes": [
      "无订单时直接成功且省略 data；pageName 使用页面枚举，不是 status。"
    ],
    "upload": false,
    "browserUnsupported": false
  },
  {
    "id": 51,
    "title": "查询订单详情 — 需要登录",
    "group": "orders",
    "method": "GET",
    "path": "/api/order/detail",
    "params": [
      [
        "orderNo",
        "260921100000123456"
      ]
    ],
    "body": "",
    "auth": true,
    "optionalAuth": false,
    "notes": [
      "当前服务入口没有按登录用户约束订单归属。"
    ],
    "upload": false,
    "browserUnsupported": false
  },
  {
    "id": 52,
    "title": "取消订单 — 需要登录",
    "group": "orders",
    "method": "PUT",
    "path": "/api/order/cancel",
    "params": [
      [
        "orderNo",
        "260921100000123456"
      ],
      [
        "cancelReason",
        "暂时不需要"
      ]
    ],
    "body": "",
    "auth": true,
    "optionalAuth": false,
    "notes": [
      "未校验原状态及订单归属。"
    ],
    "upload": false,
    "browserUnsupported": false
  },
  {
    "id": 53,
    "title": "支付成功订单 — 需要登录",
    "group": "orders",
    "method": "PUT",
    "path": "/api/order/pay/success",
    "params": [
      [
        "orderNo",
        "260921100000123456"
      ]
    ],
    "body": "",
    "auth": true,
    "optionalAuth": false,
    "notes": [
      "当前是测试性状态修改接口，不校验真实支付结果；成功状态为待发货 pendingShipment，不是注释中的待收货。"
    ],
    "upload": false,
    "browserUnsupported": false
  },
  {
    "id": 54,
    "title": "确认收货 — 需要登录",
    "group": "orders",
    "method": "PUT",
    "path": "/api/order/confirmReceipt",
    "params": [
      [
        "orderNo",
        "260921100000123456"
      ]
    ],
    "body": "",
    "auth": true,
    "optionalAuth": false,
    "notes": [
      "当前未约束原状态必须为待收货。"
    ],
    "upload": false,
    "browserUnsupported": false
  },
  {
    "id": 55,
    "title": "删除订单 — 需要登录",
    "group": "orders",
    "method": "DELETE",
    "path": "/api/order/delete",
    "params": [
      [
        "orderNo",
        "260921100000123456"
      ]
    ],
    "body": "",
    "auth": true,
    "optionalAuth": false,
    "notes": [
      "当前方法未检查订单归属及可删除状态；data 为订单号字符串。"
    ],
    "upload": false,
    "browserUnsupported": false
  },
  {
    "id": 56,
    "title": "计算订单运费 — 需要登录",
    "group": "orders",
    "method": "GET",
    "path": "/api/order/freight",
    "params": [
      [
        "productIds",
        "10001"
      ],
      [
        "addressId",
        "30001"
      ]
    ],
    "body": "",
    "auth": true,
    "optionalAuth": false,
    "notes": [
      "运费为随机模拟值，同一请求可返回不同金额；未接入真实运费服务。"
    ],
    "upload": false,
    "browserUnsupported": false
  },
  {
    "id": 57,
    "title": "查询物流信息 — 需要登录",
    "group": "orders",
    "method": "GET",
    "path": "/api/order/logistics",
    "params": [
      [
        "orderNo",
        "260921100000123456"
      ]
    ],
    "body": "",
    "auth": true,
    "optionalAuth": false,
    "notes": [
      "查询已存物流记录，未见实时物流供应商调用。"
    ],
    "upload": false,
    "browserUnsupported": false
  },
  {
    "id": 58,
    "title": "滚动查询订单列表 — 需要登录",
    "group": "orders",
    "method": "GET",
    "path": "/api/order/scroll/query/list",
    "params": [],
    "body": "{\n  \"beginId\": null\n}",
    "auth": true,
    "optionalAuth": false,
    "notes": [
      "实际每批80条；GET 必须带 JSON body，浏览器 fetch 不支持此用法。返回原始 Order 实体；空结果分支漏了 build()，首屏空集还可能触发 null 拆箱异常。"
    ],
    "upload": false,
    "browserUnsupported": true
  },
  {
    "id": 59,
    "title": "条件搜索订单 — 需要登录",
    "group": "orders",
    "method": "GET",
    "path": "/api/order/search",
    "params": [
      [
        "searchCondition",
        "板栗"
      ]
    ],
    "body": "",
    "auth": true,
    "optionalAuth": false,
    "notes": [
      "无匹配记录时成功但省略 data。订单号识别正则要求6位数字-11位数字，与生成器的6位日期+12位数字不一致，当前生成订单号可能被误判成商品名称。"
    ],
    "upload": false,
    "browserUnsupported": false
  },
  {
    "id": 60,
    "title": "微信支付预下单 — 需要登录",
    "group": "orders",
    "method": "POST",
    "path": "/api/pay/wxpay",
    "params": [],
    "body": "{\n  \"orderNo\": \"260921100000123456\"\n}",
    "auth": true,
    "optionalAuth": false,
    "notes": [
      "不会返回 timeStamp、nonceStr、package、paySign；不能凭该成功响应调起真实支付。"
    ],
    "upload": false,
    "browserUnsupported": false
  },
  {
    "id": 61,
    "title": "查询首页联播图列表 — 匿名可访问",
    "group": "content",
    "method": "GET",
    "path": "/api/banner/list",
    "params": [],
    "body": "",
    "auth": false,
    "optionalAuth": false,
    "notes": [
      "缓存命中分支执行 reverse，可能与首次查库顺序相反。"
    ],
    "upload": false,
    "browserUnsupported": false
  },
  {
    "id": 62,
    "title": "管理员查询联播图列表 — 需要登录",
    "group": "admin",
    "method": "GET",
    "path": "/api/admin/banner/list",
    "params": [
      [
        "pageNum",
        "1"
      ],
      [
        "pageSize",
        "10"
      ]
    ],
    "body": "",
    "auth": true,
    "optionalAuth": false,
    "notes": [
      "pageNum/pageSize 虽有定义，Service 未使用，实际不是分页。"
    ],
    "upload": false,
    "browserUnsupported": false
  },
  {
    "id": 63,
    "title": "管理员新增联播图 — 需要登录",
    "group": "admin",
    "method": "POST",
    "path": "/api/admin/banner/add",
    "params": [],
    "body": "{\n  \"title\": \"板栗上新\",\n  \"imageUrl\": \"https://example.com/chestnut.jpg\",\n  \"linkUrl\": \"/pages/product/detail?id=10001\",\n  \"sort\": 1,\n  \"status\": \"active\"\n}",
    "auth": true,
    "optionalAuth": false,
    "notes": [],
    "upload": false,
    "browserUnsupported": false
  },
  {
    "id": 64,
    "title": "管理员修改联播图 — 需要登录",
    "group": "admin",
    "method": "PUT",
    "path": "/api/admin/banner/update",
    "params": [],
    "body": "{\n  \"title\": \"板栗上新\",\n  \"imageUrl\": \"https://example.com/chestnut.jpg\",\n  \"linkUrl\": \"/pages/product/detail?id=10001\",\n  \"sort\": 1,\n  \"status\": \"active\"\n}",
    "auth": true,
    "optionalAuth": false,
    "notes": [
      "当前 BannerDTO 没有 id，无法指定更新目标；示例请求不能保证成功，下面展示典型错误而非虚构成功。 错误 message 的异常后缀由运行环境决定，示例使用占位文本。"
    ],
    "upload": false,
    "browserUnsupported": false
  },
  {
    "id": 65,
    "title": "管理员删除联播图 — 需要登录",
    "group": "admin",
    "method": "DELETE",
    "path": "/api/admin/banner/delete",
    "params": [
      [
        "id",
        "1"
      ]
    ],
    "body": "",
    "auth": true,
    "optionalAuth": false,
    "notes": [
      "未检查 removeById 的布尔返回值。"
    ],
    "upload": false,
    "browserUnsupported": false
  },
  {
    "id": 66,
    "title": "管理员修改联播图排序 — 需要登录",
    "group": "admin",
    "method": "PUT",
    "path": "/api/admin/banner/updateSort",
    "params": [],
    "body": "{\n  \"id\": \"1\",\n  \"sort\": 2\n}",
    "auth": true,
    "optionalAuth": false,
    "notes": [],
    "upload": false,
    "browserUnsupported": false
  },
  {
    "id": 67,
    "title": "管理员修改联播图状态 — 需要登录",
    "group": "admin",
    "method": "PUT",
    "path": "/api/admin/banner/updateStatus",
    "params": [],
    "body": "{\n  \"id\": \"1\",\n  \"status\": \"active\"\n}",
    "auth": true,
    "optionalAuth": false,
    "notes": [],
    "upload": false,
    "browserUnsupported": false
  },
  {
    "id": 68,
    "title": "查询最新通知 — 匿名可访问",
    "group": "content",
    "method": "GET",
    "path": "/api/notice/latest",
    "params": [
      [
        "limit",
        "5"
      ]
    ],
    "body": "",
    "auth": false,
    "optionalAuth": false,
    "notes": [],
    "upload": false,
    "browserUnsupported": false
  },
  {
    "id": 69,
    "title": "管理员新增通知 — 需要登录",
    "group": "admin",
    "method": "POST",
    "path": "/api/admin/notice/add",
    "params": [],
    "body": "{\n  \"id\": null,\n  \"title\": \"商城公告\",\n  \"content\": \"新品已上架\",\n  \"status\": \"active\"\n}",
    "auth": true,
    "optionalAuth": false,
    "notes": [],
    "upload": false,
    "browserUnsupported": false
  },
  {
    "id": 70,
    "title": "管理员修改通知 — 需要登录",
    "group": "admin",
    "method": "PUT",
    "path": "/api/admin/notice/update",
    "params": [],
    "body": "{\n  \"id\": \"1\",\n  \"title\": \"商城公告\",\n  \"content\": \"新品已上架\",\n  \"status\": \"active\"\n}",
    "auth": true,
    "optionalAuth": false,
    "notes": [],
    "upload": false,
    "browserUnsupported": false
  },
  {
    "id": 71,
    "title": "管理员删除通知 — 需要登录",
    "group": "admin",
    "method": "DELETE",
    "path": "/api/admin/notice/delete",
    "params": [
      [
        "id",
        "1"
      ]
    ],
    "body": "",
    "auth": true,
    "optionalAuth": false,
    "notes": [],
    "upload": false,
    "browserUnsupported": false
  },
  {
    "id": 72,
    "title": "发行优惠券 — 需要登录",
    "group": "admin",
    "method": "POST",
    "path": "/api/admin/coupon/release",
    "params": [],
    "body": "{\n  \"activityName\": \"满100减10\",\n  \"couponType\": 1,\n  \"faceValue\": 10,\n  \"discountRate\": null,\n  \"maxDiscount\": null,\n  \"minSpend\": 100,\n  \"totalQuota\": 1000,\n  \"validMode\": 1,\n  \"validStart\": \"2026-09-21T00:00:00\",\n  \"validEnd\": \"2026-10-21T23:59:59\",\n  \"receiveValidDays\": null,\n  \"limitPerPerson\": 1,\n  \"userLimitType\": 1,\n  \"useScope\": 1,\n  \"mutexGroupId\": 0,\n  \"status\": 1,\n  \"releaseTime\": \"2026-09-21T10:00:00\"\n}",
    "auth": true,
    "optionalAuth": false,
    "notes": [
      "当前公开 Controller 只实现发行；没有领券、核销、优惠试算接口。此方法未直接刷新优惠券缓存，不能描述成完整营销闭环。"
    ],
    "upload": false,
    "browserUnsupported": false
  },
  {
    "id": 73,
    "title": "获取当前用户的会话列表 — 需要登录",
    "group": "service",
    "method": "GET",
    "path": "/api/chat/sessions",
    "params": [],
    "body": "",
    "auth": true,
    "optionalAuth": false,
    "notes": [],
    "upload": false,
    "browserUnsupported": false
  },
  {
    "id": 74,
    "title": "分页获取与某人的聊天历史 — 需要登录",
    "group": "service",
    "method": "GET",
    "path": "/api/chat/history/9001",
    "params": [
      [
        "page",
        "1"
      ],
      [
        "size",
        "20"
      ]
    ],
    "body": "",
    "auth": true,
    "optionalAuth": false,
    "notes": [
      "读取历史不会调用 clearUnread；需要另发清未读请求。"
    ],
    "upload": false,
    "browserUnsupported": false
  },
  {
    "id": 75,
    "title": "手动清除某会话的未读数 — 需要登录",
    "group": "service",
    "method": "POST",
    "path": "/api/chat/clearUnread/9001",
    "params": [],
    "body": "",
    "auth": true,
    "optionalAuth": false,
    "notes": [
      "只清会话计数，不更新 chat_message.isRead。"
    ],
    "upload": false,
    "browserUnsupported": false
  },
  {
    "id": 76,
    "title": "用户提交反馈 — 需要登录",
    "group": "service",
    "method": "POST",
    "path": "/api/feedback/add",
    "params": [],
    "body": "{\n  \"id\": null,\n  \"content\": \"希望增加更多规格\",\n  \"images\": \"[\\\"https://example.com/feedback.jpg\\\"]\",\n  \"contact\": \"13800138000\"\n}",
    "auth": true,
    "optionalAuth": false,
    "notes": [
      "请求中的 id 被忽略。"
    ],
    "upload": false,
    "browserUnsupported": false
  },
  {
    "id": 77,
    "title": "查询单条工厂信息,现在用于个人中心的关于我们 — 匿名可访问",
    "group": "content",
    "method": "GET",
    "path": "/api/about/us/introduce",
    "params": [],
    "body": "",
    "auth": false,
    "optionalAuth": false,
    "notes": [],
    "upload": false,
    "browserUnsupported": false
  },
  {
    "id": 78,
    "title": "图片上传 — 匿名可访问",
    "group": "service",
    "method": "POST",
    "path": "/api/upload/image",
    "params": [],
    "body": "",
    "auth": false,
    "optionalAuth": false,
    "notes": [
      "必须使用 multipart/form-data，字段名 file；地址为示例，不是真实上传结果。"
    ],
    "upload": true,
    "browserUnsupported": false
  }
];
