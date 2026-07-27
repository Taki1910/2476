package com.example.qlchgiay.repo;

import com.example.qlchgiay.model.Size;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SizeRepo extends JpaRepository<Size, Integer> {
    Optional<Size> findFirstByTenSizeIgnoreCase(String tenSize);
}
