package org.nongchanpin.constants;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AgriTraceCodesTest {
    @Test
    void statusAndEventTypesMatchContractReadme() {
        assertTrue(AgriTraceCodes.isValidBatchCloseStatus(2));
        assertTrue(AgriTraceCodes.isValidBatchCloseStatus(3));
        assertFalse(AgriTraceCodes.isValidBatchCloseStatus(1));

        assertTrue(AgriTraceCodes.isValidItemStatus(1));
        assertTrue(AgriTraceCodes.isValidItemStatus(5));
        assertFalse(AgriTraceCodes.isValidItemStatus(6));

        assertTrue(AgriTraceCodes.isValidNfcStatus(1));
        assertTrue(AgriTraceCodes.isValidNfcStatus(3));
        assertFalse(AgriTraceCodes.isValidNfcStatus(4));

        assertTrue(AgriTraceCodes.isValidEventType(1));
        assertTrue(AgriTraceCodes.isValidEventType(11));
        assertFalse(AgriTraceCodes.isValidEventType(12));
    }
}
