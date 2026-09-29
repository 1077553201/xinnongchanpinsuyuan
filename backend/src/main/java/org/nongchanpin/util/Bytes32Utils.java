package org.nongchanpin.util;

import java.util.Arrays;

public final class Bytes32Utils {
    public static final int BYTES32_LENGTH = 32;

    private Bytes32Utils() {
    }

    public static byte[] requireBytes32(byte[] value) {
        if (value == null || value.length != BYTES32_LENGTH) {
            throw new IllegalArgumentException("bytes32 value must be exactly 32 bytes");
        }
        return Arrays.copyOf(value, BYTES32_LENGTH);
    }

    public static byte[] fromHex(String hex) {
        String normalized = normalizeHex(hex);
        if (normalized.length() != BYTES32_LENGTH * 2) {
            throw new IllegalArgumentException("bytes32 hex string must be exactly 64 hex characters");
        }
        byte[] out = new byte[BYTES32_LENGTH];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(normalized.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    public static String toHex(byte[] value) {
        byte[] bytes = requireBytes32(value);
        StringBuilder builder = new StringBuilder(BYTES32_LENGTH * 2 + 2);
        builder.append("0x");
        for (byte b : bytes) {
            builder.append(String.format("%02x", b & 0xff));
        }
        return builder.toString();
    }

    public static boolean isBytes32Hex(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.startsWith("0x") || value.startsWith("0X") ? value.substring(2) : value;
        return normalized.matches("[0-9a-fA-F]{64}");
    }

    public static String normalizeHex(String hex) {
        if (hex == null) {
            throw new IllegalArgumentException("hex string must not be null");
        }
        String normalized = hex.trim();
        if (normalized.startsWith("0x") || normalized.startsWith("0X")) {
            normalized = normalized.substring(2);
        }
        if (!normalized.matches("[0-9a-fA-F]+")) {
            throw new IllegalArgumentException("invalid hex string");
        }
        return normalized.toLowerCase();
    }
}
