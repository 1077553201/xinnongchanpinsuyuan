package org.nongchanpin.chain;

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

public interface ChainService {
    org.nongchanpin.model.vo.ChainReceiptView inspectReceipt(org.nongchanpin.model.command.ReceiptCall call);
    ChainHealthResult health();

    ChainTxResult setWriter(String writerAddress, boolean allowed);

    ChainTxResult registerBatch(RegisterBatchCommand command);

    ChainTxResult closeBatch(CloseBatchCommand command);

    ChainTxResult bindItem(BindItemCommand command);

    ChainTxResult bindNfcTag(BindNfcTagCommand command);

    ChainTxResult updateNfcStatus(UpdateNfcStatusCommand command);

    ChainTxResult appendTraceEvent(AppendTraceEventCommand command);

    ChainTxResult appendSensorWindow(AppendSensorWindowCommand command);

    ChainTxResult updateItemStatus(UpdateItemStatusCommand command);

    ChainBatchView getBatch(String batchIdOrHash);

    ChainItemView getItem(String itemCodeOrHash);

    ChainNfcView getNfcTag(String nfcUidOrHash);

    ChainTraceEventView getTraceEvent(String itemCodeOrHash, long index);

    ChainSensorWindowView getSensorWindow(String batchIdOrHash, long index);
}
