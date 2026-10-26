package org.nongchanpin.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.*;
import org.nongchanpin.mapper.ProductMapper;
import org.nongchanpin.model.dto.CreateProductRequest;
import org.nongchanpin.security.*;

class ProductOrganizationTest {
    private final ProductMapper products = mock(ProductMapper.class);
    private final ProductService service = new ProductService(products);
    @BeforeEach void setup() { Actor actor = new Actor(); actor.setId(1L); actor.setRole("MANAGER"); actor.setOrganizationId(3L); AccessContext.set(actor); }
    @AfterEach void cleanup() { AccessContext.clear(); }
    @Test void inactiveOrMissingOrganizationCannotReceiveProduct() {
        CreateProductRequest request = new CreateProductRequest(); request.setOrganizationId(3L);
        assertThrows(IllegalArgumentException.class, () -> service.create(request));
        verify(products, never()).insert(any());
    }
    @Test void crossOrganizationIsRejectedBeforeLockingOrWriting() {
        CreateProductRequest request = new CreateProductRequest(); request.setOrganizationId(4L);
        assertThrows(AccessException.class, () -> service.create(request)); verifyNoInteractions(products);
    }
    @Test void activeOrganizationIsLockedBeforeProductCreation() {
        when(products.lockActiveOrganization(3L)).thenReturn(3L);
        CreateProductRequest request = new CreateProductRequest(); request.setOrganizationId(3L); request.setProductCode("P-3");
        service.create(request); var order = inOrder(products); order.verify(products).lockActiveOrganization(3L); order.verify(products).insert(any());
    }
}
