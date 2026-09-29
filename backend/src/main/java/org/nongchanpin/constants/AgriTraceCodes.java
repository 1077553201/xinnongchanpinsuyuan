package org.nongchanpin.constants;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class AgriTraceCodes {
    public static final int BATCH_STATUS_ACTIVE = 1;
    public static final int BATCH_STATUS_CLOSED = 2;
    public static final int BATCH_STATUS_RECALL = 3;

    public static final int ITEM_STATUS_CREATED = 1;
    public static final int ITEM_STATUS_ACTIVE = 2;
    public static final int ITEM_STATUS_SOLD = 3;
    public static final int ITEM_STATUS_RECALL = 4;
    public static final int ITEM_STATUS_VOID = 5;

    public static final int NFC_STATUS_ACTIVE = 1;
    public static final int NFC_STATUS_REPLACED = 2;
    public static final int NFC_STATUS_DISABLED = 3;

    public static final Map<Integer, String> EVENT_TYPES;

    static {
        Map<Integer, String> eventTypes = new HashMap<>();
        eventTypes.put(1, "SOWING");
        eventTypes.put(2, "FARMING");
        eventTypes.put(3, "HARVEST");
        eventTypes.put(4, "PROCESSING");
        eventTypes.put(5, "QUALITY_TEST");
        eventTypes.put(6, "WAREHOUSE_IN");
        eventTypes.put(7, "WAREHOUSE_OUT");
        eventTypes.put(8, "LOGISTICS");
        eventTypes.put(9, "STORE_ARRIVAL");
        eventTypes.put(10, "SALE");
        eventTypes.put(11, "RECALL");
        EVENT_TYPES = Collections.unmodifiableMap(eventTypes);
    }

    private AgriTraceCodes() {
    }

    public static boolean isValidBatchCloseStatus(int status) {
        return status == BATCH_STATUS_CLOSED || status == BATCH_STATUS_RECALL;
    }

    public static boolean isValidItemStatus(int status) {
        return status >= ITEM_STATUS_CREATED && status <= ITEM_STATUS_VOID;
    }

    public static boolean isValidNfcStatus(int status) {
        return status >= NFC_STATUS_ACTIVE && status <= NFC_STATUS_DISABLED;
    }

    public static boolean isValidEventType(int eventType) {
        return EVENT_TYPES.containsKey(eventType);
    }

    public static int eventTypeCode(String name) {
        if (name == null) {
            throw new IllegalArgumentException("event type must not be blank");
        }
        String normalized = name.trim().toUpperCase();
        for (Map.Entry<Integer, String> entry : EVENT_TYPES.entrySet()) {
            if (entry.getValue().equals(normalized)) {
                return entry.getKey();
            }
        }
        throw new IllegalArgumentException("unsupported event type: " + name);
    }
}
