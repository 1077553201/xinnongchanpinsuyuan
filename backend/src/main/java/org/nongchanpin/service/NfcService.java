package org.nongchanpin.service;

import java.time.LocalDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.nongchanpin.chain.ChainService;
import org.nongchanpin.config.ContractConfig;
import org.nongchanpin.constants.AgriTraceCodes;
import org.nongchanpin.mapper.NfcTagMapper;
import org.nongchanpin.mapper.TraceItemMapper;
import org.nongchanpin.model.command.BindNfcTagCommand;
import org.nongchanpin.model.command.UpdateNfcStatusCommand;
import org.nongchanpin.model.dto.BindNfcRequest;
import org.nongchanpin.model.dto.DisableNfcRequest;
import org.nongchanpin.model.entity.ChainTransactionEntity;
import org.nongchanpin.model.entity.NfcTagEntity;
import org.nongchanpin.model.entity.TraceItemEntity;
import org.nongchanpin.util.HashUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NfcService {
    private final NfcTagMapper nfcTagMapper;
    private final TraceItemMapper traceItemMapper;
    private final ChainService chainService;
    private final ChainTransactionService chainTransactionService;
    private final ChainWriteSupport chainWriteSupport;
    private final PayloadService payloadService;
    private final ContractConfig contractConfig;

    @Transactional
    public NfcTagEntity bind(BindNfcRequest request) {
        TraceItemEntity item = traceItemMapper.findById(request.getItemId());
        if (item == null) {
            throw new IllegalArgumentException("item not found");
        }
        String uidHash = HashUtils.hashBusinessIdPlainHex(request.getNfcUid());
        if (nfcTagMapper.findByUidHash(uidHash) != null) {
            throw new IllegalArgumentException("nfc uid already exists");
        }
        NfcTagEntity tag = new NfcTagEntity();
        tag.setUidHash(uidHash);
        tag.setTagHash(request.getTagHash());
        tag.setItemId(item.getId());
        tag.setBindStatus("BOUND");
        tag.setBoundAt(LocalDateTime.now());
        tag.setStatus("ACTIVE");
        nfcTagMapper.insert(tag);

        Map<String, Object> payload = payloadService.map();
        payload.put("itemTraceCodeHash", item.getTraceCodeHash());
        payload.put("nfcUidHash", uidHash);
        payload.put("tagHash", request.getTagHash());
        payload.put("bindPayload", request.getPayload());
        String payloadHash = payloadService.payloadHashWithPrefix(payload);
        ChainTransactionEntity tx = chainTransactionService.createPending("NFC_BIND", tag.getId(),
                HashUtils.hashBusinessIdHex(request.getNfcUid()), contractConfig.getAgriTraceAddress(),
                "NfcTagBound", payloadHash, payloadService.requestHash(payload));

        BindNfcTagCommand command = new BindNfcTagCommand();
        command.setItemCode(itemCodeForChain(item));
        command.setNfcUid(request.getNfcUid());
        command.setPayload(payload);
        String chainStatus = chainWriteSupport.submit(tx, () -> chainService.bindNfcTag(command));
        traceItemMapper.updateNfcStatus(item.getId(), "BOUND:" + chainStatus);
        return nfcTagMapper.findById(tag.getId());
    }

    @Transactional
    public NfcTagEntity disable(Long id, DisableNfcRequest request) {
        NfcTagEntity tag = nfcTagMapper.findById(id);
        if (tag == null) {
            throw new IllegalArgumentException("nfc tag not found");
        }
        Map<String, Object> reason = payloadService.map();
        reason.put("nfcUidHash", tag.getUidHash());
        reason.put("reason", request.getReasonPayload());
        String payloadHash = payloadService.payloadHashWithPrefix(reason);
        ChainTransactionEntity tx = chainTransactionService.createPending("NFC_DISABLE", tag.getId(),
                "0x" + tag.getUidHash(), contractConfig.getAgriTraceAddress(),
                "NfcStatusUpdated", payloadHash, payloadService.requestHash(reason));

        UpdateNfcStatusCommand command = new UpdateNfcStatusCommand();
        command.setNfcUid("0x" + tag.getUidHash());
        command.setStatus(AgriTraceCodes.NFC_STATUS_DISABLED);
        command.setReasonPayload(reason);
        String chainStatus = chainWriteSupport.submit(tx, () -> chainService.updateNfcStatus(command));
        nfcTagMapper.updateStatus(tag.getId(), "DISABLED:" + chainStatus, "DISABLED");
        return nfcTagMapper.findById(tag.getId());
    }

    private String itemCodeForChain(TraceItemEntity item) {
        return item.getTraceCodePlain() == null || item.getTraceCodePlain().trim().isEmpty()
                ? "0x" + item.getTraceCodeHash()
                : item.getTraceCodePlain();
    }
}
