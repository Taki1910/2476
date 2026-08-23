package com.example.qlchgiay.service;

import com.example.qlchgiay.model.AppNotification;
import com.example.qlchgiay.model.SanPham;
import com.example.qlchgiay.model.TaiKhoan;
import com.example.qlchgiay.repo.AppNotificationRepo;
import com.example.qlchgiay.repo.TaiKhoanRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppNotificationServiceTest {
    @Mock private AppNotificationRepo notificationRepo;
    @Mock private TaiKhoanRepo accountRepo;

    @Test
    void lowStockEventCreatesOneUserScopedRowPerActiveAccount() {
        TaiKhoan first = account(1, "ACTIVE");
        TaiKhoan second = account(2, null);
        TaiKhoan inactive = account(3, "INACTIVE");
        when(accountRepo.findAllWithEmployeesOrderByUsername())
                .thenReturn(List.of(first, second, inactive));
        SanPham product = product(8, 5);

        new AppNotificationService(notificationRepo, accountRepo)
                .notifyStockThreshold(product, 6, 77);

        ArgumentCaptor<AppNotification> saved = ArgumentCaptor.forClass(AppNotification.class);
        verify(notificationRepo, times(2)).save(saved.capture());
        assertEquals(List.of(1, 2), saved.getAllValues().stream()
                .map(item -> item.getRecipient().getId()).toList());
        assertTrue(saved.getAllValues().stream()
                .allMatch(item -> item.getDedupeKey().equals("LOW_STOCK:product:8:invoice:77")));
    }

    @Test
    void duplicateEventDoesNotCreateAnotherNotification() {
        TaiKhoan account = account(1, "ACTIVE");
        when(accountRepo.findAllWithEmployeesOrderByUsername()).thenReturn(List.of(account));
        when(notificationRepo.existsByRecipientIdAndDedupeKey(
                1, "OUT_OF_STOCK:product:8:invoice:77"
        )).thenReturn(true);

        new AppNotificationService(notificationRepo, accountRepo)
                .notifyStockThreshold(product(8, 0), 1, 77);

        verify(notificationRepo, never()).save(any());
    }

    @Test
    void markReadCannotAccessAnotherUsersNotification() {
        AppNotificationService service = new AppNotificationService(notificationRepo, accountRepo);
        when(notificationRepo.findByIdAndRecipientId(9, 2)).thenReturn(Optional.empty());

        assertFalse(service.markRead(9, 2));
        verify(notificationRepo, never()).save(any());
    }

    @Test
    void markReadPersistsForTheOwningUser() {
        AppNotification notification = new AppNotification();
        when(notificationRepo.findByIdAndRecipientId(9, 1))
                .thenReturn(Optional.of(notification));

        assertTrue(new AppNotificationService(notificationRepo, accountRepo).markRead(9, 1));

        assertNotNull(notification.getReadAt());
        verify(notificationRepo).save(notification);
    }

    @Test
    void aNewRequestReloadsNotificationsFromRepository() {
        AppNotification notification = new AppNotification();
        when(notificationRepo.findTop20ByRecipientIdOrderByCreatedAtDesc(1))
                .thenReturn(List.of(notification));
        AppNotificationService service = new AppNotificationService(notificationRepo, accountRepo);

        assertEquals(1, service.latestFor(1).size());
        assertEquals(1, service.latestFor(1).size());

        verify(notificationRepo, times(2)).findTop20ByRecipientIdOrderByCreatedAtDesc(1);
    }

    private TaiKhoan account(int id, String status) {
        TaiKhoan account = new TaiKhoan();
        account.setId(id);
        account.setTrangThai(status);
        return account;
    }

    private SanPham product(int id, int stock) {
        SanPham product = new SanPham();
        product.setId(id);
        product.setTenSP("Giày kiểm thử");
        product.setTonKho(stock);
        return product;
    }
}
