package org.nongchanpin.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.Map;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class HashUtilsTest {
    @Test
    void localDatesUseStableIsoTextInCanonicalPayloads() throws Exception {
        Map<String, Object> dates = new LinkedHashMap<>();
        dates.put("plantDate", LocalDate.of(2026, 3, 1));
        dates.put("harvestDate", LocalDate.of(2026, 10, 1));
        Map<String, Object> strings = new LinkedHashMap<>();
        strings.put("harvestDate", "2026-10-01");
        strings.put("plantDate", "2026-03-01");
        assertEquals("{\"harvestDate\":\"2026-10-01\",\"plantDate\":\"2026-03-01\"}", HashUtils.toCanonicalJson(dates));
        assertArrayEquals(HashUtils.hashCanonicalJson(dates), HashUtils.hashCanonicalJson(strings));
    }

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
