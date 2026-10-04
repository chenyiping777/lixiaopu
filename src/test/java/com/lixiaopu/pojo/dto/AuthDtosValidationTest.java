package com.lixiaopu.pojo.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthDtosValidationTest {
    @Test
    void validatesRegistrationFields() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();

            assertTrue(validator.validate(new AuthDtos.Register(
                    "buyer_1", "safe-password", "13800000000", "123456")).isEmpty());
            assertFalse(validator.validate(new AuthDtos.Register(
                    "x", "short", "123", "abc")).isEmpty());
            assertFalse(validator.validate(new AuthDtos.Register(
                    "buyer_1", "中文密码需要验证", "13800000000", "123456")).isEmpty());
        }
    }

    @Test
    void validatesLoginPhoneAndSmsPurpose() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();

            assertFalse(validator.validate(new AuthDtos.Login("123", "password", null)).isEmpty());
            assertFalse(validator.validate(new AuthDtos.Sms("13800000000", "UNKNOWN")).isEmpty());
        }
    }
}
