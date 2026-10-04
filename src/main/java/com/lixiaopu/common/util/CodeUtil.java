package com.lixiaopu.common.util;


import java.util.Random;

public class CodeUtil {

    private static final Random RANDOM = new Random();
    // 字母池（大写）
    private static final char[] LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
    // 数字池
    private static final char[] NUMBERS = "0123456789".toCharArray();

    /**
     * 生成5位验证码，保证至少1个字母 + 至少1个数字
     * @return 5位验证码字符串
     */
    public static String generateFiveCode() {
        char[] code = new char[5];
        // 第一位强制放字母，保证一定有字母
        code[0] = LETTERS[RANDOM.nextInt(LETTERS.length)];
        // 第二位强制放数字，保证一定有数字
        code[1] = NUMBERS[RANDOM.nextInt(NUMBERS.length)];

        // 剩下3位随机从【字母+数字】混合池取
        char[] allChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
        for (int i = 2; i < 5; i++) {
            code[i] = allChars[RANDOM.nextInt(allChars.length)];
        }

        // 打乱数组顺序，避免固定第1位字母、第2位数字
        shuffle(code);
        return new String(code);
    }

    /**
     * 打乱字符数组顺序
     */
    private static void shuffle(char[] arr) {
        for (int i = arr.length - 1; i > 0; i--) {
            int idx = RANDOM.nextInt(i + 1);
            char temp = arr[i];
            arr[i] = arr[idx];
            arr[idx] = temp;
        }
    }


}
