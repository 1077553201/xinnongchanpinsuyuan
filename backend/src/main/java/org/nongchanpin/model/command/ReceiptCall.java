package org.nongchanpin.model.command;

import java.util.*;
import lombok.Data;
import lombok.Value;

/** Server-built expectations only; never accepted from a public request. */
@Data
public class ReceiptCall {
    private String txHash,contractAddress,method,eventName;
    private List<Argument> arguments=new ArrayList<>();
    private Map<String,String> eventFields=new LinkedHashMap<>();
    @Value public static class Argument { String type,value; }
    public ReceiptCall bytes(String value){arguments.add(new Argument("bytes32",value));return this;}
    public ReceiptCall uint(String type,Object value){arguments.add(new Argument(type,value.toString()));return this;}
}
