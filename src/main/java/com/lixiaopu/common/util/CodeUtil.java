package com.lixiaopu.common.util;
import java.security.SecureRandom;
public final class CodeUtil {
    public static final String CODE_REGEX = "[0-9]{6}";
    public static final String CODE_FORMAT_MESSAGE = "验证码须为 6 位数字";
    private static final SecureRandom RANDOM = new SecureRandom();
    private CodeUtil() { }
    public static String generateSixDigitCode() {
        return String.format(java.util.Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
    }
}