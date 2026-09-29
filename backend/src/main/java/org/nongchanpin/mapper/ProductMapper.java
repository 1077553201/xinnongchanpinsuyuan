package org.nongchanpin.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.nongchanpin.model.entity.ProductEntity;

@Mapper
public interface ProductMapper {
    @Insert("INSERT INTO product (organization_id, product_code, product_name, category, variety, origin_place, description, status) "
            + "VALUES (#{organizationId}, #{productCode}, #{productName}, #{category}, #{variety}, #{originPlace}, #{description}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ProductEntity product);

    @Select("SELECT * FROM product WHERE status <> 'DELETED' ORDER BY id DESC")
    List<ProductEntity> findAll();

    @Select("SELECT * FROM product WHERE id = #{id}")
    ProductEntity findById(Long id);
}
