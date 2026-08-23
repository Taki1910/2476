package com.example.qlchgiay.service;

import com.example.qlchgiay.model.AppNotification;
import com.example.qlchgiay.model.SanPham;
import com.example.qlchgiay.model.TaiKhoan;
import com.example.qlchgiay.repo.AppNotificationRepo;
import com.example.qlchgiay.repo.TaiKhoanRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class AppNotificationService {
    public static final String LOW_STOCK = "LOW_STOCK";
    public static final String OUT_OF_STOCK = "OUT_OF_STOCK";

    private final AppNotificationRepo notificationRepo;
    private final TaiKhoanRepo accountRepo;

    public AppNotificationService(AppNotificationRepo notificationRepo, TaiKhoanRepo accountRepo) {
        this.notificationRepo = notificationRepo;
        this.accountRepo = accountRepo;
    }

    @Transactional(readOnly = true)
    public List<AppNotification> latestFor(Integer accountId) {
        return accountId == null
                ? List.of()
                : notificationRepo.findTop20ByRecipientIdOrderByCreatedAtDesc(accountId);
    }

    @Transactional(readOnly = true)
    public long unreadCount(Integer accountId) {
        return accountId == null ? 0 : notificationRepo.countByRecipientIdAndReadAtIsNull(accountId);
    }

    @Transactional
    public boolean markRead(Integer notificationId, Integer accountId) {
        AppNotification notification = notificationRepo
                .findByIdAndRecipientId(notificationId, accountId)
                .orElse(null);
        if (notification == null) return false;
        if (notification.getReadAt() == null) {
            notification.setReadAt(LocalDateTime.now());
            notificationRepo.save(notification);
        }
        return true;
    }

    @Transactional
    public void notifyStockThreshold(SanPham product, int previousStock, Integer invoiceId) {
        int currentStock = product.getTonKho() == null ? 0 : product.getTonKho();
        String type;
        String title;
        if (currentStock == 0 && previousStock > 0) {
            type = OUT_OF_STOCK;
            title = "Sản phẩm đã hết hàng";
        } else if (currentStock <= 5 && previousStock > 5) {
            type = LOW_STOCK;
            title = "Sản phẩm sắp hết hàng";
        } else {
            return;
        }

        String message = product.getTenSP() + " (#SP-" + product.getId()
                + ") còn " + currentStock + " đôi sau khi thanh toán hóa đơn #HD-" + invoiceId + ".";
        String dedupeKey = type + ":product:" + product.getId() + ":invoice:" + invoiceId;
        for (TaiKhoan account : accountRepo.findAllWithEmployeesOrderByUsername()) {
            if (!isActive(account)) continue;
            if (notificationRepo.existsByRecipientIdAndDedupeKey(account.getId(), dedupeKey)) continue;
            AppNotification notification = new AppNotification();
            notification.setType(type);
            notification.setTitle(title);
            notification.setMessage(message);
            notification.setRecipient(account);
            notification.setRelatedEntityType("SAN_PHAM");
            notification.setRelatedEntityId(product.getId());
            notification.setDedupeKey(dedupeKey);
            notificationRepo.save(notification);
        }
    }

    private boolean isActive(TaiKhoan account) {
        return account != null
                && account.getId() != null
                && !isInactive(account.getTrangThai())
                && (account.getMaNhanVien() == null
                    || !isInactive(account.getMaNhanVien().getTrangThai()));
    }

    private boolean isInactive(String status) {
        String value = Normalizer.normalize(status == null ? "" : status, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        return value.contains("ngung") || value.contains("khoa")
                || value.contains("inactive") || value.contains("disable");
    }
}
