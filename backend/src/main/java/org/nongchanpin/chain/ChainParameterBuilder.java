package org.nongchanpin.chain;

import java.math.BigInteger;
import lombok.Value;
import org.nongchanpin.constants.AgriTraceCodes;
import org.nongchanpin.model.command.AppendSensorWindowCommand;
import org.nongchanpin.model.command.AppendTraceEventCommand;
import org.nongchanpin.model.command.BindItemCommand;
import org.nongchanpin.model.command.BindNfcTagCommand;
import org.nongchanpin.model.command.CloseBatchCommand;
import org.nongchanpin.model.command.RegisterBatchCommand;
import org.nongchanpin.model.command.UpdateItemStatusCommand;
import org.nongchanpin.model.command.UpdateNfcStatusCommand;
import org.nongchanpin.util.Bytes32Utils;
import org.nongchanpin.util.HashUtils;
import org.nongchanpin.util.TimeUtils;

public final class ChainParameterBuilder {
    private ChainParameterBuilder() {
    }

    public static byte[] idHash(String idOrHash) {
        if (Bytes32Utils.isBytes32Hex(idOrHash)) {
            return Bytes32Utils.fromHex(idOrHash);
        }
        return HashUtils.hashBusinessId(idOrHash);
    }

    public static RegisterBatchParams registerBatch(RegisterBatchCommand command) {
        return new RegisterBatchParams(
                idHash(command.getBatchCode()),
                idHash(command.getProductCode()),
                HashUtils.hashCanonicalJson(command.getPayload()),
                TimeUtils.epochSeconds(command.getStartedAt()));
    }

    public static CloseBatchParams closeBatch(CloseBatchCommand command) {
        if (!AgriTraceCodes.isValidBatchCloseStatus(command.getStatus())) {
            throw new IllegalArgumentException("batch close status must be 2(CLOSED) or 3(RECALL)");
        }
        return new CloseBatchParams(
                idHash(command.getBatchCode()),
                BigInteger.valueOf(command.getStatus()),
                HashUtils.hashCanonicalJson(command.getReasonPayload()));
    }

    public static BindItemParams bindItem(BindItemCommand command) {
        return new BindItemParams(
                idHash(command.getItemCode()),
                idHash(command.getBatchCode()),
                HashUtils.hashCanonicalJson(command.getPayload()));
    }

    public static BindNfcTagParams bindNfcTag(BindNfcTagCommand command) {
        return new BindNfcTagParams(
                idHash(command.getItemCode()),
                idHash(command.getNfcUid()),
                HashUtils.hashCanonicalJson(command.getPayload()));
    }

    public static UpdateNfcStatusParams updateNfcStatus(UpdateNfcStatusCommand command) {
        if (!AgriTraceCodes.isValidNfcStatus(command.getStatus())) {
            throw new IllegalArgumentException("nfc status must be 1(ACTIVE), 2(REPLACED), or 3(DISABLED)");
        }
        return new UpdateNfcStatusParams(
                idHash(command.getNfcUid()),
                BigInteger.valueOf(command.getStatus()),
                HashUtils.hashCanonicalJson(command.getReasonPayload()));
    }

    public static AppendTraceEventParams appendTraceEvent(AppendTraceEventCommand command) {
        if (!AgriTraceCodes.isValidEventType(command.getEventType())) {
            throw new IllegalArgumentException("event type must be in range 1..11");
        }
        return new AppendTraceEventParams(
                idHash(command.getItemCode()),
                idHash(command.getEventId()),
                BigInteger.valueOf(command.getEventType()),
                HashUtils.hashCanonicalJson(command.getPayload()),
                TimeUtils.epochSeconds(command.getOccurredAt()));
    }

    public static AppendSensorWindowParams appendSensorWindow(AppendSensorWindowCommand command) {
        BigInteger windowStart = TimeUtils.epochSeconds(command.getWindowStart());
        BigInteger windowEnd = TimeUtils.epochSeconds(command.getWindowEnd());
        if (windowEnd.compareTo(windowStart) < 0) {
            throw new IllegalArgumentException("windowEnd must be greater than or equal to windowStart");
        }
        return new AppendSensorWindowParams(
                idHash(command.getBatchCode()),
                idHash(command.getWindowId()),
                idHash(command.getDeviceCode()),
                HashUtils.hashCanonicalJson(command.getPayload()),
                windowStart,
                windowEnd);
    }

    public static UpdateItemStatusParams updateItemStatus(UpdateItemStatusCommand command) {
        if (!AgriTraceCodes.isValidItemStatus(command.getStatus())) {
            throw new IllegalArgumentException("item status must be in range 1..5");
        }
        return new UpdateItemStatusParams(
                idHash(command.getItemCode()),
                BigInteger.valueOf(command.getStatus()),
                HashUtils.hashCanonicalJson(command.getReasonPayload()));
    }

    @Value
    public static class RegisterBatchParams {
        byte[] batchIdHash;
        byte[] productIdHash;
        byte[] payloadHash;
        BigInteger startedAt;
    }

    @Value
    public static class CloseBatchParams {
        byte[] batchIdHash;
        BigInteger status;
        byte[] reasonHash;
    }

    @Value
    public static class BindItemParams {
        byte[] itemCodeHash;
        byte[] batchIdHash;
        byte[] payloadHash;
    }

    @Value
    public static class BindNfcTagParams {
        byte[] itemCodeHash;
        byte[] nfcUidHash;
        byte[] payloadHash;
    }

    @Value
    public static class UpdateNfcStatusParams {
        byte[] nfcUidHash;
        BigInteger status;
        byte[] reasonHash;
    }

    @Value
    public static class AppendTraceEventParams {
        byte[] itemCodeHash;
        byte[] eventIdHash;
        BigInteger eventType;
        byte[] payloadHash;
        BigInteger occurredAt;
    }

    @Value
    public static class AppendSensorWindowParams {
        byte[] batchIdHash;
        byte[] windowIdHash;
        byte[] deviceCodeHash;
        byte[] payloadHash;
        BigInteger windowStart;
        BigInteger windowEnd;
    }

    @Value
    public static class UpdateItemStatusParams {
        byte[] itemCodeHash;
        BigInteger status;
        byte[] reasonHash;
    }
}
