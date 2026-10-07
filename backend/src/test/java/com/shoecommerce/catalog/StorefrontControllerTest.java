package com.shoecommerce.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.shoecommerce.pricing.CartQuoteService;
import com.shoecommerce.pricing.PriceQuoteService;

class StorefrontControllerTest {
    @Test
    void exposesDiscoveryWithoutChangingTheProductListContract() {
        StorefrontCatalogService catalog = mock(StorefrontCatalogService.class);
        StorefrontCatalogService.DiscoveryResponse expected = new StorefrontCatalogService.DiscoveryResponse(
                "giày trắng", List.of(), List.of(),
                new StorefrontCatalogService.InterpretedQuery(Set.of("WHITE"), Set.of(), null, false));
        when(catalog.discover("giày trắng")).thenReturn(expected);
        StorefrontController controller = new StorefrontController(catalog,
                mock(PriceQuoteService.class), mock(CartQuoteService.class));

        assertThat(controller.discovery("giày trắng")).isSameAs(expected);
        verify(catalog).discover("giày trắng");
    }
}
