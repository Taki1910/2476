package com.shoecommerce.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.PlatformTransactionManager;

import com.shoecommerce.inventory.InventoryReservationService;
import com.shoecommerce.payment.PaymentAttemptService;
import com.shoecommerce.promotion.PromotionService;

class CheckoutHoldExpiryServiceTest {
    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
    void retiredVariantsRemainEligibleForCheckoutHoldExpiry() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        CheckoutHoldExpiryService service = new CheckoutHoldExpiryService(mock(CustomerOrderRepository.class),
                mock(InventoryReservationService.class), mock(PaymentAttemptService.class),
                Clock.fixed(Instant.parse("2026-09-28T00:00:00Z"), ZoneOffset.UTC),
                mock(PlatformTransactionManager.class), mock(PromotionService.class), jdbc);

        service.expireRelevant(null);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> parameters = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), parameters.capture());
        assertThat(sql.getValue()).contains("reservations.status = 'ADOPTED'")
                .doesNotContain("variants.lifecycle_status");
        assertThat(parameters.getValue()).containsExactly(LocalDateTime.parse("2026-09-28T00:00:00"));
    }
}
