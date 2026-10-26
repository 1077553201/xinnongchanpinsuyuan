package org.nongchanpin.model.entity;

import java.time.LocalDateTime;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnore;

public final class TransportModels {
    private TransportModels() {}
    @Data public static class Shipment {
        private Long id, dispatchId, organizationId, orderId, packingId, warehouseId;
        private String shipmentCode, carrier, trackingReference, driverName, driverContact, recipient, deliveryAddress, notes, status;
        private String orderCode, productName, variety, packingCode, warehouseName, packageUnit, movementCode;
        private Integer packageCount;
        private LocalDateTime dispatchedAt, recordedAt, correctionRecordedAt;
        private Long recordedBy;
        private String recorderName;
        @JsonIgnore private String requestKey;
        @JsonIgnore private String requestHash;
    }
    @Data public static class Event {
        private Long id, shipmentId, recordedBy;
        private String kind, location, performerName, description, reference, recorderName;
        private LocalDateTime occurredAt, recordedAt;
        private Integer acceptedCount, rejectedCount;
        @JsonIgnore private String requestKey;
        @JsonIgnore private String requestHash;
    }
}
