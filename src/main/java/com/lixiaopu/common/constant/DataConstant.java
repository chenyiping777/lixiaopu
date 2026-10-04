package com.lixiaopu.common.constant;



public class DataConstant {

    /**
     * 通用滚动查询的单次条数上限：80条（数据不足时少于80条）。
     * 当前项目尚未引用；参考项目订单滚动查询实际使用此常量。
     */
    public static final int COMMON_SCROLL_QUERY_NUMBER = 80;

    /**
     * 商品滚动查询的单次条数上限：80条。
     * 当前项目尚未引用；参考项目分类商品查询用它拼接SQL的LIMIT。
     */
    public static final int PRODUCT_SCROLL_QUERY_NUMBER = 80;

    /**
     * 为订单滚动查询预留的单次条数：40条。
     * 当前项目和参考项目均未引用；仅定义常量不会让订单接口自动改为40条。
     */
    public static final int ORDER_SCROLL_QUERY_NUMBER = 40;

    /**
     * 随机商品查询起始ID的范围系数：0.95即95%，不是安全认证参数。
     * 参考项目首次查询用round(最大商品ID * 0.95)计算随机起点的上界，
     * 再保证上界至少为2；随机起点小于该上界，避免过于接近最大ID。
     * ID可能不连续，因此不能保证后面一定有足够商品。当前项目尚未引用。
     */
    public static final Double QUERY_SECURITY_NUMBER = 0.95;

    /**
     * 预留查询比例：0.80即80%。名字表达“足够”的判断意图，
     * 但当前项目和参考项目均未引用，尚无具体比较对象或判断规则。
     */
    public static final Double QUERY_ENOUGH_NUMBER = 0.80;

    /**
     * 预留查询比例：0.4即40%。名字表达“不足”的判断意图，
     * 但当前项目和参考项目均未引用，不能认定为实际生效的分页阈值。
     */
    public static final Double QUERY_NOT_ENOUGH_NUMBER = 0.4;

    /** int类型的0；当前用于读取列表第一个元素（下标从0开始）。 */
    public static final int ZERO_INT = 0;

    /** int类型的1；当前用于判断按ID查找到的一级分类是否超过一个。 */
    public static final int ONE_INT = 1;

    /** long类型的0，L表示long字面量；当前parentId=0L表示一级分类。 */
    public static final long ZERO_LONG = 0L;

    /** long类型的1；可用于long数值或ID比较，当前项目尚未引用。 */
    public static final long ONE_LONG = 1L;

    /**
     * 字符串"-1"，不是数字-1。
     * 参考项目商品查询把它作为未提供用户ID时的占位值；当前项目尚未引用。
     */
    public static final String NEGATIVE_ONE_STRING = "-1";

    /**
     * 默认头像的访问路径；参考项目匿名评论使用它，当前尚未引用此常量。
     * 当前AuthService创建用户时直接写了同一路径，未通过这个常量取值。
     */
    public static final String DEFAULT_AVATAR="/static/images/default-avatar.png";


}
