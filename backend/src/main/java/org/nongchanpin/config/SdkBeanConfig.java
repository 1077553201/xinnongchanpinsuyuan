package org.nongchanpin.config;

import lombok.extern.slf4j.Slf4j;
import org.fisco.bcos.sdk.v3.BcosSDK;
import org.fisco.bcos.sdk.v3.client.Client;
import org.fisco.bcos.sdk.v3.config.ConfigOption;
import org.fisco.bcos.sdk.v3.config.model.ConfigProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigInteger;
import java.util.Map;

@Configuration
@Slf4j
public class SdkBeanConfig {

    @Autowired
    private SystemConfig systemConfig;

    @Autowired
    private BcosConfig bcosConfig;

    @Bean
    public Client client() throws Exception {
        ConfigProperty property = new ConfigProperty();
        configNetwork(property);
        configCryptoMaterial(property);

        ConfigOption configOption = new ConfigOption(property);
        Client client = new BcosSDK(configOption).getClient(systemConfig.getGroupId());

        BigInteger blockNumber = client.getBlockNumber().getBlockNumber();
        log.info("Chain connect successful. Current block number {}", blockNumber);

        configCryptoKeyPair(client);
        log.info("is Gm:{}, address:{}", client.getCryptoSuite().cryptoTypeConfig == 1, client.getCryptoSuite().getCryptoKeyPair().getAddress());
        return client;
    }

    public void configNetwork(ConfigProperty configProperty){
        Map peers = bcosConfig.getNetwork();
        configProperty.setNetwork(peers);
    }

    public void configCryptoMaterial(ConfigProperty configProperty){
        Map<String, Object> cryptoMaterials = bcosConfig.getCryptoMaterial();
        configProperty.setCryptoMaterial(cryptoMaterials);
    }

    public void configCryptoKeyPair(Client client) {
        if(systemConfig.getHexPrivateKey() == null || systemConfig.getHexPrivateKey().isEmpty()){
            return;
        }
        // 此处适配webase的多个hexPrivateKey，选中其中一个
        String privateKey;
        if (!systemConfig.getHexPrivateKey().contains(",")) {
            privateKey = systemConfig.getHexPrivateKey();
        } else {
            String[] list = systemConfig.getHexPrivateKey().split(",");
            privateKey = list[0];
        }
        if (privateKey.startsWith("0x") || privateKey.startsWith("0X")) {
            privateKey = privateKey.substring(2);
            systemConfig.setHexPrivateKey(privateKey);
        }
        client.getCryptoSuite().setCryptoKeyPair(client.getCryptoSuite().loadKeyPair(systemConfig.getHexPrivateKey()));
    }
}
