package com.paytrack.expense;

public class UserContextHolder {

    private static final ThreadLocal<String> userId = new ThreadLocal<>();
    private static final ThreadLocal<String> role = new ThreadLocal<>();

    public static void setUserId(String uid) {
        userId.set(uid);
    }

    public static String getUserId() {
        return userId.get();
    }

    public static void setRole(String r) {
        role.set(r);
    }

    public static String getRole() {
        return role.get();
    }

    public static void clear() {
        userId.remove();
        role.remove();
    }
}
