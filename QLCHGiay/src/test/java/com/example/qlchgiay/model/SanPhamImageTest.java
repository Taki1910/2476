package com.example.qlchgiay.model;

import com.example.qlchgiay.repo.SanPhamRepo;
import org.hibernate.collection.spi.PersistentSet;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SanPhamImageTest {
    @Test
    void returnsLoadedImageAndHandlesMissingImages() {
        SanPham withImage = productWithDetails(detail(null), detail("/images/products/nike.svg"));
        SanPham withNullImage = productWithDetails(detail(null));
        SanPham withoutDetails = productWithDetails();

        assertEquals("/images/products/nike.svg", withImage.getHinhAnh());
        assertNull(withNullImage.getHinhAnh());
        assertNull(withoutDetails.getHinhAnh());
        assertEquals(
                Arrays.asList("/images/products/nike.svg", null, null),
                Arrays.asList(
                        withImage.getHinhAnh(),
                        withNullImage.getHinhAnh(),
                        withoutDetails.getHinhAnh()
                )
        );
    }

    @Test
    void detachedLazyDetailsAreNotInitializedByImageGetter() {
        SanPham product = new SanPham();
        PersistentSet<ChiTietSanPham> details = new PersistentSet<>(null);
        product.setChiTietSanPhams(details);

        assertNull(product.getHinhAnh());
        assertFalse(details.wasInitialized());
    }

    @Test
    void imageRenderingQueriesFetchProductDetails() throws Exception {
        assertFetchesDetails("findByTrangThaiOrderByTenSPAsc", String.class);
        assertFetchesDetails("findByTrangThai", String.class, Sort.class);
    }

    private void assertFetchesDetails(String name, Class<?>... parameterTypes) throws Exception {
        Method method = SanPhamRepo.class.getMethod(name, parameterTypes);
        EntityGraph graph = method.getAnnotation(EntityGraph.class);

        assertTrue(graph != null && Arrays.asList(graph.attributePaths()).contains("chiTietSanPhams"));
    }

    private SanPham productWithDetails(ChiTietSanPham... details) {
        SanPham product = new SanPham();
        product.setChiTietSanPhams(new LinkedHashSet<>(Arrays.asList(details)));
        return product;
    }

    private ChiTietSanPham detail(String image) {
        ChiTietSanPham detail = new ChiTietSanPham();
        detail.setHinhAnh(image);
        return detail;
    }
}
