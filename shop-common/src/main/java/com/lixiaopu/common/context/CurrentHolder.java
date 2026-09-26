package com.lixiaopu.common.context;


import com.lixiaopu.common.result.UserInfo;

public class CurrentHolder {

    private static final ThreadLocal<UserInfo> CURRENT_USER = new ThreadLocal<>();
    public static void setCurrentUser(UserInfo userInfo){
        CURRENT_USER.set(userInfo);
    }
    public static  UserInfo getCurrentUser(){
        return CURRENT_USER.get();
    }

    public static void remove(){
        CURRENT_USER.remove();
    }
}
