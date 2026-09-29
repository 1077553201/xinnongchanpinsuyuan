package org.nongchanpin.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.nongchanpin.model.entity.ChainTransactionEntity;

@Mapper
public interface ChainTransactionMapper {
    @Insert("INSERT INTO chain_transaction (business_type, business_id, business_id_hash, chain_status, contract_address, event_name, event_indexed_keys, payload_hash, request_hash, submitted_at, status) "
            + "VALUES (#{businessType}, #{businessId}, #{businessIdHash}, #{chainStatus}, #{contractAddress}, #{eventName}, #{eventIndexedKeys}, #{payloadHash}, #{requestHash}, #{submittedAt}, 'ACTIVE')")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ChainTransactionEntity tx);

    @Update("UPDATE chain_transaction SET chain_status = #{chainStatus}, tx_hash = #{txHash}, block_number = #{blockNumber}, receipt_status = #{receiptStatus}, "
            + "contract_address = #{contractAddress}, event_name = #{eventName}, payload_hash = #{payloadHash}, chain_operator = #{chainOperator}, confirmed_at = #{confirmedAt}, error_message = #{errorMessage}, update_time = NOW() WHERE id = #{id}")
    int updateResult(ChainTransactionEntity tx);

    @Select("SELECT * FROM chain_transaction WHERE business_type = #{businessType} AND business_id = #{businessId} AND status = 'ACTIVE' ORDER BY id DESC LIMIT 1")
    ChainTransactionEntity findLatestByBusiness(@Param("businessType") String businessType, @Param("businessId") Long businessId);
}
