package com.shoecommerce.merchandising;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/storefront/homepage")
public class StorefrontHomepageController {
    private final StorefrontMerchandisingService merchandising;

    public StorefrontHomepageController(StorefrontMerchandisingService merchandising) {
        this.merchandising = merchandising;
    }

    @GetMapping
    StorefrontMerchandisingService.PublishedHomepage homepage() {
        return merchandising.homepage();
    }
}
