package com.paytrack.transaction;

public class UserContextHolder {

    private static final ThreadLocal<String> userId = new ThreadLocal<>();

    public static void setUserId(String uid) {
        userId.set(uid);
    }

    public static String getUserId() {
        return userId.get();
    }

    public static void clear() {
        userId.remove();
    }
}
