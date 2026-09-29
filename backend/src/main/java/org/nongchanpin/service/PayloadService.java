package org.nongchanpin.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.nongchanpin.util.HashUtils;
import org.springframework.stereotype.Service;

@Service
public class PayloadService {
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String canonicalJson(Object payload) {
        try {
            return HashUtils.toCanonicalJson(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("payload cannot be serialized", ex);
        }
    }

    public String payloadHash(String canonicalJson) {
        return HashUtils.hashCanonicalTextPlainHex(canonicalJson);
    }

    public String payloadHashWithPrefix(Object payload) {
        return HashUtils.hashCanonicalJsonHex(payload);
    }

    public String requestHash(Object payload) {
        return HashUtils.hashCanonicalJsonPlainHex(payload);
    }

    public Map<String, Object> map() {
        return new LinkedHashMap<>();
    }

    public Object readJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            return json;
        }
    }
}
