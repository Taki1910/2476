package com.shoecommerce.promotion;

import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.shoecommerce.identity.SessionPrincipal;

@RestController
@RequestMapping("/api/v1/me/voucher-claims")
public class VoucherClaimController {
    private final VoucherService vouchers;
    public VoucherClaimController(VoucherService vouchers){this.vouchers=vouchers;}
    @PutMapping("/{familyId}") VoucherService.ClaimView claim(@AuthenticationPrincipal SessionPrincipal actor,@PathVariable UUID familyId){return vouchers.claim(actor,familyId);}
    @GetMapping VoucherService.Page<VoucherService.SavedClaim> list(@AuthenticationPrincipal SessionPrincipal actor,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return vouchers.saved(actor,page,size);}
    @GetMapping("/{familyId}") VoucherService.ClaimState ownership(@AuthenticationPrincipal SessionPrincipal actor,@PathVariable UUID familyId){return vouchers.ownership(actor,familyId);}
}
