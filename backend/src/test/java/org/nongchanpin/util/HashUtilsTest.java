package org.nongchanpin.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HashUtilsTest {
    @Test
    void sameBusinessIdProducesStableBytes32Hash() {
        byte[] first = HashUtils.hashBusinessId("BATCH-001");
        byte[] second = HashUtils.hashBusinessId("BATCH-001");

        assertArrayEquals(first, second);
        assertEquals(32, first.length);
    }

    @Test
    void canonicalJsonIgnoresMapInsertionOrder() {
        Map<String, Object> left = new LinkedHashMap<>();
        left.put("productCode", "P001");
        left.put("batchCode", "B001");

        Map<String, Object> right = new LinkedHashMap<>();
        right.put("batchCode", "B001");
        right.put("productCode", "P001");

        assertArrayEquals(HashUtils.hashCanonicalJson(left), HashUtils.hashCanonicalJson(right));
    }
}
