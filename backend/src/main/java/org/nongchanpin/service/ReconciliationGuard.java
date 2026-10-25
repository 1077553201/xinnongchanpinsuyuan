package org.nongchanpin.service;

import java.util.Objects;

final class ReconciliationGuard {
    private ReconciliationGuard() {}
    static void check(String hash,Long tx,String expectedHash,Long expectedTx) {
        if(!Objects.equals(hash,expectedHash)||!Objects.equals(tx,expectedTx))
            throw new IllegalArgumentException("冻结内容或交易意图已变化，请重新查链核对。");
    }
}
