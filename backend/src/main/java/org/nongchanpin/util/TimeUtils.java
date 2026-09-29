package org.nongchanpin.util;

import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

public final class TimeUtils {
    private TimeUtils() {
    }

    public static BigInteger epochSeconds(Long epochSeconds) {
        long value = epochSeconds == null ? Instant.now().getEpochSecond() : epochSeconds;
        if (value <= 0) {
            throw new IllegalArgumentException("epoch seconds must be greater than zero");
        }
        return BigInteger.valueOf(value);
    }

    public static BigInteger epochSeconds(LocalDateTime dateTime, ZoneId zoneId) {
        if (dateTime == null) {
            return epochSeconds((Long) null);
        }
        return BigInteger.valueOf(dateTime.atZone(zoneId).toEpochSecond());
    }
}
