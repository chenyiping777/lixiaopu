package com.lixiaopu.common.auth.sms;

public interface SmsGateway {
    void send(String phone, String purpose, String code);
    default boolean localTest() { return false; }
}
