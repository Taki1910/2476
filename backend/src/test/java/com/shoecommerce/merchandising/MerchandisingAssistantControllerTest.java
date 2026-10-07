package com.shoecommerce.merchandising;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.shoecommerce.identity.SessionPrincipal;
import com.shoecommerce.merchandising.MerchandisingAssistant.Availability;
import com.shoecommerce.merchandising.MerchandisingAssistant.AvailabilityStatus;
import com.shoecommerce.merchandising.MerchandisingAssistant.CopySuggestion;
import com.shoecommerce.merchandising.MerchandisingAssistant.Intent;
import com.shoecommerce.merchandising.MerchandisingAssistant.IntentInterpretation;

class MerchandisingAssistantControllerTest {
    @Test
    void delegatesAvailabilityCopyAndIntentToAuthorizedApplicationService() {
        MerchandisingAssistantService service = mock(MerchandisingAssistantService.class);
        SessionPrincipal actor = mock(SessionPrincipal.class);
        var copy = mock(MerchandisingAssistantService.CopyRequest.class);
        var intent = new MerchandisingAssistantService.IntentRequest("bán chạy");
        var availability = new Availability(true, true, AvailabilityStatus.AVAILABLE);
        var suggestion = new CopySuggestion("Mới", List.of(), "");
        var interpretation = new IntentInterpretation(Intent.BEST_SELLERS, "", "Bộ lọc bán chạy.");
        when(service.availability(actor)).thenReturn(availability);
        when(service.rewriteCopy(actor, copy)).thenReturn(suggestion);
        when(service.interpretIntent(actor, intent)).thenReturn(interpretation);
        MerchandisingAssistantController controller = new MerchandisingAssistantController(service);

        assertThat(controller.availability(actor)).isSameAs(availability);
        assertThat(controller.copy(actor, copy)).isSameAs(suggestion);
        assertThat(controller.intent(actor, intent)).isSameAs(interpretation);
        verify(service).availability(actor);
        verify(service).rewriteCopy(actor, copy);
        verify(service).interpretIntent(actor, intent);
    }
}
