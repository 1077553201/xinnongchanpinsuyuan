package org.nongchanpin.service;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.nongchanpin.constants.IotConstants;
import org.nongchanpin.mapper.IotDeviceMapper;
import org.nongchanpin.mapper.IotGatewayMapper;
import org.nongchanpin.model.dto.RegisterDeviceRequest;
import org.nongchanpin.model.dto.RegisterGatewayRequest;
import org.nongchanpin.model.entity.IotDeviceEntity;
import org.nongchanpin.model.entity.IotGatewayEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IotDeviceService {
    private final IotGatewayMapper gatewayMapper;
    private final IotDeviceMapper deviceMapper;

    @Transactional
    public IotGatewayEntity registerGateway(RegisterGatewayRequest request) {
        IotGatewayEntity existing = gatewayMapper.findByCode(request.getGatewayCode());
        if (existing != null) {
            return existing;
        }
        IotGatewayEntity gateway = new IotGatewayEntity();
        gateway.setFarmId(request.getFarmId());
        gateway.setGatewayCode(request.getGatewayCode());
        gateway.setGatewayName(request.getGatewayName());
        gateway.setMqttClientId(request.getMqttClientId());
        gateway.setStatus(IotConstants.GATEWAY_STATUS_ACTIVE);
        gatewayMapper.insert(gateway);
        return gatewayMapper.findByCode(request.getGatewayCode());
    }

    @Transactional
    public IotDeviceEntity registerDevice(RegisterDeviceRequest request) {
        IotDeviceEntity existing = deviceMapper.findByCode(request.getDeviceCode());
        if (existing != null) {
            return existing;
        }
        IotGatewayEntity gateway = gatewayMapper.findByCode(request.getGatewayCode());
        IotDeviceEntity device = new IotDeviceEntity();
        device.setGatewayId(gateway == null ? null : gateway.getId());
        device.setFarmId(request.getFarmId());
        device.setGatewayCode(request.getGatewayCode());
        device.setDeviceCode(request.getDeviceCode());
        device.setDeviceName(request.getDeviceName());
        device.setDeviceType(request.getDeviceType());
        device.setStatus(IotConstants.DEVICE_STATUS_ACTIVE);
        device.setLastSeenAt(LocalDateTime.now());
        deviceMapper.insert(device);
        return deviceMapper.findByCode(request.getDeviceCode());
    }

    public List<IotGatewayEntity> listGateways() {
        return gatewayMapper.findAll();
    }

    public List<IotDeviceEntity> listDevices() {
        return deviceMapper.findAll();
    }
}
