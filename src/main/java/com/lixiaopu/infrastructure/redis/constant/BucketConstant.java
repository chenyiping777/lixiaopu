package com.lixiaopu.infrastructure.redis.constant;

public class BucketConstant {

    //UUID:每次成功抢占到写标记的时候，就生成一个全新 UUID。
  public record BucketSign(BucketThreadType bucketThreadType, String UUID) {}

    public enum BucketThreadType{
        READ_THREAD,//读线程
       WRITE_THREAD;//写线程
    }

}
