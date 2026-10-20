package org.nongchanpin.controller;

import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.nongchanpin.chain.ChainService;
import org.nongchanpin.service.ChainTransactionService;
import org.nongchanpin.model.CommonResponse;
import org.nongchanpin.model.command.AppendSensorWindowCommand;
import org.nongchanpin.model.command.AppendTraceEventCommand;
import org.nongchanpin.model.command.BindItemCommand;
import org.nongchanpin.model.command.BindNfcTagCommand;
import org.nongchanpin.model.command.CloseBatchCommand;
import org.nongchanpin.model.command.RegisterBatchCommand;
import org.nongchanpin.model.command.SetWriterCommand;
import org.nongchanpin.model.command.UpdateItemStatusCommand;
import org.nongchanpin.model.command.UpdateNfcStatusCommand;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/chain")
@RequiredArgsConstructor
public class ChainController {
    private final ChainService chainService;
    private final ChainTransactionService transactions;

    @GetMapping("/transactions")
    public CommonResponse transactions(@RequestParam(required = false) String businessType,
            @RequestParam(required = false) Long businessId, @RequestParam(required = false) String chainStatus,
            @RequestParam(required = false) Integer limit) {
        return CommonResponse.ok(transactions.findRecent(businessType, businessId, chainStatus, limit));
    }

    @GetMapping("/transactions/{id}")
    public CommonResponse transaction(@PathVariable Long id) { return CommonResponse.ok(transactions.get(id)); }

    @GetMapping("/health")
    public CommonResponse health() {
        return CommonResponse.ok(chainService.health());
    }

    @PostMapping("/writers")
    public CommonResponse setWriter(@Valid @RequestBody SetWriterCommand command) {
        return CommonResponse.ok(chainService.setWriter(command.getWriterAddress(), command.getAllowed()));
    }

    @PostMapping("/batches")
    public CommonResponse registerBatch(@Valid @RequestBody RegisterBatchCommand command) {
        return CommonResponse.ok(chainService.registerBatch(command));
    }

    @PostMapping("/batches/{batchCode}/close")
    public CommonResponse closeBatch(@PathVariable String batchCode,
            @Valid @RequestBody CloseBatchCommand command) {
        command.setBatchCode(batchCode);
        return CommonResponse.ok(chainService.closeBatch(command));
    }

    @PostMapping("/items")
    public CommonResponse bindItem(@Valid @RequestBody BindItemCommand command) {
        return CommonResponse.ok(chainService.bindItem(command));
    }

    @PostMapping("/nfc-tags")
    public CommonResponse bindNfcTag(@Valid @RequestBody BindNfcTagCommand command) {
        return CommonResponse.ok(chainService.bindNfcTag(command));
    }

    @PostMapping("/nfc-tags/{nfcUid}/status")
    public CommonResponse updateNfcStatus(@PathVariable String nfcUid,
            @Valid @RequestBody UpdateNfcStatusCommand command) {
        command.setNfcUid(nfcUid);
        return CommonResponse.ok(chainService.updateNfcStatus(command));
    }

    @PostMapping("/items/{itemCode}/events")
    public CommonResponse appendTraceEvent(@PathVariable String itemCode,
            @Valid @RequestBody AppendTraceEventCommand command) {
        command.setItemCode(itemCode);
        return CommonResponse.ok(chainService.appendTraceEvent(command));
    }

    @PostMapping("/batches/{batchCode}/sensor-windows")
    public CommonResponse appendSensorWindow(@PathVariable String batchCode,
            @Valid @RequestBody AppendSensorWindowCommand command) {
        command.setBatchCode(batchCode);
        return CommonResponse.ok(chainService.appendSensorWindow(command));
    }

    @PostMapping("/items/{itemCode}/status")
    public CommonResponse updateItemStatus(@PathVariable String itemCode,
            @Valid @RequestBody UpdateItemStatusCommand command) {
        command.setItemCode(itemCode);
        return CommonResponse.ok(chainService.updateItemStatus(command));
    }

    @GetMapping("/batches/{batchCode}")
    public CommonResponse getBatch(@PathVariable String batchCode) {
        return CommonResponse.ok(chainService.getBatch(batchCode));
    }

    @GetMapping("/items/{itemCode}")
    public CommonResponse getItem(@PathVariable String itemCode) {
        return CommonResponse.ok(chainService.getItem(itemCode));
    }

    @GetMapping("/nfc-tags/{nfcUid}")
    public CommonResponse getNfcTag(@PathVariable String nfcUid) {
        return CommonResponse.ok(chainService.getNfcTag(nfcUid));
    }

    @GetMapping("/items/{itemCode}/events/{index}")
    public CommonResponse getTraceEvent(@PathVariable String itemCode, @PathVariable long index) {
        return CommonResponse.ok(chainService.getTraceEvent(itemCode, index));
    }

    @GetMapping("/batches/{batchCode}/sensor-windows/{index}")
    public CommonResponse getSensorWindow(@PathVariable String batchCode, @PathVariable long index) {
        return CommonResponse.ok(chainService.getSensorWindow(batchCode, index));
    }
}
