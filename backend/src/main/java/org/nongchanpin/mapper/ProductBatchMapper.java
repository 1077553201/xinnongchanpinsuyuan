package org.nongchanpin.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.nongchanpin.model.entity.ProductBatchEntity;

@Mapper
public interface ProductBatchMapper {
    @Insert("INSERT INTO product_batch (product_id, organization_id, batch_code, production_area, plant_date, harvest_date, batch_metadata, chain_status, status) "
            + "VALUES (#{productId}, #{organizationId}, #{batchCode}, #{productionArea}, #{plantDate}, #{harvestDate}, #{batchMetadata}, #{chainStatus}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ProductBatchEntity batch);

    @Select("SELECT * FROM product_batch WHERE status <> 'DELETED' ORDER BY id DESC")
    List<ProductBatchEntity> findAll();

    @Select("SELECT * FROM product_batch WHERE id = #{id}")
    ProductBatchEntity findById(Long id);

    @Select("SELECT * FROM product_batch WHERE batch_code = #{batchCode}")
    ProductBatchEntity findByBatchCode(String batchCode);

    @Update("UPDATE product_batch SET chain_status = #{chainStatus}, status = #{status}, update_time = NOW() WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("chainStatus") String chainStatus, @Param("status") String status);

    @Update("UPDATE product_batch SET chain_status = #{chainStatus}, update_time = NOW() WHERE id = #{id}")
    int updateChainStatus(@Param("id") Long id, @Param("chainStatus") String chainStatus);
}
