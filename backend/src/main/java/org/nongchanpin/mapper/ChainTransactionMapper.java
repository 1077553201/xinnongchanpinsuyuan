package org.nongchanpin.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.nongchanpin.model.entity.ChainTransactionEntity;
import java.util.List;

@Mapper
public interface ChainTransactionMapper {
    @Select({"<script>", "SELECT * FROM chain_transaction WHERE status = 'ACTIVE'",
            "<if test='businessType != null'>AND business_type = #{businessType}</if>",
            "<if test='businessId != null'>AND business_id = #{businessId}</if>",
            "<if test='chainStatus != null'>AND chain_status = #{chainStatus}</if>",
            "ORDER BY id DESC LIMIT #{limit}", "</script>"})
    List<ChainTransactionEntity> findRecent(@Param("businessType") String businessType,
            @Param("businessId") Long businessId, @Param("chainStatus") String chainStatus, @Param("limit") int limit);

    @Select("SELECT * FROM chain_transaction WHERE id = #{id} AND status = 'ACTIVE'")
    ChainTransactionEntity findById(Long id);

    @Select("SELECT * FROM chain_transaction WHERE id=#{id} AND status='ACTIVE' FOR UPDATE")
    ChainTransactionEntity lockReceipt(Long id);

    @Update("UPDATE chain_transaction SET chain_status=#{chainStatus},block_number=#{blockNumber},receipt_status=#{receiptStatus},chain_operator=#{chainOperator},confirmed_at=#{confirmedAt},error_message=#{errorMessage},receipt_verified_at=#{receiptVerifiedAt},receipt_verification_hash=#{receiptVerificationHash},update_time=NOW() WHERE id=#{id} AND tx_hash=#{txHash} AND status='ACTIVE'")
    int recordReceipt(ChainTransactionEntity tx);

    @Insert("INSERT INTO chain_transaction (business_type, business_id, business_id_hash, chain_status, contract_address, event_name, event_indexed_keys, payload_hash, request_hash, submitted_at, status) "
            + "VALUES (#{businessType}, #{businessId}, #{businessIdHash}, #{chainStatus}, #{contractAddress}, #{eventName}, #{eventIndexedKeys}, #{payloadHash}, #{requestHash}, #{submittedAt}, 'ACTIVE')")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ChainTransactionEntity tx);

    @Update("UPDATE chain_transaction SET chain_status = #{chainStatus}, tx_hash = #{txHash}, block_number = #{blockNumber}, receipt_status = #{receiptStatus}, "
            + "contract_address = #{contractAddress}, event_name = #{eventName}, payload_hash = #{payloadHash}, chain_operator = #{chainOperator}, confirmed_at = #{confirmedAt}, error_message = #{errorMessage}, update_time = NOW() WHERE id = #{id} AND receipt_verified_at IS NULL")
    int updateResult(ChainTransactionEntity tx);

    @Select("SELECT * FROM chain_transaction WHERE business_type = #{businessType} AND business_id = #{businessId} AND status = 'ACTIVE' ORDER BY id DESC LIMIT 1")
    ChainTransactionEntity findLatestByBusiness(@Param("businessType") String businessType, @Param("businessId") Long businessId);
}
