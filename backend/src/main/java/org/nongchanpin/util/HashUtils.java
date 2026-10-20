package org.nongchanpin.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class HashUtils {
    private static final ObjectMapper CANONICAL_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

    private HashUtils() {
    }

    public static byte[] hashBusinessId(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("business id must not be blank");
        }
        return sha256(value.trim().getBytes(StandardCharsets.UTF_8));
    }

    public static byte[] hashCanonicalJson(Object payload) {
        if (payload == null) {
            return sha256("{}".getBytes(StandardCharsets.UTF_8));
        }
        try {
            return sha256(toCanonicalJson(payload).getBytes(StandardCharsets.UTF_8));
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("payload cannot be serialized as canonical JSON", ex);
        }
    }

    public static String toCanonicalJson(Object payload) throws JsonProcessingException {
        if (payload == null) {
            return "{}";
        }
        return CANONICAL_MAPPER.writeValueAsString(payload);
    }

    public static String hashBusinessIdHex(String value) {
        return Bytes32Utils.toHex(hashBusinessId(value));
    }

    public static String hashCanonicalJsonHex(Object payload) {
        return Bytes32Utils.toHex(hashCanonicalJson(payload));
    }

    public static String hashCanonicalTextHex(String canonicalJson) {
        String value = canonicalJson == null || canonicalJson.trim().isEmpty() ? "{}" : canonicalJson;
        return Bytes32Utils.toHex(sha256(value.getBytes(StandardCharsets.UTF_8)));
    }

    public static String hashBusinessIdPlainHex(String value) {
        return withoutPrefix(hashBusinessIdHex(value));
    }

    public static String hashCanonicalJsonPlainHex(Object payload) {
        return withoutPrefix(hashCanonicalJsonHex(payload));
    }

    public static String hashCanonicalTextPlainHex(String canonicalJson) {
        return withoutPrefix(hashCanonicalTextHex(canonicalJson));
    }

    public static String withoutPrefix(String hex) {
        if (hex == null) {
            return null;
        }
        if (hex.startsWith("0x") || hex.startsWith("0X")) {
            return hex.substring(2);
        }
        return hex;
    }

    private static byte[] sha256(byte[] source) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(source);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
