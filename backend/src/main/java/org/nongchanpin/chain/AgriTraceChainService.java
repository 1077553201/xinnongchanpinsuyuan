package org.nongchanpin.chain;

import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.fisco.bcos.sdk.v3.client.Client;
import org.fisco.bcos.sdk.v3.codec.datatypes.generated.tuples.generated.Tuple6;
import org.fisco.bcos.sdk.v3.codec.datatypes.generated.tuples.generated.Tuple7;
import org.fisco.bcos.sdk.v3.codec.datatypes.generated.tuples.generated.Tuple8;
import org.fisco.bcos.sdk.v3.codec.datatypes.generated.tuples.generated.Tuple9;
import org.fisco.bcos.sdk.v3.crypto.keypair.CryptoKeyPair;
import org.fisco.bcos.sdk.v3.model.TransactionReceipt;
import org.nongchanpin.config.BcosConfig;
import org.nongchanpin.config.ContractConfig;
import org.nongchanpin.config.SystemConfig;
import org.nongchanpin.contracts.AgriTrace;
import org.nongchanpin.model.command.AppendSensorWindowCommand;
import org.nongchanpin.model.command.AppendTraceEventCommand;
import org.nongchanpin.model.command.BindItemCommand;
import org.nongchanpin.model.command.BindNfcTagCommand;
import org.nongchanpin.model.command.CloseBatchCommand;
import org.nongchanpin.model.command.RegisterBatchCommand;
import org.nongchanpin.model.command.UpdateItemStatusCommand;
import org.nongchanpin.model.command.UpdateNfcStatusCommand;
import org.nongchanpin.model.vo.ChainBatchView;
import org.nongchanpin.model.vo.ChainHealthResult;
import org.nongchanpin.model.vo.ChainItemView;
import org.nongchanpin.model.vo.ChainNfcView;
import org.nongchanpin.model.vo.ChainSensorWindowView;
import org.nongchanpin.model.vo.ChainTraceEventView;
import org.nongchanpin.model.vo.ChainTxResult;
import org.nongchanpin.util.Bytes32Utils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AgriTraceChainService implements ChainService {
    private static final String RECEIPT_SUCCESS = "0x0";

    private final Client client;
    private final BcosConfig bcosConfig;
    private final SystemConfig systemConfig;
    private final ContractConfig contractConfig;

    @Override
    public ChainHealthResult health() {
        ChainHealthResult result = new ChainHealthResult();
        result.setGroupId(systemConfig.getGroupId());
        result.setPeers(resolvePeers());
        result.setContractAddress(contractConfig.getAgriTraceAddress());
        result.setChainEnabled(chainConfigured());
        try {
            result.setLatestBlock(client.getBlockNumber().getBlockNumber());
            CryptoKeyPair keyPair = client.getCryptoSuite().getCryptoKeyPair();
            if (keyPair != null) {
                result.setAccountAddress(keyPair.getAddress());
                if (StringUtils.hasText(contractConfig.getAgriTraceAddress())) {
                    result.setWriterStatus(contract().isWriter(keyPair.getAddress()));
                }
            }
            result.setStatus("OK");
        } catch (Exception ex) {
            result.setStatus("FAILED");
            result.setChainEnabled(false);
            result.setErrorMessage(ex.getMessage());
        }
        return result;
    }

    @Override
    public ChainTxResult setWriter(String writerAddress, boolean allowed) {
        return executeTx("WriterUpdated", null, null, () -> contract().setWriter(writerAddress, allowed));
    }

    @Override
    public ChainTxResult registerBatch(RegisterBatchCommand command) {
        ChainParameterBuilder.RegisterBatchParams params = ChainParameterBuilder.registerBatch(command);
        return executeTx("BatchRegistered", params.getBatchIdHash(), params.getPayloadHash(),
                () -> contract().registerBatch(params.getBatchIdHash(), params.getProductIdHash(),
                        params.getPayloadHash(), params.getStartedAt()));
    }

    @Override
    public ChainTxResult closeBatch(CloseBatchCommand command) {
        ChainParameterBuilder.CloseBatchParams params = ChainParameterBuilder.closeBatch(command);
        return executeTx("BatchClosed", params.getBatchIdHash(), params.getReasonHash(),
                () -> contract().closeBatch(params.getBatchIdHash(), params.getStatus(), params.getReasonHash()));
    }

    @Override
    public ChainTxResult bindItem(BindItemCommand command) {
        ChainParameterBuilder.BindItemParams params = ChainParameterBuilder.bindItem(command);
        return executeTx("ItemBound", params.getItemCodeHash(), params.getPayloadHash(),
                () -> contract().bindItem(params.getItemCodeHash(), params.getBatchIdHash(), params.getPayloadHash()));
    }

    @Override
    public ChainTxResult bindNfcTag(BindNfcTagCommand command) {
        ChainParameterBuilder.BindNfcTagParams params = ChainParameterBuilder.bindNfcTag(command);
        return executeTx("NfcTagBound", params.getNfcUidHash(), params.getPayloadHash(),
                () -> contract().bindNfcTag(params.getItemCodeHash(), params.getNfcUidHash(), params.getPayloadHash()));
    }

    @Override
    public ChainTxResult updateNfcStatus(UpdateNfcStatusCommand command) {
        ChainParameterBuilder.UpdateNfcStatusParams params = ChainParameterBuilder.updateNfcStatus(command);
        return executeTx("NfcStatusUpdated", params.getNfcUidHash(), params.getReasonHash(),
                () -> contract().updateNfcStatus(params.getNfcUidHash(), params.getStatus(), params.getReasonHash()));
    }

    @Override
    public ChainTxResult appendTraceEvent(AppendTraceEventCommand command) {
        ChainParameterBuilder.AppendTraceEventParams params = ChainParameterBuilder.appendTraceEvent(command);
        return executeTx("TraceEventAppended", params.getEventIdHash(), params.getPayloadHash(),
                () -> contract().appendTraceEvent(params.getItemCodeHash(), params.getEventIdHash(),
                        params.getEventType(), params.getPayloadHash(), params.getOccurredAt()));
    }

    @Override
    public ChainTxResult appendSensorWindow(AppendSensorWindowCommand command) {
        ChainParameterBuilder.AppendSensorWindowParams params = ChainParameterBuilder.appendSensorWindow(command);
        return executeTx("SensorWindowAppended", params.getWindowIdHash(), params.getPayloadHash(),
                () -> contract().appendSensorWindow(params.getBatchIdHash(), params.getWindowIdHash(),
                        params.getDeviceCodeHash(), params.getPayloadHash(), params.getWindowStart(),
                        params.getWindowEnd()));
    }

    @Override
    public ChainTxResult updateItemStatus(UpdateItemStatusCommand command) {
        ChainParameterBuilder.UpdateItemStatusParams params = ChainParameterBuilder.updateItemStatus(command);
        return executeTx("ItemStatusUpdated", params.getItemCodeHash(), params.getReasonHash(),
                () -> contract().updateItemStatus(params.getItemCodeHash(), params.getStatus(), params.getReasonHash()));
    }

    @Override
    public ChainBatchView getBatch(String batchIdOrHash) {
        try {
            byte[] batchIdHash = ChainParameterBuilder.idHash(batchIdOrHash);
            Tuple8<Boolean, byte[], byte[], BigInteger, BigInteger, BigInteger, byte[], String> tuple =
                    contract().getBatch(batchIdHash);
            ChainBatchView view = new ChainBatchView();
            view.setBatchIdHash(Bytes32Utils.toHex(batchIdHash));
            view.setExists(tuple.getValue1());
            view.setProductIdHash(Bytes32Utils.toHex(tuple.getValue2()));
            view.setPayloadHash(Bytes32Utils.toHex(tuple.getValue3()));
            view.setStatus(tuple.getValue4());
            view.setStartedAt(tuple.getValue5());
            view.setClosedAt(tuple.getValue6());
            view.setCloseReasonHash(Bytes32Utils.toHex(tuple.getValue7()));
            view.setOperator(tuple.getValue8());
            view.setSensorWindowCount(contract().getSensorWindowCount(batchIdHash));
            return view;
        } catch (Exception ex) {
            throw chainException(ex);
        }
    }

    @Override
    public ChainItemView getItem(String itemCodeOrHash) {
        try {
            byte[] itemCodeHash = ChainParameterBuilder.idHash(itemCodeOrHash);
            Tuple9<Boolean, byte[], byte[], BigInteger, byte[], BigInteger, BigInteger, byte[], String> tuple =
                    contract().getItem(itemCodeHash);
            ChainItemView view = new ChainItemView();
            view.setItemCodeHash(Bytes32Utils.toHex(itemCodeHash));
            view.setExists(tuple.getValue1());
            view.setBatchIdHash(Bytes32Utils.toHex(tuple.getValue2()));
            view.setPayloadHash(Bytes32Utils.toHex(tuple.getValue3()));
            view.setStatus(tuple.getValue4());
            view.setNfcUidHash(Bytes32Utils.toHex(tuple.getValue5()));
            view.setCreatedAt(tuple.getValue6());
            view.setUpdatedAt(tuple.getValue7());
            view.setStatusReasonHash(Bytes32Utils.toHex(tuple.getValue8()));
            view.setOperator(tuple.getValue9());
            view.setTraceEventCount(contract().getTraceEventCount(itemCodeHash));
            return view;
        } catch (Exception ex) {
            throw chainException(ex);
        }
    }

    @Override
    public ChainNfcView getNfcTag(String nfcUidOrHash) {
        try {
            byte[] nfcUidHash = ChainParameterBuilder.idHash(nfcUidOrHash);
            Tuple8<Boolean, byte[], byte[], BigInteger, byte[], BigInteger, BigInteger, String> tuple =
                    contract().getNfcTag(nfcUidHash);
            ChainNfcView view = new ChainNfcView();
            view.setNfcUidHash(Bytes32Utils.toHex(nfcUidHash));
            view.setExists(tuple.getValue1());
            view.setItemCodeHash(Bytes32Utils.toHex(tuple.getValue2()));
            view.setPayloadHash(Bytes32Utils.toHex(tuple.getValue3()));
            view.setStatus(tuple.getValue4());
            view.setReasonHash(Bytes32Utils.toHex(tuple.getValue5()));
            view.setBoundAt(tuple.getValue6());
            view.setUpdatedAt(tuple.getValue7());
            view.setOperator(tuple.getValue8());
            return view;
        } catch (Exception ex) {
            throw chainException(ex);
        }
    }

    @Override
    public ChainTraceEventView getTraceEvent(String itemCodeOrHash, long index) {
        try {
            byte[] itemCodeHash = ChainParameterBuilder.idHash(itemCodeOrHash);
            BigInteger eventIndex = BigInteger.valueOf(index);
            Tuple6<byte[], BigInteger, byte[], BigInteger, BigInteger, String> tuple =
                    contract().getTraceEvent(itemCodeHash, eventIndex);
            ChainTraceEventView view = new ChainTraceEventView();
            view.setItemCodeHash(Bytes32Utils.toHex(itemCodeHash));
            view.setIndex(eventIndex);
            view.setEventIdHash(Bytes32Utils.toHex(tuple.getValue1()));
            view.setEventType(tuple.getValue2());
            view.setPayloadHash(Bytes32Utils.toHex(tuple.getValue3()));
            view.setOccurredAt(tuple.getValue4());
            view.setRecordedAt(tuple.getValue5());
            view.setOperator(tuple.getValue6());
            return view;
        } catch (Exception ex) {
            throw chainException(ex);
        }
    }

    @Override
    public ChainSensorWindowView getSensorWindow(String batchIdOrHash, long index) {
        try {
            byte[] batchIdHash = ChainParameterBuilder.idHash(batchIdOrHash);
            BigInteger windowIndex = BigInteger.valueOf(index);
            Tuple7<byte[], byte[], byte[], BigInteger, BigInteger, BigInteger, String> tuple =
                    contract().getSensorWindow(batchIdHash, windowIndex);
            ChainSensorWindowView view = new ChainSensorWindowView();
            view.setBatchIdHash(Bytes32Utils.toHex(batchIdHash));
            view.setIndex(windowIndex);
            view.setWindowIdHash(Bytes32Utils.toHex(tuple.getValue1()));
            view.setDeviceCodeHash(Bytes32Utils.toHex(tuple.getValue2()));
            view.setPayloadHash(Bytes32Utils.toHex(tuple.getValue3()));
            view.setWindowStart(tuple.getValue4());
            view.setWindowEnd(tuple.getValue5());
            view.setRecordedAt(tuple.getValue6());
            view.setOperator(tuple.getValue7());
            return view;
        } catch (Exception ex) {
            throw chainException(ex);
        }
    }

    private ChainTxResult executeTx(String eventName, byte[] businessIdHash, byte[] payloadHash,
            Supplier<TransactionReceipt> transaction) {
        if (!chainConfigured()) {
            return ChainTxResult.disabled(contractConfig.getAgriTraceAddress(),
                    "contract address or SYSTEM_HEX_PRIVATE_KEY is not configured");
        }
        ChainTxResult result = new ChainTxResult();
        result.setChainStatus(ChainStatus.PENDING);
        result.setSubmittedAt(Instant.now());
        result.setContractAddress(contractConfig.getAgriTraceAddress());
        result.setEventName(eventName);
        if (businessIdHash != null) {
            result.setBusinessIdHash(Bytes32Utils.toHex(businessIdHash));
        }
        if (payloadHash != null) {
            result.setPayloadHash(Bytes32Utils.toHex(payloadHash));
        }
        try {
            CryptoKeyPair keyPair = client.getCryptoSuite().getCryptoKeyPair();
            if (keyPair != null) {
                result.setChainOperator(keyPair.getAddress());
            }
            TransactionReceipt receipt = transaction.get();
            fillReceipt(result, receipt);
        } catch (Exception ex) {
            result.setChainStatus(ChainStatus.FAILED);
            result.setSuccess(false);
            result.setConfirmedAt(Instant.now());
            result.setErrorMessage(ex.getMessage());
        }
        return result;
    }

    private void fillReceipt(ChainTxResult result, TransactionReceipt receipt) {
        result.setTxHash(receipt.getTransactionHash());
        result.setBlockNumber(parseBigInteger(receipt.getBlockNumber()));
        result.setReceiptStatus(String.valueOf(receipt.getStatus()));
        result.setConfirmedAt(Instant.now());
        boolean success = RECEIPT_SUCCESS.equalsIgnoreCase(result.getReceiptStatus())
                || "0".equals(result.getReceiptStatus());
        result.setSuccess(success);
        result.setChainStatus(success ? ChainStatus.CONFIRMED : ChainStatus.FAILED);
        if (!success) {
            result.setErrorMessage(receipt.getMessage());
        }
    }

    private AgriTrace contract() {
        return AgriTrace.load(contractConfig.getAgriTraceAddress(), client, client.getCryptoSuite().getCryptoKeyPair());
    }

    private BigInteger parseBigInteger(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.startsWith("0x") || normalized.startsWith("0X")) {
            return new BigInteger(normalized.substring(2), 16);
        }
        return new BigInteger(normalized);
    }

    private boolean chainConfigured() {
        return StringUtils.hasText(contractConfig.getAgriTraceAddress())
                && StringUtils.hasText(systemConfig.getHexPrivateKey());
    }

    private List<String> resolvePeers() {
        List<String> peers = new ArrayList<>();
        Object value = bcosConfig.getNetwork() == null ? null : bcosConfig.getNetwork().get("peers");
        if (value instanceof List) {
            for (Object peer : (List<?>) value) {
                peers.add(String.valueOf(peer));
            }
        }
        return peers;
    }

    private IllegalStateException chainException(Exception ex) {
        return new IllegalStateException("AgriTrace chain call failed: " + ex.getMessage(), ex);
    }
}
