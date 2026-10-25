package org.nongchanpin.mapper;

import java.util.List;
import org.apache.ibatis.annotations.*;
import org.nongchanpin.model.entity.PackingNfcOperation;

@Mapper
public interface PackingNfcMapper {
    @Select("SELECT * FROM packing_nfc_operation WHERE id=#{id}")
    PackingNfcOperation find(Long id);
    @Select("SELECT * FROM packing_nfc_operation WHERE id=#{id} FOR UPDATE")
    PackingNfcOperation lockFrozen(Long id);
    @Select("SELECT * FROM packing_nfc_operation WHERE item_id=#{itemId} AND operation=#{operation}")
    PackingNfcOperation operation(@Param("itemId") Long itemId,@Param("operation") String operation);

    @Select("SELECT * FROM packing_nfc_operation WHERE item_id=#{itemId} ORDER BY id")
    List<PackingNfcOperation> history(Long itemId);

    @Insert("INSERT INTO packing_nfc_operation(packing_id,item_id,tag_id,operation,payload_json,payload_hash,private_reason,chain_status,recorded_by) VALUES(#{packingId},#{itemId},#{tagId},#{operation},#{payloadJson},#{payloadHash},#{privateReason},#{chainStatus},#{recordedBy})")
    @Options(useGeneratedKeys=true,keyProperty="id")
    int insert(PackingNfcOperation row);

    @Update("UPDATE packing_nfc_operation SET chain_status=#{chainStatus},tx_id=#{txId},error_code=#{errorCode} WHERE id=#{id}")
    int state(PackingNfcOperation row);

    @Update("UPDATE packing_nfc_operation SET lease_token=#{token},lease_until=#{until} WHERE item_id=#{itemId} AND operation='BIND'")
    int claim(@Param("itemId") Long itemId,@Param("token") String token,@Param("until") java.time.LocalDateTime until);

    @Update("UPDATE packing_nfc_operation SET lease_token=NULL,lease_until=NULL WHERE item_id=#{itemId} AND operation='BIND' AND lease_token=#{token}")
    int release(@Param("itemId") Long itemId,@Param("token") String token);
}
