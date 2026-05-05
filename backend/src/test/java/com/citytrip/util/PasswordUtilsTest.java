package com.citytrip.util;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordUtilsTest {

    @Test
    void hashPasswordCreatesBcryptHash() {
        String hash = PasswordUtils.hashPassword("password123");

        assertThat(hash).startsWith("$2");
        assertThat(PasswordUtils.matchesPassword("password123", hash, PasswordUtils.bcryptStorageMarker())).isTrue();
        assertThat(PasswordUtils.needsRehash(hash)).isFalse();
    }

    @Test
    void matchesPasswordSupportsLegacySha256Hashes() throws Exception {
        String salt = "legacy-salt";
        String legacyHash = sha256Hex(salt + ":" + "password123");

        assertThat(PasswordUtils.matchesPassword("password123", legacyHash, salt)).isTrue();
        assertThat(PasswordUtils.needsRehash(legacyHash)).isTrue();
    }

    private String sha256Hex(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder(hashed.length * 2);
        for (byte b : hashed) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }
}
