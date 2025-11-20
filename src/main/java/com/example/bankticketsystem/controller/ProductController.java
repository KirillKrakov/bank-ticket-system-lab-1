package com.example.bankticketsystem.controller;

import com.example.bankticketsystem.dto.ProductDto;
import com.example.bankticketsystem.service.ProductService;
import com.example.bankticketsystem.exception.BadRequestException;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.data.domain.Page;

import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private static final int MAX_PAGE_SIZE = 50;
    private final ProductService productService;

    public ProductController(ProductService productService) { this.productService = productService; }

    // Create: POST "/api/v1/products" + ProductDto(name,description) (Body)
    @PostMapping
    public ResponseEntity<ProductDto> create(@Valid @RequestBody ProductDto req, UriComponentsBuilder uriBuilder) {
        ProductDto dto = productService.create(req);
        URI location = uriBuilder.path("/api/v1/products/{id}").buildAndExpand(dto.getId()).toUri();
        return ResponseEntity.created(location).body(dto);
    }

    // ReadAll: GET “api/v1/products?page=0&size=20”
    @GetMapping
    public ResponseEntity<List<ProductDto>> list(@RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size,
                                                 HttpServletResponse response) {
        if (size > MAX_PAGE_SIZE) {
            throw new BadRequestException("size cannot be greater than " + MAX_PAGE_SIZE);
        }
        Page<ProductDto> p = productService.list(page, size);
        response.setHeader("X-Total-Count", String.valueOf(p.getTotalElements()));
        return ResponseEntity.ok(p.getContent());
    }

    // Read: GET “/api/v1/products/{id}”
    @GetMapping("/{id}")
    public ResponseEntity<ProductDto> get(@PathVariable UUID id) {
        ProductDto productDto = productService.get(id);
        return productDto == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(productDto);
    }

    // Update: PUT “/api/v1/products/{id}?actorId={adminOrOwnerId}” + ProductCreateRequest (Body)
    @PutMapping("/{id}")
    public ResponseEntity<ProductDto> updateProduct(@PathVariable("id") UUID id,
                                                    @Valid @RequestBody ProductDto req,
                                                    @RequestParam("actorId") UUID actorId) {
        if (actorId == null) throw new BadRequestException("actorId required");
        ProductDto dto = productService.updateProduct(id, req, actorId);
        return ResponseEntity.ok(dto);
    }

    // Delete: DELETE “/api/v1/products/{id}?actorId={adminOrOwnerId}
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable("id") UUID id,
                                              @RequestParam("actorId") UUID actorId) {
        if (actorId == null) throw new BadRequestException("actorId required");
        productService.deleteProduct(id, actorId);
        return ResponseEntity.noContent().build();
    }
}
