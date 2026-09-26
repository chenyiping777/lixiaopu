package com.lixiaopu.common.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 认证接口使用的请求与响应对象。 */
public final class AuthDtos {
    private static final String PHONE_REGEX = "1[3-9]\\d{9}";
    private static final String USERNAME_REGEX = "[A-Za-z0-9_]{3,64}";
    // BCrypt 只处理密码的前 72 字节；限制为可打印 ASCII 后，字符数等于字节数。
    private static final String PASSWORD_REGEX = "[\\x20-\\x7E]{8,72}";

    private AuthDtos() {
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Login {
        @NotBlank
        @Pattern(regexp = PHONE_REGEX, message = "手机号格式错误")
        private String phone;
        @NotBlank
        private String password;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Register {
        @NotBlank
        @Pattern(regexp = USERNAME_REGEX, message = "用户名须为 3-64 位字母、数字或下划线")
        private String username;
        @NotBlank
        @Pattern(regexp = PASSWORD_REGEX, message = "密码须为 8-72 位可打印 ASCII 字符")
        private String password;
        @NotBlank
        @Pattern(regexp = PHONE_REGEX, message = "手机号格式错误")
        private String phone;
        @NotBlank
        @Pattern(regexp = "\\d{6}", message = "验证码须为 6 位数字")
        private String code;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Wechat {
        @NotBlank
        private String code;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Sms {
        @NotBlank
        @Pattern(regexp = PHONE_REGEX, message = "手机号格式错误")
        private String phone;
        @NotBlank
        @Pattern(regexp = "REGISTER|RESET_PASSWORD", message = "短信用途不支持")
        private String purpose;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResetVerify {
        @NotBlank
        @Pattern(regexp = PHONE_REGEX, message = "手机号格式错误")
        private String phone;
        @NotBlank
        @Pattern(regexp = "\\d{6}", message = "验证码须为 6 位数字")
        private String code;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResetPassword {
        @NotBlank
        private String ticket;
        @NotBlank
        @Pattern(regexp = PASSWORD_REGEX, message = "密码须为 8-72 位可打印 ASCII 字符")
        private String password;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChangePassword {
        @NotBlank
        private String oldPassword;
        @NotBlank
        @Pattern(regexp = PASSWORD_REGEX, message = "密码须为 8-72 位可打印 ASCII 字符")
        private String newPassword;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Refresh {
        @NotBlank
        private String refreshToken;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateAdmin {
        @NotBlank
        @Pattern(regexp = USERNAME_REGEX, message = "用户名须为 3-64 位字母、数字或下划线")
        private String username;
        @NotBlank
        @Pattern(regexp = PASSWORD_REGEX, message = "密码须为 8-72 位可打印 ASCII 字符")
        private String password;
        @NotBlank
        @Pattern(regexp = PHONE_REGEX, message = "手机号格式错误")
        private String phone;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Tokens {
        private String token;// 访问令牌
        private String refreshToken;// 刷新令牌
        private Long userId;// 用户 ID
        private String sessionId;// 会话 ID
    }
}
