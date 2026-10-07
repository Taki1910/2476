package com.shoecommerce.promotion;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.shoecommerce.identity.SessionPrincipal;

@RestController
@RequestMapping("/api/v1/operations/promotions")
public class PromotionController {
    private final PromotionService promotions;
    private final VoucherService vouchers;
    public PromotionController(PromotionService promotions,VoucherService vouchers){this.promotions=promotions;this.vouchers=vouchers;}
    @GetMapping List<PromotionService.RuleView> list(@AuthenticationPrincipal SessionPrincipal actor){return promotions.rules(actor);}
    @GetMapping("/campaigns") PromotionService.CampaignList campaigns(@AuthenticationPrincipal SessionPrincipal actor){return promotions.campaigns(actor);}
    @GetMapping("/campaigns/{familyId}") PromotionService.CampaignView campaign(@AuthenticationPrincipal SessionPrincipal actor,@PathVariable UUID familyId){return promotions.campaign(actor,familyId);}
    @GetMapping("/product-references") List<PromotionService.ProductReference> productReferences(@AuthenticationPrincipal SessionPrincipal actor,@RequestParam(defaultValue="") String q){return promotions.productReferences(actor,q);}
    @PostMapping("/preview") PromotionService.PreviewResult preview(@AuthenticationPrincipal SessionPrincipal actor,@RequestBody PromotionService.PreviewRequest request){return promotions.preview(actor,request);}
    @PutMapping("/campaigns/{familyId}/discoverability") PromotionService.CampaignView discoverability(@AuthenticationPrincipal SessionPrincipal actor,@PathVariable UUID familyId,@RequestBody PromotionService.VisibilityCommand command){return promotions.setDiscoverability(actor,familyId,command);}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) PromotionService.RuleView create(@AuthenticationPrincipal SessionPrincipal actor,@RequestBody PromotionService.Draft draft){return promotions.create(actor,draft);}
    @PutMapping("/{id}") PromotionService.RuleView edit(@AuthenticationPrincipal SessionPrincipal actor,@PathVariable UUID id,@RequestBody PromotionService.Draft draft){return promotions.edit(actor,id,draft);}
    @PostMapping("/{id}/publish") PromotionService.RuleView publish(@AuthenticationPrincipal SessionPrincipal actor,@PathVariable UUID id,@RequestBody(required=false) PromotionService.PublishCommand command){return promotions.publish(actor,id,command==null?null:command.expectedPublishedRevisionId());}
    @PostMapping("/{id}/retire") PromotionService.RuleView retire(@AuthenticationPrincipal SessionPrincipal actor,@PathVariable UUID id){return promotions.transition(actor,id,"RETIRED");}
    @PostMapping("/{id}/invalidate") PromotionService.RuleView invalidate(@AuthenticationPrincipal SessionPrincipal actor,@PathVariable UUID id){return promotions.transition(actor,id,"INVALIDATED");}
    @PutMapping("/voucher-claims/{claimId}/revoke") VoucherService.ClaimView revoke(@AuthenticationPrincipal SessionPrincipal actor,@PathVariable UUID claimId){return vouchers.revoke(actor,claimId);}
    @GetMapping("/voucher-claims") VoucherService.Page<VoucherService.ManagedClaim> claims(@AuthenticationPrincipal SessionPrincipal actor,@RequestParam(required=false) UUID familyId,@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return vouchers.managedClaims(actor,familyId,q,page,size);}

}
