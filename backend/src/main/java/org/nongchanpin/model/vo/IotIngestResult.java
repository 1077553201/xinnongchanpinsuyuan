package org.nongchanpin.model.vo;

import java.util.List;
import lombok.Data;
import org.nongchanpin.model.entity.IotAlertEntity;
import org.nongchanpin.model.entity.IotDataWindowEntity;
import org.nongchanpin.model.entity.IotSensorReadingEntity;

@Data
public class IotIngestResult {
    private IotSensorReadingEntity reading;
    private List<IotAlertEntity> alerts;
    private IotDataWindowEntity window;
}
