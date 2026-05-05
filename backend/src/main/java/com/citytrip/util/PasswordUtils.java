package com.citytrip.util;

import org.springframework.security.crypto.bcrypt.BCrypt;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class PasswordUtils {
    private static final String BCRYPT_MARKER = "BCRYPT";

    private PasswordUtils() {
    }

    public static String hashPassword(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt(12));
    }

    public static boolean matchesPassword(String rawPassword, String storedHash, String storedSalt) {
        if (!hasText(rawPassword) || !hasText(storedHash)) {
            return false;
        }
        if (isBcryptHash(storedHash)) {
            return BCrypt.checkpw(rawPassword, storedHash);
        }
        return legacyHashPassword(rawPassword, storedSalt).equals(storedHash);
    }

    public static boolean needsRehash(String storedHash) {
        return !isBcryptHash(storedHash);
    }

    public static String bcryptStorageMarker() {
        return BCRYPT_MARKER;
    }

    private static String legacyHashPassword(String password, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] input = (salt + ":" + password).getBytes(StandardCharsets.UTF_8);
            return toHex(digest.digest(input));
        } catch (Exception e) {
            throw new IllegalStateException("密码加密失败", e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }

    private static boolean isBcryptHash(String value) {
        return hasText(value) && (value.startsWith("$2a$") || value.startsWith("$2b$") || value.startsWith("$2y$"));
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
