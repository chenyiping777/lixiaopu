package com.lixiaopu.admin;

import com.lixiaopu.common.auth.service.AuthService;
import com.lixiaopu.common.auth.dto.AuthDtos;
import jakarta.validation.Validator;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** One-time offline bootstrap. Set all three environment variables for first startup, then remove them. */
@Component
public class AdminBootstrap implements ApplicationRunner {
    private final AuthService auth;
    private final Validator validator;
    @Value("${lixiaopu.bootstrap.username:}") private String username;
    @Value("${lixiaopu.bootstrap.password:}") private String password;
    @Value("${lixiaopu.bootstrap.phone:}") private String phone;
    public AdminBootstrap(AuthService auth, Validator validator) {
        this.auth=auth;
        this.validator=validator;
    }
    @Override public void run(ApplicationArguments args) {
        if (username.isBlank() && password.isBlank() && phone.isBlank()) return;
        AuthDtos.CreateAdmin request = new AuthDtos.CreateAdmin(username, password, phone);
        if (!validator.validate(request).isEmpty())
            throw new IllegalStateException("Bootstrap username, password or phone is invalid");
        auth.bootstrapAdmin(username,password,phone);
    }
}
