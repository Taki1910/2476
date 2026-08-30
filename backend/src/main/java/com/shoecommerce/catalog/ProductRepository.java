package com.shoecommerce.catalog;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ProductRepository extends JpaRepository<Product, Long> { Optional<Product> findByPublicId(UUID publicId); }
