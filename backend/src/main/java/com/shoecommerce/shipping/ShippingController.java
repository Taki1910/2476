package com.shoecommerce.shipping;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.shoecommerce.identity.SessionPrincipal;

@RestController
@RequestMapping("/api/v1")
public class ShippingController {
    private final ShippingService shipping;
    public ShippingController(ShippingService shipping){this.shipping=shipping;}
    @GetMapping("/reference/provinces") List<ShippingService.Reference> provinces(){return shipping.provinces();}
    @GetMapping("/reference/provinces/{code}/districts") List<ShippingService.Reference> districts(@PathVariable String code){return shipping.districts(code);}
    @GetMapping("/reference/provinces/{provinceCode}/districts/{districtCode}/wards")
    List<ShippingService.Reference> wards(@PathVariable String provinceCode,@PathVariable String districtCode){return shipping.wards(provinceCode,districtCode);}
    @GetMapping("/operations/shipping-rules") List<ShippingService.RuleView> rules(@AuthenticationPrincipal SessionPrincipal actor){return shipping.rules(actor);}
    @GetMapping("/operations/shipping-rules/locations") List<ShippingService.LocationReference> locations(@AuthenticationPrincipal SessionPrincipal actor){return shipping.locations(actor);}
    @PostMapping("/operations/shipping-rules") @ResponseStatus(HttpStatus.CREATED)
    ShippingService.RuleView create(@AuthenticationPrincipal SessionPrincipal actor,@RequestBody ShippingService.Draft request){return shipping.createDraft(actor,request);}
    @PutMapping("/operations/shipping-rules/{id}") ShippingService.RuleView edit(@AuthenticationPrincipal SessionPrincipal actor,@PathVariable UUID id,@RequestBody ShippingService.Draft request){return shipping.editDraft(actor,id,request);}
    @PostMapping("/operations/shipping-rules/{id}/publish") ShippingService.RuleView publish(@AuthenticationPrincipal SessionPrincipal actor,@PathVariable UUID id){return shipping.publish(actor,id);}
    @PostMapping("/operations/shipping-rules/{id}/retire") ShippingService.RuleView retire(@AuthenticationPrincipal SessionPrincipal actor,@PathVariable UUID id){return shipping.transition(actor,id,"RETIRED");}
    @PostMapping("/operations/shipping-rules/{id}/invalidate") ShippingService.RuleView invalidate(@AuthenticationPrincipal SessionPrincipal actor,@PathVariable UUID id){return shipping.transition(actor,id,"INVALIDATED");}
}
