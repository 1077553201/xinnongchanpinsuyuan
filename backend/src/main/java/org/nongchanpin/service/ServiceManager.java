package org.nongchanpin.service;

import java.lang.Exception;
import java.lang.String;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.nongchanpin.config.ContractConfig;
import org.nongchanpin.config.SystemConfig;
import org.fisco.bcos.sdk.v3.client.Client;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Data
@Slf4j
public class ServiceManager {
  @Autowired
  private SystemConfig systemConfig;

  @Autowired
  private ContractConfig contractConfig;

  @Autowired
  private Client client;

  List<String> hexPrivateKeyList;

  @PostConstruct
  public void init() {
    if (this.systemConfig.getHexPrivateKey() == null || this.systemConfig.getHexPrivateKey().trim().isEmpty()) {
      hexPrivateKeyList = Collections.emptyList();
      return;
    }
    hexPrivateKeyList = Arrays.asList(this.systemConfig.getHexPrivateKey().split(","));
  }

  /**
   * @notice: must use @Qualifier("AgriTraceService") with @Autowired to get this Bean
   */
  @Bean("AgriTraceService")
  public Map<String, AgriTraceService> initAgriTraceServiceManager() throws Exception {
    Map<String, AgriTraceService> serviceMap = new ConcurrentHashMap<>(this.hexPrivateKeyList.size());
    for (int i = 0; i < this.hexPrivateKeyList.size(); i++) {
    	String privateKey = this.hexPrivateKeyList.get(i);
    	if (privateKey.startsWith("0x") || privateKey.startsWith("0X")) {
    		privateKey = privateKey.substring(2);
    	}
    	if (privateKey.isEmpty()) {
    		continue;
    	}
    	org.fisco.bcos.sdk.v3.crypto.CryptoSuite cryptoSuite = new org.fisco.bcos.sdk.v3.crypto.CryptoSuite(this.client.getCryptoType());
    	org.fisco.bcos.sdk.v3.crypto.keypair.CryptoKeyPair cryptoKeyPair = cryptoSuite.getKeyPairFactory().createKeyPair(privateKey);
    	String userAddress = cryptoKeyPair.getAddress();
    	log.info("Configured AgriTrace service account index {}, userAddress:{}", i, userAddress);
    	AgriTraceService agriTraceService = new AgriTraceService();
    	agriTraceService.setAddress(this.contractConfig.getAgriTraceAddress());
    	agriTraceService.setClient(this.client);
    	org.fisco.bcos.sdk.v3.transaction.manager.AssembleTransactionProcessor txProcessor = 
    		org.fisco.bcos.sdk.v3.transaction.manager.TransactionProcessorFactory.createAssembleTransactionProcessor(this.client, cryptoKeyPair);
    	agriTraceService.setTxProcessor(txProcessor);
    	serviceMap.put(userAddress, agriTraceService);
    }
    log.info("Configured {} AgriTrace service account(s)", serviceMap.size());
    return serviceMap;
  }
}
