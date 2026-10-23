package org.nongchanpin.model.vo;

import java.util.List;
import lombok.Data;

@Data
public class PublicTraceView {
    private Product product;
    private Batch batch;
    private Item item;
    private Nfc nfc;
    private List<Event> timeline;
    private List<Quality> qualityReports;
    private List<Environment> environmentSummary;
    private ChainVerification chainVerification;
    private AiStatus ai;
    private java.util.Map<String,Object> productionSource;

    @Data
    public static class Product {
        private String productCode;
        private String productName;
        private String category;
        private String variety;
        private String originPlace;
        private String description;
    }

    @Data
    public static class Batch {
        private String batchCode;
        private String productionArea;
        private String plantDate;
        private String harvestDate;
        private String chainStatus;
        private String status;
    }

    @Data
    public static class Item {
        private String traceCodeHash;
        private String traceUrl;
        private String qrStatus;
        private String nfcStatus;
        private String chainStatus;
        private String status;
    }

    @Data
    public static class Nfc {
        private String uidHash;
        private String bindStatus;
        private String status;
    }

    @Data
    public static class Event {
        private String eventType;
        private String eventTime;
        private String location;
        private String operatorName;
        private String payloadHash;
        private String chainStatus;
    }

    @Data
    public static class Quality {
        private String reportNo;
        private String agencyName;
        private String result;
        private String reportHash;
        private String checkedAt;
    }

    @Data
    public static class Environment {
        private String deviceCode;
        private String metric;
        private String windowStart;
        private String windowEnd;
        private String minValue;
        private String maxValue;
        private String avgValue;
        private Integer sampleCount;
        private String dataHash;
        private String chainStatus;
    }

    @Data
    public static class ChainVerification {
        private String verificationSource;
        private boolean eventsComplete;
        private Integer chainBatchStatus;
        private Integer chainItemStatus;
        private String contractAddress;
        private String latestTxHash;
        private String latestChainStatus;
        private boolean itemPayloadHashMatched;
        private boolean batchPayloadHashMatched;
        private List<Record> records;
    }

    @Data
    public static class Record {
        private String verificationSource;
        private String chainPayloadHash;
        private String businessType;
        private String chainStatus;
        private String txHash;
        private String payloadHash;
        private boolean payloadHashMatched;
    }

    @Data
    public static class AiStatus {
        private boolean available;
        private String provider;
    }
}
