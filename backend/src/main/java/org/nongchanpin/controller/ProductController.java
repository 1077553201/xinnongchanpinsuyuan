package org.nongchanpin.controller;

import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.nongchanpin.model.CommonResponse;
import org.nongchanpin.model.dto.CreateProductRequest;
import org.nongchanpin.service.ProductService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {
    private final ProductService productService;

    @PostMapping
    public CommonResponse create(@Valid @RequestBody CreateProductRequest request) {
        return CommonResponse.ok(productService.create(request));
    }

    @GetMapping
    public CommonResponse findAll() {
        return CommonResponse.ok(productService.findAll());
    }

    @GetMapping("/{id}")
    public CommonResponse get(@PathVariable Long id) {
        return CommonResponse.ok(productService.get(id));
    }
}
