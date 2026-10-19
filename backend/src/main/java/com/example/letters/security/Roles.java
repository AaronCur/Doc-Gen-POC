package com.example.letters.security;

/** Role names as used with {@code hasRole(...)} (Spring adds the {@code ROLE_} prefix). */
public final class Roles {

    public static final String ADMIN = "ADMIN";
    public static final String STAFF = "STAFF";
    public static final String EXTERNAL = "EXTERNAL";

    private Roles() {
    }
}
