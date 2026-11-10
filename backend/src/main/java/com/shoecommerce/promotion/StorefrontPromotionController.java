package com.shoecommerce.promotion;

import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/storefront/promotions")
public class StorefrontPromotionController {
    private final VoucherService vouchers;
    public StorefrontPromotionController(VoucherService vouchers){this.vouchers=vouchers;}
    @GetMapping VoucherService.Page<VoucherService.PublicOffer> list(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return vouchers.discover(page,size);}
    @GetMapping("/{familyId}") VoucherService.PublicOffer detail(@PathVariable UUID familyId){return vouchers.detail(familyId);}
}
