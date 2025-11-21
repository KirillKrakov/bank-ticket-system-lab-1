package com.example.bankticketsystem.controller;

import com.example.bankticketsystem.dto.ProductDto;
import com.example.bankticketsystem.dto.ProductRequest;
import com.example.bankticketsystem.service.ProductAppManagementService;
import com.example.bankticketsystem.service.ProductService;
import com.example.bankticketsystem.exception.BadRequestException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.data.domain.Page;

import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.util.List;
import java.util.UUID;

@Tag(name = "Produsts", description = "API for managing products")
@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private static final int MAX_PAGE_SIZE = 50;
    private final ProductService productService;
    private final ProductAppManagementService managementService;

    public ProductController(ProductService productService, ProductAppManagementService managementService) { this.productService = productService;
        this.managementService = managementService;
    }

    // Create: POST "/api/v1/products" + ProductDto(name,description) (Body)
    @Operation(summary = "Create a new product", description = "Registers a new product: name, description")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Product created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request body"),
            @ApiResponse(responseCode = "403", description = "Forbidden input data: product ID"),
            @ApiResponse(responseCode = "409", description = "Product name already in use")
    })
    @PostMapping
    public ResponseEntity<ProductDto> create(@Valid @RequestBody ProductRequest req, UriComponentsBuilder uriBuilder) {
        ProductDto dto = productService.create(req);
        URI location = uriBuilder.path("/api/v1/products/{id}").buildAndExpand(dto.getId()).toUri();
        return ResponseEntity.created(location).body(dto);
    }

    // ReadAll: GET “api/v1/products?page=0&size=20”
    @Operation(summary = "Read all products with pagination", description = "Returns a paginated list of products")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List of products"),
            @ApiResponse(responseCode = "400", description = "Page size too large")
    })
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
    @Operation(summary = "Read certain product by its ID", description = "Returns data about a single product: " +
            "ID, name, description")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Data about a single user"),
            @ApiResponse(responseCode = "404", description = "Product with this ID is not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ProductDto> get(@PathVariable UUID id) {
        ProductDto productDto = productService.get(id);
        return productDto == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(productDto);
    }

    // Update: PUT “/api/v1/products/{id}?actorId={adminOrOwnerId}” + ProductDto (name, description) (Body)
    @Operation(summary = "Update the data of a specific product found by ID", description = "Update any data of single product and returns it " +
            "if the actor has sufficient rights")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Product updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request body"),
            @ApiResponse(responseCode = "401", description = "Actor is unauthorized (actorId is null"),
            @ApiResponse(responseCode = "403", description = "Forbidden input data (like new product ID) or insufficient level of actor's rights " +
                    "(not ADMIN or PRODUCT_OWNER)"),
            @ApiResponse(responseCode = "404", description = "Product or actor with their ID are not found")
    })
    @PutMapping("/{id}")
    public ResponseEntity<ProductDto> updateProduct(@PathVariable("id") UUID id,
                                                    @Valid @RequestBody ProductRequest req,
                                                    @RequestParam("actorId") UUID actorId) {
        ProductDto dto = managementService.updateProduct(id, req, actorId);
        return ResponseEntity.ok(dto);
    }

    // Delete: DELETE “/api/v1/products/{id}?actorId={adminOrOwnerId}
    @Operation(summary = "Delete a specific product found by ID", description = "Deletes one specific product from the database " +
            "if the actor has sufficient rights")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Product deleted successfully"),
            @ApiResponse(responseCode = "401", description = "Actor is unauthorized (actorId is null"),
            @ApiResponse(responseCode = "403", description = "Insufficient level of actor's rights to delete user (not ADMIN or PRODUCT_OWNER)"),
            @ApiResponse(responseCode = "404", description = "Product or actor with their ID are not found"),
            @ApiResponse(responseCode = "409", description = "An error occurred while cascading deletion of product and product-related applications")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable("id") UUID id,
                                              @RequestParam("actorId") UUID actorId) {
        managementService.deleteProduct(id, actorId);
        return ResponseEntity.noContent().build();
    }
}
