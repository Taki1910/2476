package com.shoecommerce.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.shoecommerce.identity.SessionPrincipal;

class InventoryReceiptControllerTest {
    @Test
    void reportsCreatedForANewReceiptAndOkForAReplay() {
        FakeReceiptService receipts = new FakeReceiptService();
        InventoryReceiptController controller = new InventoryReceiptController(receipts);
        UUID variantId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();

        receipts.created = true;
        var created = controller.receive(null, variantId, locationId, "receipt-key",
                new InventoryReceiptController.ReceiptRequest(3, "PO-123"));
        receipts.created = false;
        var replay = controller.receive(null, variantId, locationId, "receipt-key",
                new InventoryReceiptController.ReceiptRequest(3, "PO-123"));

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(replay.getBody().created()).isFalse();
    }

    private static final class FakeReceiptService extends InventoryReceiptService {
        private boolean created;

        private FakeReceiptService() {
            super(null, null, null, null, null, null, null, null);
        }

        @Override
        public ReceiptResult receive(SessionPrincipal actor, UUID variantId, UUID locationId, long quantity,
                String note, String idempotencyKey) {
            return new ReceiptResult(UUID.randomUUID(), variantId, locationId, quantity, 2, 2 + quantity, 1,
                    1 + quantity, quantity, 0, note, created);
        }
    }
}
