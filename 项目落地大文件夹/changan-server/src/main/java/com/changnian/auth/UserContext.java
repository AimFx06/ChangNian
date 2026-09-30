package com.changnian.auth;

public final class UserContext {
    private static final ThreadLocal<CurrentUser> LOCAL = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(CurrentUser user) {
        LOCAL.set(user);
    }

    public static CurrentUser get() {
        CurrentUser user = LOCAL.get();
        if (user == null) {
            throw new IllegalStateException("未登录");
        }
        return user;
    }

    public static void clear() {
        LOCAL.remove();
    }
}
