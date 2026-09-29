package org.nongchanpin.service;

import java.lang.Exception;
import java.lang.String;
import java.util.Arrays;
import javax.annotation.PostConstruct;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.nongchanpin.constants.ContractConstants;
import org.nongchanpin.model.bo.AgriTraceAppendSensorWindowInputBO;
import org.nongchanpin.model.bo.AgriTraceAppendTraceEventInputBO;
import org.nongchanpin.model.bo.AgriTraceBatchExistsInputBO;
import org.nongchanpin.model.bo.AgriTraceBindItemInputBO;
import org.nongchanpin.model.bo.AgriTraceBindNfcTagInputBO;
import org.nongchanpin.model.bo.AgriTraceCloseBatchInputBO;
import org.nongchanpin.model.bo.AgriTraceGetBatchInputBO;
import org.nongchanpin.model.bo.AgriTraceGetItemInputBO;
import org.nongchanpin.model.bo.AgriTraceGetNfcTagInputBO;
import org.nongchanpin.model.bo.AgriTraceGetSensorWindowCountInputBO;
import org.nongchanpin.model.bo.AgriTraceGetSensorWindowInputBO;
import org.nongchanpin.model.bo.AgriTraceGetTraceEventCountInputBO;
import org.nongchanpin.model.bo.AgriTraceGetTraceEventInputBO;
import org.nongchanpin.model.bo.AgriTraceIsWriterInputBO;
import org.nongchanpin.model.bo.AgriTraceItemExistsInputBO;
import org.nongchanpin.model.bo.AgriTraceNfcExistsInputBO;
import org.nongchanpin.model.bo.AgriTraceRegisterBatchInputBO;
import org.nongchanpin.model.bo.AgriTraceSetWriterInputBO;
import org.nongchanpin.model.bo.AgriTraceUpdateItemStatusInputBO;
import org.nongchanpin.model.bo.AgriTraceUpdateNfcStatusInputBO;
import org.fisco.bcos.sdk.v3.client.Client;
import org.fisco.bcos.sdk.v3.transaction.manager.AssembleTransactionProcessor;
import org.fisco.bcos.sdk.v3.transaction.manager.TransactionProcessorFactory;
import org.fisco.bcos.sdk.v3.transaction.model.dto.CallResponse;
import org.fisco.bcos.sdk.v3.transaction.model.dto.TransactionResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@NoArgsConstructor
@Data
public class AgriTraceService {
  @Value("${contract.agriTraceAddress}")
  private String address;

  @Autowired
  private Client client;

  AssembleTransactionProcessor txProcessor;

  @PostConstruct
  public void init() throws Exception {
    this.txProcessor = TransactionProcessorFactory.createAssembleTransactionProcessor(this.client, this.client.getCryptoSuite().getCryptoKeyPair());
  }

  public CallResponse BATCH_STATUS_CLOSED() throws Exception {
    return this.txProcessor.sendCall(this.client.getCryptoSuite().getCryptoKeyPair().getAddress(), this.address, ContractConstants.AgriTraceAbi, "BATCH_STATUS_CLOSED", Arrays.asList());
  }

  public CallResponse getBatch(AgriTraceGetBatchInputBO input) throws Exception {
    return this.txProcessor.sendCall(this.client.getCryptoSuite().getCryptoKeyPair().getAddress(), this.address, ContractConstants.AgriTraceAbi, "getBatch", input.toArgs());
  }

  public CallResponse getSensorWindowCount(AgriTraceGetSensorWindowCountInputBO input) throws
      Exception {
    return this.txProcessor.sendCall(this.client.getCryptoSuite().getCryptoKeyPair().getAddress(), this.address, ContractConstants.AgriTraceAbi, "getSensorWindowCount", input.toArgs());
  }

  public TransactionResponse updateItemStatus(AgriTraceUpdateItemStatusInputBO input) throws
      Exception {
    return this.txProcessor.sendTransactionAndGetResponse(this.address, ContractConstants.AgriTraceAbi, "updateItemStatus", input.toArgs());
  }

  public CallResponse NFC_STATUS_ACTIVE() throws Exception {
    return this.txProcessor.sendCall(this.client.getCryptoSuite().getCryptoKeyPair().getAddress(), this.address, ContractConstants.AgriTraceAbi, "NFC_STATUS_ACTIVE", Arrays.asList());
  }

  public CallResponse NFC_STATUS_DISABLED() throws Exception {
    return this.txProcessor.sendCall(this.client.getCryptoSuite().getCryptoKeyPair().getAddress(), this.address, ContractConstants.AgriTraceAbi, "NFC_STATUS_DISABLED", Arrays.asList());
  }

  public CallResponse getNfcTag(AgriTraceGetNfcTagInputBO input) throws Exception {
    return this.txProcessor.sendCall(this.client.getCryptoSuite().getCryptoKeyPair().getAddress(), this.address, ContractConstants.AgriTraceAbi, "getNfcTag", input.toArgs());
  }

  public TransactionResponse appendSensorWindow(AgriTraceAppendSensorWindowInputBO input) throws
      Exception {
    return this.txProcessor.sendTransactionAndGetResponse(this.address, ContractConstants.AgriTraceAbi, "appendSensorWindow", input.toArgs());
  }

  public CallResponse ITEM_STATUS_CREATED() throws Exception {
    return this.txProcessor.sendCall(this.client.getCryptoSuite().getCryptoKeyPair().getAddress(), this.address, ContractConstants.AgriTraceAbi, "ITEM_STATUS_CREATED", Arrays.asList());
  }

  public CallResponse getSensorWindow(AgriTraceGetSensorWindowInputBO input) throws Exception {
    return this.txProcessor.sendCall(this.client.getCryptoSuite().getCryptoKeyPair().getAddress(), this.address, ContractConstants.AgriTraceAbi, "getSensorWindow", input.toArgs());
  }

  public CallResponse getTraceEvent(AgriTraceGetTraceEventInputBO input) throws Exception {
    return this.txProcessor.sendCall(this.client.getCryptoSuite().getCryptoKeyPair().getAddress(), this.address, ContractConstants.AgriTraceAbi, "getTraceEvent", input.toArgs());
  }

  public TransactionResponse updateNfcStatus(AgriTraceUpdateNfcStatusInputBO input) throws
      Exception {
    return this.txProcessor.sendTransactionAndGetResponse(this.address, ContractConstants.AgriTraceAbi, "updateNfcStatus", input.toArgs());
  }

  public CallResponse owner() throws Exception {
    return this.txProcessor.sendCall(this.client.getCryptoSuite().getCryptoKeyPair().getAddress(), this.address, ContractConstants.AgriTraceAbi, "owner", Arrays.asList());
  }

  public CallResponse BATCH_STATUS_ACTIVE() throws Exception {
    return this.txProcessor.sendCall(this.client.getCryptoSuite().getCryptoKeyPair().getAddress(), this.address, ContractConstants.AgriTraceAbi, "BATCH_STATUS_ACTIVE", Arrays.asList());
  }

  public CallResponse ITEM_STATUS_ACTIVE() throws Exception {
    return this.txProcessor.sendCall(this.client.getCryptoSuite().getCryptoKeyPair().getAddress(), this.address, ContractConstants.AgriTraceAbi, "ITEM_STATUS_ACTIVE", Arrays.asList());
  }

  public CallResponse nfcExists(AgriTraceNfcExistsInputBO input) throws Exception {
    return this.txProcessor.sendCall(this.client.getCryptoSuite().getCryptoKeyPair().getAddress(), this.address, ContractConstants.AgriTraceAbi, "nfcExists", input.toArgs());
  }

  public TransactionResponse registerBatch(AgriTraceRegisterBatchInputBO input) throws Exception {
    return this.txProcessor.sendTransactionAndGetResponse(this.address, ContractConstants.AgriTraceAbi, "registerBatch", input.toArgs());
  }

  public CallResponse isWriter(AgriTraceIsWriterInputBO input) throws Exception {
    return this.txProcessor.sendCall(this.client.getCryptoSuite().getCryptoKeyPair().getAddress(), this.address, ContractConstants.AgriTraceAbi, "isWriter", input.toArgs());
  }

  public CallResponse NFC_STATUS_REPLACED() throws Exception {
    return this.txProcessor.sendCall(this.client.getCryptoSuite().getCryptoKeyPair().getAddress(), this.address, ContractConstants.AgriTraceAbi, "NFC_STATUS_REPLACED", Arrays.asList());
  }

  public TransactionResponse appendTraceEvent(AgriTraceAppendTraceEventInputBO input) throws
      Exception {
    return this.txProcessor.sendTransactionAndGetResponse(this.address, ContractConstants.AgriTraceAbi, "appendTraceEvent", input.toArgs());
  }

  public CallResponse itemExists(AgriTraceItemExistsInputBO input) throws Exception {
    return this.txProcessor.sendCall(this.client.getCryptoSuite().getCryptoKeyPair().getAddress(), this.address, ContractConstants.AgriTraceAbi, "itemExists", input.toArgs());
  }

  public CallResponse getItem(AgriTraceGetItemInputBO input) throws Exception {
    return this.txProcessor.sendCall(this.client.getCryptoSuite().getCryptoKeyPair().getAddress(), this.address, ContractConstants.AgriTraceAbi, "getItem", input.toArgs());
  }

  public TransactionResponse bindNfcTag(AgriTraceBindNfcTagInputBO input) throws Exception {
    return this.txProcessor.sendTransactionAndGetResponse(this.address, ContractConstants.AgriTraceAbi, "bindNfcTag", input.toArgs());
  }

  public CallResponse ITEM_STATUS_VOID() throws Exception {
    return this.txProcessor.sendCall(this.client.getCryptoSuite().getCryptoKeyPair().getAddress(), this.address, ContractConstants.AgriTraceAbi, "ITEM_STATUS_VOID", Arrays.asList());
  }

  public CallResponse ITEM_STATUS_RECALL() throws Exception {
    return this.txProcessor.sendCall(this.client.getCryptoSuite().getCryptoKeyPair().getAddress(), this.address, ContractConstants.AgriTraceAbi, "ITEM_STATUS_RECALL", Arrays.asList());
  }

  public CallResponse BATCH_STATUS_RECALL() throws Exception {
    return this.txProcessor.sendCall(this.client.getCryptoSuite().getCryptoKeyPair().getAddress(), this.address, ContractConstants.AgriTraceAbi, "BATCH_STATUS_RECALL", Arrays.asList());
  }

  public TransactionResponse closeBatch(AgriTraceCloseBatchInputBO input) throws Exception {
    return this.txProcessor.sendTransactionAndGetResponse(this.address, ContractConstants.AgriTraceAbi, "closeBatch", input.toArgs());
  }

  public TransactionResponse bindItem(AgriTraceBindItemInputBO input) throws Exception {
    return this.txProcessor.sendTransactionAndGetResponse(this.address, ContractConstants.AgriTraceAbi, "bindItem", input.toArgs());
  }

  public CallResponse ITEM_STATUS_SOLD() throws Exception {
    return this.txProcessor.sendCall(this.client.getCryptoSuite().getCryptoKeyPair().getAddress(), this.address, ContractConstants.AgriTraceAbi, "ITEM_STATUS_SOLD", Arrays.asList());
  }

  public CallResponse batchExists(AgriTraceBatchExistsInputBO input) throws Exception {
    return this.txProcessor.sendCall(this.client.getCryptoSuite().getCryptoKeyPair().getAddress(), this.address, ContractConstants.AgriTraceAbi, "batchExists", input.toArgs());
  }

  public TransactionResponse setWriter(AgriTraceSetWriterInputBO input) throws Exception {
    return this.txProcessor.sendTransactionAndGetResponse(this.address, ContractConstants.AgriTraceAbi, "setWriter", input.toArgs());
  }

  public CallResponse getTraceEventCount(AgriTraceGetTraceEventCountInputBO input) throws
      Exception {
    return this.txProcessor.sendCall(this.client.getCryptoSuite().getCryptoKeyPair().getAddress(), this.address, ContractConstants.AgriTraceAbi, "getTraceEventCount", input.toArgs());
  }
}
