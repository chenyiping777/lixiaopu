package com.lixiaopu.common.auth.session;

import com.lixiaopu.common.auth.security.AuthException;

public final class AuthContext {
    private static final ThreadLocal<TokenService.Principal> CURRENT=new ThreadLocal<>();
    private AuthContext() {}
    public static void set(TokenService.Principal principal) { CURRENT.set(principal); }
    public static TokenService.Principal get() {
        TokenService.Principal principal=CURRENT.get();
        if (principal==null) throw new AuthException(com.lixiaopu.common.result.ResultCode.UNAUTHORIZED);
        return principal;
    }
    public static void clear() { CURRENT.remove(); }
}
