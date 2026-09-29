package org.nongchanpin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PayloadServiceTest {
    private final PayloadService payloadService = new PayloadService();

    @Test
    void canonicalPayloadHashIsStableAcrossMapOrder() {
        Map<String, Object> left = new LinkedHashMap<>();
        left.put("productCode", "P001");
        left.put("batchCode", "B001");

        Map<String, Object> right = new LinkedHashMap<>();
        right.put("batchCode", "B001");
        right.put("productCode", "P001");

        assertEquals(payloadService.payloadHash(payloadService.canonicalJson(left)),
                payloadService.payloadHash(payloadService.canonicalJson(right)));
    }
}
