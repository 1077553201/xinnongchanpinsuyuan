package org.nongchanpin.service;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigInteger;
import java.util.*;
import org.junit.jupiter.api.*;
import org.nongchanpin.model.command.ReceiptCall;
import org.nongchanpin.model.entity.ChainTransactionEntity;
import org.nongchanpin.model.vo.ChainReceiptView;

class ReceiptVerificationTest {
    private final ReceiptCall call=new ReceiptCall();private final ChainTransactionEntity intent=new ChainTransactionEntity();private final ChainReceiptView receipt=new ChainReceiptView();
    @BeforeEach void setup(){call.setTxHash("0x"+"a".repeat(64));call.setContractAddress("0x"+"1".repeat(40));call.getEventFields().put("payloadHash","0x"+"b".repeat(64));receipt.setFound(true);receipt.setTransactionHash(call.getTxHash());receipt.setContractAddress(call.getContractAddress());receipt.setOperator("0x"+"2".repeat(40));receipt.setBlockNumber(BigInteger.TEN);receipt.setInputMatched(true);receipt.setReceiptStatus(0);receipt.getEvents().add(new HashMap<>(Map.of("operator",receipt.getOperator(),"payloadHash",call.getEventFields().get("payloadHash"))));}
    private String outcome(){return ReceiptVerification.outcome(call,intent,receipt);}
    @Test void fullyMatchingSuccess(){assertEquals("SUCCESS_MATCHED",outcome());}
    @Test void nullReadIsUnavailable(){assertEquals("UNAVAILABLE",ReceiptVerification.outcome(call,intent,null));}
    @Test void absentReceiptIsNotFailure(){receipt.setFound(false);assertEquals("NOT_FOUND",outcome());}
    @Test void wrongHashIsConflict(){receipt.setTransactionHash("0x"+"f".repeat(64));assertEquals("CONFLICT",outcome());}
    @Test void wrongContractIsConflict(){receipt.setContractAddress("0x"+"f".repeat(40));assertEquals("CONFLICT",outcome());}
    @Test void matchingEventCannotHideWrongCall(){receipt.setInputMatched(false);assertEquals("CONFLICT",outcome());}
    @Test void successWithoutEventIsNotConfirmed(){receipt.getEvents().clear();assertEquals("EVENT_CONFLICT",outcome());}
    @Test void duplicateEventsAreNotAccepted(){receipt.getEvents().add(receipt.getEvents().get(0));assertEquals("EVENT_CONFLICT",outcome());}
    @Test void anotherEventSenderIsNotAccepted(){receipt.getEvents().get(0).put("operator","0x"+"3".repeat(40));assertEquals("EVENT_CONFLICT",outcome());}
    @Test void wrongOriginalSenderIsNotAccepted(){intent.setChainOperator("0x"+"3".repeat(40));assertEquals("CONFLICT",outcome());}
    @Test void wrongPayloadIsNotAccepted(){receipt.getEvents().get(0).put("payloadHash","0x"+"f".repeat(64));assertEquals("EVENT_CONFLICT",outcome());}
    @Test void negativeOrOversizedBlockIsNotAccepted(){receipt.setBlockNumber(BigInteger.valueOf(-1));assertEquals("CONFLICT",outcome());receipt.setBlockNumber(BigInteger.ONE.shiftLeft(65));assertEquals("CONFLICT",outcome());}
    @Test void knownFailureNeedsMatchingInputButNoSuccessEvent(){receipt.setReceiptStatus(16);receipt.getEvents().clear();assertEquals("FAILED_MATCHED",outcome());receipt.setInputMatched(false);assertEquals("CONFLICT",outcome());}
    @Test void unknownReceiptStatusIsNotFailure(){receipt.setReceiptStatus(null);assertEquals("CONFLICT",outcome());}
}
