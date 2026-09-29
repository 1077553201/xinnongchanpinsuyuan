package org.nongchanpin.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.nongchanpin.model.entity.NfcTagEntity;

@Mapper
public interface NfcTagMapper {
    @Insert("INSERT INTO nfc_tag (uid_hash, tag_hash, item_id, bind_status, bound_at, status) "
            + "VALUES (#{uidHash}, #{tagHash}, #{itemId}, #{bindStatus}, #{boundAt}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(NfcTagEntity tag);

    @Select("SELECT * FROM nfc_tag WHERE id = #{id}")
    NfcTagEntity findById(Long id);

    @Select("SELECT * FROM nfc_tag WHERE item_id = #{itemId} AND status = 'ACTIVE' ORDER BY id DESC LIMIT 1")
    NfcTagEntity findActiveByItemId(Long itemId);

    @Select("SELECT * FROM nfc_tag WHERE uid_hash = #{uidHash}")
    NfcTagEntity findByUidHash(String uidHash);

    @Update("UPDATE nfc_tag SET bind_status = #{bindStatus}, status = #{status}, update_time = NOW() WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("bindStatus") String bindStatus, @Param("status") String status);
}
