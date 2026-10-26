package org.nongchanpin.service;

import org.nongchanpin.mapper.InventoryMapper;
import org.nongchanpin.mapper.LossCorrectionMapper;
import org.springframework.stereotype.Service;

/** Retains the original loss-only endpoint and immutable V1 records. */
@Service
public class LossCorrectionService extends InventoryCorrectionService {
    public LossCorrectionService(InventoryMapper inventory,LossCorrectionMapper corrections,PayloadService payloads,AuditService audit){super(inventory,corrections,payloads,audit);}
}
