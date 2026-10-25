package org.nongchanpin.chain;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.fisco.bcos.sdk.v3.client.Client;
import org.fisco.bcos.sdk.v3.client.protocol.response.BcosTransactionReceipt;
import org.fisco.bcos.sdk.v3.crypto.CryptoSuite;
import org.fisco.bcos.sdk.v3.codec.abi.FunctionEncoder;
import org.fisco.bcos.sdk.v3.codec.datatypes.Function;
import org.fisco.bcos.sdk.v3.codec.datatypes.generated.Bytes32;
import org.fisco.bcos.sdk.v3.model.TransactionReceipt;
import org.nongchanpin.config.*;
import org.nongchanpin.model.command.ReceiptCall;
import org.nongchanpin.model.vo.ChainReceiptView;
import org.nongchanpin.util.Bytes32Utils;

class ReceiptReadAdapterTest {
    private final Client client=mock(Client.class);
    private final CryptoSuite crypto=new CryptoSuite(0);
    private final AgriTraceChainService service=new AgriTraceChainService(client,new BcosConfig(),new SystemConfig(),new ContractConfig());
    private final ReceiptCall call=new ReceiptCall();
    private final TransactionReceipt receipt=new TransactionReceipt();
    private final BcosTransactionReceipt response=new BcosTransactionReceipt();
    private final String item="0x"+"a".repeat(64),uid="0x"+"b".repeat(64),payload="0x"+"c".repeat(64),operator="0x"+"2".repeat(40);
    @BeforeEach void setup(){
        when(client.getCryptoSuite()).thenReturn(crypto);
        call.setTxHash("0x"+"d".repeat(64));call.setContractAddress("0x"+"1".repeat(40));call.setMethod("bindNfcTag");call.setEventName("NfcTagBound");call.bytes(item).bytes(uid).bytes(payload);
        receipt.setTransactionHash(call.getTxHash());receipt.setTo(call.getContractAddress());receipt.setFrom(operator);receipt.setBlockNumber("0x10");receipt.setStatus(0);
        receipt.setInput("0x"+hex(new FunctionEncoder(crypto).encode(new Function("bindNfcTag",List.of(new Bytes32(Bytes32Utils.fromHex(item)),new Bytes32(Bytes32Utils.fromHex(uid)),new Bytes32(Bytes32Utils.fromHex(payload))),Collections.emptyList()))));
        TransactionReceipt.Logs log=new TransactionReceipt.Logs();log.setAddress(call.getContractAddress());String signature=crypto.hash("NfcTagBound(bytes32,bytes32,bytes32,address)");log.setTopics(List.of(signature.startsWith("0x")?signature:"0x"+signature,item,uid));log.setData(payload+"0".repeat(24)+operator.substring(2));receipt.setLogEntries(new ArrayList<>(List.of(log)));
        response.setResult(receipt);when(client.getTransactionReceipt(call.getTxHash(),false)).thenReturn(response);
    }
    private String hex(byte[] bytes){StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(String.format("%02x",b&255));return s.toString();}
    @Test void readsActualAbiInputAndDecodesEventWithoutTransactionSend(){ChainReceiptView value=service.inspectReceipt(call);assertTrue(value.isFound());assertTrue(value.isInputMatched());assertEquals(16,value.getBlockNumber().intValue());assertEquals(1,value.getEvents().size());assertEquals(uid,value.getEvents().get(0).get("nfcUidHash"));assertEquals(operator,value.getEvents().get(0).get("operator"));verify(client).getTransactionReceipt(call.getTxHash(),false);}
    @Test void emptyRpcResultMeansNotFound(){response.setResult(null);assertFalse(service.inspectReceipt(call).isFound());}
    @Test void nullRpcResponseIsUnavailable(){when(client.getTransactionReceipt(anyString(),anyBoolean())).thenReturn(null);assertThrows(IllegalStateException.class,()->service.inspectReceipt(call));}
    @Test void foreignContractLogsDoNotCountAsEvidence(){TransactionReceipt.Logs foreign=new TransactionReceipt.Logs();foreign.setAddress("0x"+"f".repeat(40));foreign.setTopics(receipt.getLogEntries().get(0).getTopics());foreign.setData(receipt.getLogEntries().get(0).getData());receipt.getLogEntries().add(foreign);assertEquals(1,service.inspectReceipt(call).getEvents().size());}
    @Test void matchingEventDoesNotHideDifferentFunctionInput(){receipt.setInput("0x00000000"+receipt.getInput().substring(10));assertFalse(service.inspectReceipt(call).isInputMatched());}
    @Test void failureStillVerifiesOriginalInput(){receipt.setStatus(16);ChainReceiptView value=service.inspectReceipt(call);assertTrue(value.isInputMatched());assertTrue(value.getEvents().isEmpty());assertEquals(16,value.getReceiptStatus());}
    @Test void nodeHexValuesWithoutPrefixesKeepStrictIdentityMatching(){receipt.setTo(receipt.getTo().substring(2));receipt.setFrom(receipt.getFrom().substring(2));receipt.setTransactionHash(receipt.getTransactionHash().substring(2));TransactionReceipt.Logs log=receipt.getLogEntries().get(0);log.setAddress(log.getAddress().substring(2));log.setTopics(log.getTopics().stream().map(t->t.substring(2)).collect(java.util.stream.Collectors.toList()));log.setData(log.getData().substring(2));ChainReceiptView result=service.inspectReceipt(call);assertEquals(call.getContractAddress(),result.getContractAddress());assertEquals(operator,result.getOperator());assertEquals(1,result.getEvents().size());assertEquals(uid,result.getEvents().get(0).get("nfcUidHash"));}
}
