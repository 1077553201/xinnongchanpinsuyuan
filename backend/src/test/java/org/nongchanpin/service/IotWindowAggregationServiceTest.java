package org.nongchanpin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.nongchanpin.chain.ChainService;
import org.nongchanpin.chain.ChainStatus;
import org.nongchanpin.config.ContractConfig;
import org.nongchanpin.config.IotProperties;
import org.nongchanpin.mapper.IotDataWindowMapper;
import org.nongchanpin.mapper.IotSensorReadingMapper;
import org.nongchanpin.mapper.ProductBatchMapper;
import org.nongchanpin.model.command.AppendSensorWindowCommand;
import org.nongchanpin.model.entity.ChainTransactionEntity;
import org.nongchanpin.model.entity.IotDataWindowEntity;
import org.nongchanpin.model.entity.IotSensorReadingEntity;
import org.nongchanpin.model.entity.ProductBatchEntity;
import org.nongchanpin.model.vo.ChainTxResult;

class IotWindowAggregationServiceTest {
    private final IotSensorReadingMapper readingMapper = mock(IotSensorReadingMapper.class);
    private final IotDataWindowMapper windowMapper = mock(IotDataWindowMapper.class);
    private final ProductBatchMapper batchMapper = mock(ProductBatchMapper.class);
    private final ChainService chainService = mock(ChainService.class);
    private final ChainTransactionService txService = mock(ChainTransactionService.class);
    private final IotProperties iotProperties = new IotProperties();

    @Test
    void manualAggregationRejectsMixedBatchesBeforeAnchoring() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 20, 10, 0);
        when(readingMapper.findInWindow("DEV1", "temperature", start, start.plusMinutes(5))).thenReturn(List.of(
                reading("20", start.plusMinutes(1), 7L), reading("80", start.plusMinutes(2), 8L)));
        assertThrows(IllegalArgumentException.class, () -> service().aggregate("DEV1", "temperature", 1L, "GW1", start, start.plusMinutes(5)));
        verifyNoInteractions(windowMapper, chainService, txService);
    }

    @Test
    void automaticWindowNeverMixesReadingsFromAnotherBatch() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 20, 10, 0);
        IotSensorReadingEntity current = reading("20", start.plusMinutes(1), 7L);
        when(readingMapper.findInWindow("DEV1", "temperature", start, start.plusMinutes(5))).thenReturn(List.of(current,
                reading("80", start.plusMinutes(2), 8L)));
        IotDataWindowEntity[] saved = new IotDataWindowEntity[1];
        when(windowMapper.findByDataHash(any())).thenAnswer(invocation -> saved[0]);
        doAnswer(invocation -> { saved[0] = invocation.getArgument(0); saved[0].setId(1L); return 1; }).when(windowMapper).insert(any());
        // 未配置批次链记录时可降级，但窗口统计仍必须隔离。
        IotDataWindowEntity result = service().aggregateForReading(current);
        assertEquals(7L, result.getBatchId());
        assertEquals(1, result.getSampleCount());
        assertEquals(new BigDecimal("20.000000"), result.getAvgValue());
    }

    @Test
    void aggregateComputesStatsAndAnchorsWhenBatchBound() {
        LocalDateTime start = LocalDateTime.of(2026, 9, 29, 10, 0);
        LocalDateTime end = start.plusMinutes(5);
        when(readingMapper.findInWindow("DEV1", "temperature", start, end)).thenReturn(List.of(
                reading("20.000000", start.plusMinutes(1), 7L),
                reading("30.000000", start.plusMinutes(2), 7L),
                reading("25.000000", start.plusMinutes(3), 7L)));

        IotDataWindowEntity[] saved = new IotDataWindowEntity[1];
        when(windowMapper.findByDataHash(any())).thenAnswer(invocation -> saved[0]);
        doAnswer(invocation -> {
            IotDataWindowEntity window = invocation.getArgument(0);
            window.setId(11L);
            saved[0] = window;
            return 1;
        }).when(windowMapper).insert(any(IotDataWindowEntity.class));

        ProductBatchEntity batch = new ProductBatchEntity();
        batch.setId(7L);
        batch.setBatchCode("B001");
        when(batchMapper.findById(7L)).thenReturn(batch);
        ChainTransactionEntity pending = new ChainTransactionEntity();
        pending.setId(99L);
        when(txService.createPending(any(), any(), any(), any(), any(), any(), any())).thenReturn(pending);
        ChainTxResult chainResult = new ChainTxResult();
        chainResult.setChainStatus(ChainStatus.CONFIRMED);
        when(chainService.appendSensorWindow(any(AppendSensorWindowCommand.class))).thenReturn(chainResult);

        IotDataWindowEntity window = service().aggregate("DEV1", "temperature", 1L, "GW1", start, end);

        assertEquals(new BigDecimal("20.000000"), window.getMinValue());
        assertEquals(new BigDecimal("30.000000"), window.getMaxValue());
        assertEquals(new BigDecimal("25.000000"), window.getAvgValue());
        assertEquals(3, window.getSampleCount());
        assertEquals(7L, window.getBatchId());
        verify(chainService).appendSensorWindow(any(AppendSensorWindowCommand.class));
        verify(windowMapper).updateChainStatus(11L, "CONFIRMED");
    }

    @Test
    void existingWindowByHashIsReusedWithoutChainCall() {
        LocalDateTime start = LocalDateTime.of(2026, 9, 29, 10, 0);
        LocalDateTime end = start.plusMinutes(5);
        when(readingMapper.findInWindow("DEV1", "temperature", start, end))
                .thenReturn(List.of(reading("20.000000", start.plusMinutes(1), 7L)));
        IotDataWindowEntity existing = new IotDataWindowEntity();
        existing.setId(33L);
        when(windowMapper.findByDataHash(any())).thenReturn(existing);

        IotDataWindowEntity window = service().aggregate("DEV1", "temperature", 1L, "GW1", start, end);

        assertEquals(33L, window.getId());
        verify(windowMapper, never()).insert(any(IotDataWindowEntity.class));
        verify(chainService, never()).appendSensorWindow(any(AppendSensorWindowCommand.class));
    }

    @Test
    void windowWithoutBatchIsMarkedChainDisabled() {
        LocalDateTime start = LocalDateTime.of(2026, 9, 29, 10, 0);
        LocalDateTime end = start.plusMinutes(5);
        when(readingMapper.findInWindow("DEV1", "temperature", start, end))
                .thenReturn(List.of(reading("20.000000", start.plusMinutes(1), null)));
        IotDataWindowEntity[] saved = new IotDataWindowEntity[1];
        when(windowMapper.findByDataHash(any())).thenAnswer(invocation -> saved[0]);
        doAnswer(invocation -> {
            IotDataWindowEntity window = invocation.getArgument(0);
            window.setId(44L);
            saved[0] = window;
            return 1;
        }).when(windowMapper).insert(any(IotDataWindowEntity.class));

        service().aggregate("DEV1", "temperature", 1L, "GW1", start, end);

        verify(chainService, never()).appendSensorWindow(any(AppendSensorWindowCommand.class));
        verify(windowMapper).updateChainStatus(44L, ChainStatus.CHAIN_DISABLED.name());
    }

    @Test
    void aggregateForReadingUsesConfiguredBucket() {
        LocalDateTime reportedAt = LocalDateTime.of(2026, 9, 29, 10, 7, 30);
        iotProperties.setWindowMinutes(5);
        when(readingMapper.findInWindow(eq("DEV1"), eq("temperature"),
                eq(LocalDateTime.of(2026, 9, 29, 10, 5)), eq(LocalDateTime.of(2026, 9, 29, 10, 10))))
                .thenReturn(List.of(reading("20.000000", reportedAt, 7L)));
        when(windowMapper.findByDataHash(any())).thenReturn(new IotDataWindowEntity());

        service().aggregateForReading(reading("20.000000", reportedAt, 7L));

        verify(readingMapper).findInWindow("DEV1", "temperature", LocalDateTime.of(2026, 9, 29, 10, 5),
                LocalDateTime.of(2026, 9, 29, 10, 10));
    }

    private IotWindowAggregationService service() {
        ContractConfig contractConfig = new ContractConfig();
        contractConfig.setAgriTraceAddress("0xcontract");
        return new IotWindowAggregationService(readingMapper, windowMapper, batchMapper, chainService, txService,
                new ChainWriteSupport(txService), new PayloadService(), iotProperties, contractConfig,
                mock(org.nongchanpin.mapper.MonitoringMapper.class));
    }

    @Test void manualWindowRejectsSameDeviceAcrossPlantingBindings() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 20, 10, 0);
        IotSensorReadingEntity first = reading("20", start.plusSeconds(1), null);
        first.setCycleId(10L); first.setBindingId(100L);
        IotSensorReadingEntity second = reading("80", start.plusSeconds(2), null);
        second.setCycleId(11L); second.setBindingId(101L);
        when(readingMapper.findInWindow("DEV1", "temperature", start, start.plusMinutes(5))).thenReturn(List.of(first, second));
        assertThrows(IllegalArgumentException.class, () -> service().aggregate("DEV1", "temperature", 1L, "GW1", start, start.plusMinutes(5)));
        verifyNoInteractions(windowMapper, chainService);
    }

    @Test void automaticWindowExcludesPreviousPlantingWithinSameTimeBucket() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 20, 10, 0);
        IotSensorReadingEntity first = reading("20", start.plusSeconds(1), null);
        first.setCycleId(10L); first.setBindingId(100L); first.setPlotId(3L);
        IotSensorReadingEntity second = reading("80", start.plusSeconds(2), null);
        second.setCycleId(11L); second.setBindingId(101L); second.setPlotId(4L);
        when(readingMapper.findInWindow("DEV1", "temperature", start, start.plusMinutes(5))).thenReturn(List.of(first, second));
        when(windowMapper.findByDataHash(any())).thenReturn(null, new IotDataWindowEntity());
        service().aggregateForReading(first);
        org.mockito.ArgumentCaptor<IotDataWindowEntity> capture = org.mockito.ArgumentCaptor.forClass(IotDataWindowEntity.class);
        verify(windowMapper).insert(capture.capture());
        assertEquals(1, capture.getValue().getSampleCount()); assertEquals(new BigDecimal("20.000000"), capture.getValue().getAvgValue());
        assertEquals(10L, capture.getValue().getCycleId()); assertEquals(100L, capture.getValue().getBindingId());
    }

    private IotSensorReadingEntity reading(String value, LocalDateTime reportedAt, Long batchId) {
        IotSensorReadingEntity reading = new IotSensorReadingEntity();
        reading.setFarmId(1L);
        reading.setGatewayCode("GW1");
        reading.setDeviceCode("DEV1");
        reading.setMetric("temperature");
        reading.setValue(new BigDecimal(value));
        reading.setReportedAt(reportedAt);
        reading.setBatchId(batchId);
        return reading;
    }
}
