package com.lixiaopu.common.auth.sms;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("local-test")
public class LocalSmsGateway implements SmsGateway {
    @Override public void send(String phone, String purpose, String code) { }
    @Override public boolean localTest() { return true; }
}
