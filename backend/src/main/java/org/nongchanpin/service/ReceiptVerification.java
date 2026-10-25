package org.nongchanpin.service;

import java.math.BigInteger;
import java.util.Map;
import org.nongchanpin.model.command.ReceiptCall;
import org.nongchanpin.model.entity.ChainTransactionEntity;
import org.nongchanpin.model.vo.ChainReceiptView;

final class ReceiptVerification {
    private ReceiptVerification() {}
    static String outcome(ReceiptCall call,ChainTransactionEntity intent,ChainReceiptView view) {
        if(view==null)return "UNAVAILABLE";
        if(!view.isFound())return "NOT_FOUND";
        if(!same(call.getTxHash(),view.getTransactionHash())||!same(call.getContractAddress(),view.getContractAddress())
                ||view.getBlockNumber()==null||view.getBlockNumber().signum()<0||view.getBlockNumber().compareTo(BigInteger.valueOf(Long.MAX_VALUE))>0
                ||view.getOperator()==null||!view.getOperator().matches("0x[0-9a-fA-F]{40}")||!view.isInputMatched()
                ||intent.getChainOperator()!=null&&!intent.getChainOperator().isBlank()&&!same(intent.getChainOperator(),view.getOperator())
                ||view.getReceiptStatus()==null||view.getReceiptStatus()<0)return "CONFLICT";
        if(view.getReceiptStatus()!=0)return "FAILED_MATCHED";
        if(view.getEvents()==null||view.getEvents().size()!=1)return "EVENT_CONFLICT";
        Map<String,String> event=view.getEvents().get(0);
        if(!same(view.getOperator(),event.get("operator")))return "EVENT_CONFLICT";
        for(Map.Entry<String,String> expected:call.getEventFields().entrySet())
            if(!same(expected.getValue(),event.get(expected.getKey())))return "EVENT_CONFLICT";
        return "SUCCESS_MATCHED";
    }
    static boolean same(String a,String b){return a!=null&&b!=null&&a.equalsIgnoreCase(b);}
}
