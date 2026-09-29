package org.nongchanpin.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.nongchanpin.mapper.ProductMapper;
import org.nongchanpin.model.dto.CreateProductRequest;
import org.nongchanpin.model.entity.ProductEntity;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProductService {
    private final ProductMapper productMapper;

    public ProductEntity create(CreateProductRequest request) {
        ProductEntity product = new ProductEntity();
        product.setOrganizationId(request.getOrganizationId());
        product.setProductCode(request.getProductCode());
        product.setProductName(request.getProductName());
        product.setCategory(request.getCategory());
        product.setVariety(request.getVariety());
        product.setOriginPlace(request.getOriginPlace());
        product.setDescription(request.getDescription());
        product.setStatus("ACTIVE");
        productMapper.insert(product);
        return productMapper.findById(product.getId());
    }

    public List<ProductEntity> findAll() {
        return productMapper.findAll();
    }

    public ProductEntity get(Long id) {
        ProductEntity product = productMapper.findById(id);
        if (product == null) {
            throw new IllegalArgumentException("product not found");
        }
        return product;
    }
}
