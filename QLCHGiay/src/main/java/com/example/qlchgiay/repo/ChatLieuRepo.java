package com.example.qlchgiay.repo;

import com.example.qlchgiay.model.ChatLieu;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChatLieuRepo extends JpaRepository<ChatLieu, Integer> {
    Optional<ChatLieu> findFirstByTenChatLieuIgnoreCase(String tenChatLieu);
}
