package com.shoecommerce.fitting;

import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.shoecommerce.platform.api.InvalidRequestException;

@RestController
@RequestMapping("/api/v1/storefront/products")
public final class ShoeFitController {
    private final ShoeFitService service;

    public ShoeFitController(ShoeFitService service) { this.service = service; }

    @PostMapping(value = "/{productId}/fit-analysis", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ShoeFitService.FitResult analyze(@PathVariable UUID productId,
            @RequestPart("image") MultipartFile image,
            @RequestParam(required = false) String selectedColor) {
        try {
            return service.analyze(productId, selectedColor == null || selectedColor.isBlank() ? null : selectedColor.trim(), image.getBytes());
        } catch (java.io.IOException exception) {
            throw new InvalidRequestException("FIT_IMAGE_INVALID", "The image could not be read.");
        }
    }
}
