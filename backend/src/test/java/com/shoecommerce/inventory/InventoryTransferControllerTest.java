package com.shoecommerce.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.shoecommerce.identity.SessionPrincipal;

class InventoryTransferControllerTest {
    @Test
    void createUses201OnlyForTheFirstBusinessEffectAndCommandsUseStablePaths() {
        FakeTransferService service = new FakeTransferService();
        InventoryTransferController controller = new InventoryTransferController(service);
        UUID source = UUID.randomUUID();
        UUID destination = UUID.randomUUID();
        UUID variant = UUID.randomUUID();
        UUID transferId = UUID.randomUUID();
        var request = new InventoryTransferController.CreateRequest(source, destination,
                List.of(new InventoryTransferController.LineRequest(variant, 2)));
        var first = view(transferId, source, destination, variant, false);
        var replay = view(transferId, source, destination, variant, true);
        service.created = first;

        var created = controller.create(null, "create-key", request);
        service.created = replay;
        var replayed = controller.create(null, "create-key", request);
        service.transitioned = first;

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(replayed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(controller.dispatch(null, transferId, "dispatch-key")).isEqualTo(first);
        assertThat(controller.receive(null, transferId, "receive-key")).isEqualTo(first);
    }

    private static final class FakeTransferService extends InventoryTransferService {
        private TransferView created;
        private TransferView transitioned;
        private FakeTransferService() { super(null, null, null, null, null, null, null, null, null, null, null); }
        @Override public TransferView create(SessionPrincipal actor, CreateCommand command, String key) { return created; }
        @Override public TransferView dispatch(SessionPrincipal actor, UUID id, String key) { return transitioned; }
        @Override public TransferView receive(SessionPrincipal actor, UUID id, String key) { return transitioned; }
    }

    private static InventoryTransferService.TransferView view(UUID id, UUID source, UUID destination,
            UUID variant, boolean replayed) {
        Instant now = Instant.parse("2026-10-12T00:00:00Z");
        return new InventoryTransferService.TransferView(id, source, "SRC", "Source", destination, "DST",
                "Destination", "DRAFT", now, null, null,
                List.of(new InventoryTransferService.LineView(variant, "SKU-1", 2, 0, 0)), replayed);
    }
}
