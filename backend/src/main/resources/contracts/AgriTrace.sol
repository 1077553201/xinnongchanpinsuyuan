// SPDX-License-Identifier: MIT
pragma solidity ^0.8.0;

/**
 * @title AgriTrace
 * @notice Agricultural product traceability evidence contract for FISCO BCOS 3.x.
 *         The contract stores only hashes, indexes, status codes, timestamps, and
 *         chain operators. Full business payloads stay in the off-chain database
 *         or object storage.
 */
contract AgriTrace {
    uint8 public constant BATCH_STATUS_ACTIVE = 1;
    uint8 public constant BATCH_STATUS_CLOSED = 2;
    uint8 public constant BATCH_STATUS_RECALL = 3;

    uint8 public constant ITEM_STATUS_CREATED = 1;
    uint8 public constant ITEM_STATUS_ACTIVE = 2;
    uint8 public constant ITEM_STATUS_SOLD = 3;
    uint8 public constant ITEM_STATUS_RECALL = 4;
    uint8 public constant ITEM_STATUS_VOID = 5;

    uint8 public constant NFC_STATUS_ACTIVE = 1;
    uint8 public constant NFC_STATUS_REPLACED = 2;
    uint8 public constant NFC_STATUS_DISABLED = 3;

    address public owner;

    struct Batch {
        bool exists;
        bytes32 productIdHash;
        bytes32 payloadHash;
        uint8 status;
        uint256 startedAt;
        uint256 closedAt;
        bytes32 closeReasonHash;
        address operator;
    }

    struct Item {
        bool exists;
        bytes32 batchIdHash;
        bytes32 payloadHash;
        uint8 status;
        bytes32 nfcUidHash;
        uint256 createdAt;
        uint256 updatedAt;
        bytes32 statusReasonHash;
        address operator;
    }

    struct NfcTag {
        bool exists;
        bytes32 itemCodeHash;
        bytes32 payloadHash;
        uint8 status;
        bytes32 reasonHash;
        uint256 boundAt;
        uint256 updatedAt;
        address operator;
    }

    struct TraceRecord {
        bytes32 eventIdHash;
        uint8 eventType;
        bytes32 payloadHash;
        uint256 occurredAt;
        uint256 recordedAt;
        address operator;
    }

    struct SensorWindow {
        bytes32 windowIdHash;
        bytes32 deviceCodeHash;
        bytes32 payloadHash;
        uint256 windowStart;
        uint256 windowEnd;
        uint256 recordedAt;
        address operator;
    }

    mapping(address => bool) private writers;
    mapping(bytes32 => Batch) private batches;
    mapping(bytes32 => Item) private items;
    mapping(bytes32 => NfcTag) private nfcTags;
    mapping(bytes32 => bool) private usedTraceEventIds;
    mapping(bytes32 => bool) private usedSensorWindowIds;
    mapping(bytes32 => TraceRecord[]) private traceRecordsByItem;
    mapping(bytes32 => SensorWindow[]) private sensorWindowsByBatch;

    event WriterUpdated(address indexed writer, bool allowed, address indexed operator);
    event BatchRegistered(bytes32 indexed batchIdHash, bytes32 indexed productIdHash, bytes32 payloadHash, uint256 startedAt, address indexed operator);
    event BatchClosed(bytes32 indexed batchIdHash, uint8 status, bytes32 reasonHash, address indexed operator);
    event ItemBound(bytes32 indexed itemCodeHash, bytes32 indexed batchIdHash, bytes32 payloadHash, address indexed operator);
    event NfcTagBound(bytes32 indexed itemCodeHash, bytes32 indexed nfcUidHash, bytes32 payloadHash, address operator);
    event NfcStatusUpdated(bytes32 indexed nfcUidHash, uint8 status, bytes32 reasonHash, address indexed operator);
    event TraceEventAppended(bytes32 indexed itemCodeHash, bytes32 indexed eventIdHash, uint8 eventType, bytes32 payloadHash, uint256 occurredAt, address operator);
    event SensorWindowAppended(bytes32 indexed batchIdHash, bytes32 indexed windowIdHash, bytes32 indexed deviceCodeHash, bytes32 payloadHash, uint256 windowStart, uint256 windowEnd, address operator);
    event ItemStatusUpdated(bytes32 indexed itemCodeHash, uint8 status, bytes32 reasonHash, address indexed operator);

    modifier onlyOwner() {
        require(msg.sender == owner, "AgriTrace: caller is not owner");
        _;
    }

    modifier onlyWriter() {
        require(msg.sender == owner || writers[msg.sender], "AgriTrace: caller is not writer");
        _;
    }

    constructor() {
        owner = msg.sender;
        writers[msg.sender] = true;
        emit WriterUpdated(msg.sender, true, msg.sender);
    }

    function setWriter(address writer, bool allowed) external onlyOwner {
        require(writer != address(0), "AgriTrace: writer is zero address");
        writers[writer] = allowed;
        emit WriterUpdated(writer, allowed, msg.sender);
    }

    function registerBatch(
        bytes32 batchIdHash,
        bytes32 productIdHash,
        bytes32 payloadHash,
        uint256 startedAt
    ) external onlyWriter {
        require(batchIdHash != bytes32(0), "AgriTrace: empty batchIdHash");
        require(productIdHash != bytes32(0), "AgriTrace: empty productIdHash");
        require(payloadHash != bytes32(0), "AgriTrace: empty payloadHash");
        require(startedAt > 0, "AgriTrace: empty startedAt");
        require(!batches[batchIdHash].exists, "AgriTrace: batch already exists");

        batches[batchIdHash] = Batch({
            exists: true,
            productIdHash: productIdHash,
            payloadHash: payloadHash,
            status: BATCH_STATUS_ACTIVE,
            startedAt: startedAt,
            closedAt: 0,
            closeReasonHash: bytes32(0),
            operator: msg.sender
        });

        emit BatchRegistered(batchIdHash, productIdHash, payloadHash, startedAt, msg.sender);
    }

    function closeBatch(
        bytes32 batchIdHash,
        uint8 status,
        bytes32 reasonHash
    ) external onlyWriter {
        require(batches[batchIdHash].exists, "AgriTrace: batch not found");
        require(status == BATCH_STATUS_CLOSED || status == BATCH_STATUS_RECALL, "AgriTrace: invalid batch status");
        require(batches[batchIdHash].status == BATCH_STATUS_ACTIVE, "AgriTrace: batch not active");

        Batch storage batch = batches[batchIdHash];
        batch.status = status;
        batch.closedAt = block.timestamp;
        batch.closeReasonHash = reasonHash;
        batch.operator = msg.sender;

        emit BatchClosed(batchIdHash, status, reasonHash, msg.sender);
    }

    function bindItem(
        bytes32 itemCodeHash,
        bytes32 batchIdHash,
        bytes32 payloadHash
    ) external onlyWriter {
        require(itemCodeHash != bytes32(0), "AgriTrace: empty itemCodeHash");
        require(payloadHash != bytes32(0), "AgriTrace: empty payloadHash");
        require(batches[batchIdHash].exists, "AgriTrace: batch not found");
        require(!items[itemCodeHash].exists, "AgriTrace: item already exists");

        items[itemCodeHash] = Item({
            exists: true,
            batchIdHash: batchIdHash,
            payloadHash: payloadHash,
            status: ITEM_STATUS_CREATED,
            nfcUidHash: bytes32(0),
            createdAt: block.timestamp,
            updatedAt: block.timestamp,
            statusReasonHash: bytes32(0),
            operator: msg.sender
        });

        emit ItemBound(itemCodeHash, batchIdHash, payloadHash, msg.sender);
    }

    function bindNfcTag(
        bytes32 itemCodeHash,
        bytes32 nfcUidHash,
        bytes32 payloadHash
    ) external onlyWriter {
        require(nfcUidHash != bytes32(0), "AgriTrace: empty nfcUidHash");
        require(payloadHash != bytes32(0), "AgriTrace: empty payloadHash");
        require(items[itemCodeHash].exists, "AgriTrace: item not found");
        require(items[itemCodeHash].nfcUidHash == bytes32(0), "AgriTrace: item already has nfc");
        require(!nfcTags[nfcUidHash].exists, "AgriTrace: nfc already exists");

        items[itemCodeHash].nfcUidHash = nfcUidHash;
        items[itemCodeHash].updatedAt = block.timestamp;
        items[itemCodeHash].operator = msg.sender;

        nfcTags[nfcUidHash] = NfcTag({
            exists: true,
            itemCodeHash: itemCodeHash,
            payloadHash: payloadHash,
            status: NFC_STATUS_ACTIVE,
            reasonHash: bytes32(0),
            boundAt: block.timestamp,
            updatedAt: block.timestamp,
            operator: msg.sender
        });

        emit NfcTagBound(itemCodeHash, nfcUidHash, payloadHash, msg.sender);
    }

    function updateNfcStatus(
        bytes32 nfcUidHash,
        uint8 status,
        bytes32 reasonHash
    ) external onlyWriter {
        require(nfcTags[nfcUidHash].exists, "AgriTrace: nfc not found");
        require(_isValidNfcStatus(status), "AgriTrace: invalid nfc status");

        NfcTag storage tag = nfcTags[nfcUidHash];
        tag.status = status;
        tag.reasonHash = reasonHash;
        tag.updatedAt = block.timestamp;
        tag.operator = msg.sender;

        emit NfcStatusUpdated(nfcUidHash, status, reasonHash, msg.sender);
    }

    function appendTraceEvent(
        bytes32 itemCodeHash,
        bytes32 eventIdHash,
        uint8 eventType,
        bytes32 payloadHash,
        uint256 occurredAt
    ) external onlyWriter {
        require(items[itemCodeHash].exists, "AgriTrace: item not found");
        require(eventIdHash != bytes32(0), "AgriTrace: empty eventIdHash");
        require(payloadHash != bytes32(0), "AgriTrace: empty payloadHash");
        require(occurredAt > 0, "AgriTrace: empty occurredAt");
        require(_isValidEventType(eventType), "AgriTrace: invalid event type");
        require(!usedTraceEventIds[eventIdHash], "AgriTrace: trace event already exists");

        usedTraceEventIds[eventIdHash] = true;
        traceRecordsByItem[itemCodeHash].push(TraceRecord({
            eventIdHash: eventIdHash,
            eventType: eventType,
            payloadHash: payloadHash,
            occurredAt: occurredAt,
            recordedAt: block.timestamp,
            operator: msg.sender
        }));

        emit TraceEventAppended(itemCodeHash, eventIdHash, eventType, payloadHash, occurredAt, msg.sender);
    }

    function appendSensorWindow(
        bytes32 batchIdHash,
        bytes32 windowIdHash,
        bytes32 deviceCodeHash,
        bytes32 payloadHash,
        uint256 windowStart,
        uint256 windowEnd
    ) external onlyWriter {
        require(batches[batchIdHash].exists, "AgriTrace: batch not found");
        require(windowIdHash != bytes32(0), "AgriTrace: empty windowIdHash");
        require(deviceCodeHash != bytes32(0), "AgriTrace: empty deviceCodeHash");
        require(payloadHash != bytes32(0), "AgriTrace: empty payloadHash");
        require(windowStart > 0, "AgriTrace: empty windowStart");
        require(windowEnd >= windowStart, "AgriTrace: invalid window range");
        require(!usedSensorWindowIds[windowIdHash], "AgriTrace: sensor window already exists");

        usedSensorWindowIds[windowIdHash] = true;
        sensorWindowsByBatch[batchIdHash].push(SensorWindow({
            windowIdHash: windowIdHash,
            deviceCodeHash: deviceCodeHash,
            payloadHash: payloadHash,
            windowStart: windowStart,
            windowEnd: windowEnd,
            recordedAt: block.timestamp,
            operator: msg.sender
        }));

        emit SensorWindowAppended(batchIdHash, windowIdHash, deviceCodeHash, payloadHash, windowStart, windowEnd, msg.sender);
    }

    function updateItemStatus(
        bytes32 itemCodeHash,
        uint8 status,
        bytes32 reasonHash
    ) external onlyWriter {
        require(items[itemCodeHash].exists, "AgriTrace: item not found");
        require(_isValidItemStatus(status), "AgriTrace: invalid item status");

        Item storage item = items[itemCodeHash];
        item.status = status;
        item.statusReasonHash = reasonHash;
        item.updatedAt = block.timestamp;
        item.operator = msg.sender;

        emit ItemStatusUpdated(itemCodeHash, status, reasonHash, msg.sender);
    }

    function isWriter(address account) external view returns (bool) {
        return account == owner || writers[account];
    }

    function batchExists(bytes32 batchIdHash) external view returns (bool) {
        return batches[batchIdHash].exists;
    }

    function itemExists(bytes32 itemCodeHash) external view returns (bool) {
        return items[itemCodeHash].exists;
    }

    function nfcExists(bytes32 nfcUidHash) external view returns (bool) {
        return nfcTags[nfcUidHash].exists;
    }

    function getBatch(bytes32 batchIdHash) external view returns (
        bool exists,
        bytes32 productIdHash,
        bytes32 payloadHash,
        uint8 status,
        uint256 startedAt,
        uint256 closedAt,
        bytes32 closeReasonHash,
        address operator
    ) {
        Batch storage batch = batches[batchIdHash];
        return (
            batch.exists,
            batch.productIdHash,
            batch.payloadHash,
            batch.status,
            batch.startedAt,
            batch.closedAt,
            batch.closeReasonHash,
            batch.operator
        );
    }

    function getItem(bytes32 itemCodeHash) external view returns (
        bool exists,
        bytes32 batchIdHash,
        bytes32 payloadHash,
        uint8 status,
        bytes32 nfcUidHash,
        uint256 createdAt,
        uint256 updatedAt,
        bytes32 statusReasonHash,
        address operator
    ) {
        Item storage item = items[itemCodeHash];
        return (
            item.exists,
            item.batchIdHash,
            item.payloadHash,
            item.status,
            item.nfcUidHash,
            item.createdAt,
            item.updatedAt,
            item.statusReasonHash,
            item.operator
        );
    }

    function getNfcTag(bytes32 nfcUidHash) external view returns (
        bool exists,
        bytes32 itemCodeHash,
        bytes32 payloadHash,
        uint8 status,
        bytes32 reasonHash,
        uint256 boundAt,
        uint256 updatedAt,
        address operator
    ) {
        NfcTag storage tag = nfcTags[nfcUidHash];
        return (
            tag.exists,
            tag.itemCodeHash,
            tag.payloadHash,
            tag.status,
            tag.reasonHash,
            tag.boundAt,
            tag.updatedAt,
            tag.operator
        );
    }

    function getTraceEventCount(bytes32 itemCodeHash) external view returns (uint256) {
        return traceRecordsByItem[itemCodeHash].length;
    }

    function getTraceEvent(bytes32 itemCodeHash, uint256 index) external view returns (
        bytes32 eventIdHash,
        uint8 eventType,
        bytes32 payloadHash,
        uint256 occurredAt,
        uint256 recordedAt,
        address operator
    ) {
        require(index < traceRecordsByItem[itemCodeHash].length, "AgriTrace: trace event index out of bounds");
        TraceRecord storage record = traceRecordsByItem[itemCodeHash][index];
        return (
            record.eventIdHash,
            record.eventType,
            record.payloadHash,
            record.occurredAt,
            record.recordedAt,
            record.operator
        );
    }

    function getSensorWindowCount(bytes32 batchIdHash) external view returns (uint256) {
        return sensorWindowsByBatch[batchIdHash].length;
    }

    function getSensorWindow(bytes32 batchIdHash, uint256 index) external view returns (
        bytes32 windowIdHash,
        bytes32 deviceCodeHash,
        bytes32 payloadHash,
        uint256 windowStart,
        uint256 windowEnd,
        uint256 recordedAt,
        address operator
    ) {
        require(index < sensorWindowsByBatch[batchIdHash].length, "AgriTrace: sensor window index out of bounds");
        SensorWindow storage window = sensorWindowsByBatch[batchIdHash][index];
        return (
            window.windowIdHash,
            window.deviceCodeHash,
            window.payloadHash,
            window.windowStart,
            window.windowEnd,
            window.recordedAt,
            window.operator
        );
    }

    function _isValidItemStatus(uint8 status) private pure returns (bool) {
        return status >= ITEM_STATUS_CREATED && status <= ITEM_STATUS_VOID;
    }

    function _isValidNfcStatus(uint8 status) private pure returns (bool) {
        return status >= NFC_STATUS_ACTIVE && status <= NFC_STATUS_DISABLED;
    }

    function _isValidEventType(uint8 eventType) private pure returns (bool) {
        return eventType >= 1 && eventType <= 11;
    }
}
