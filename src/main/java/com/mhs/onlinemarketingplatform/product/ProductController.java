/*
 * Copyright 2025-2026 the original author.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mhs.onlinemarketingplatform.product;

import com.mhs.onlinemarketingplatform.catalog.CategoryApi;
import com.mhs.onlinemarketingplatform.product.event.AddProductEvent;
import com.mhs.onlinemarketingplatform.product.event.UpdateProductEvent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * @author Milad Haghighat Shahedi
 */
@Controller
@ResponseBody
class ProductController {

	private final ProductService productService;
	private final static String OWNER = "26712732-813a-4b3b-8ecf-54e47e428160";

	ProductController(ProductService productService) {
		this.productService = productService;
	}

	@PostMapping("/api/me/products/{category}")
	ResponseEntity<ProductResponse> addByOwner(@RequestBody AddProductRequest addProductRequest, @PathVariable("category") String category) {
		return ResponseEntity.ok(this.productService.addByOwner(addProductRequest, UUID.fromString(OWNER),UUID.fromString(category)));
	}

	@GetMapping("/api/me/products")
	ProductPagedResponse<ProductResponse> findAllByOwner(@PageableDefault(size = 20) Pageable pageable) {
		return this.productService.findAllByOwner(UUID.fromString(OWNER), pageable);
	}

	@GetMapping(value = "/api/me/products", params = "name")
	ResponseEntity<ProductResponse> findByNameAndOwner(@RequestParam("name") String name) {
		return ResponseEntity.ok(this.productService.findByNameAndOwner(name,UUID.fromString(OWNER)));
	}

	@GetMapping(value = "/api/me/products", params = "status")
	ProductPagedResponse<ProductResponse> findAllByStatusAndOwner(@RequestParam("status") String status, @PageableDefault(size = 20) Pageable pageable) {
		return this.productService.findAllByProductStatusAndOwner(
				ProductStatus.valueOf(status.trim().toUpperCase()),
				UUID.fromString(OWNER),pageable);
	}

	@GetMapping("/api/me/products/{id}")
	ResponseEntity<ProductResponse> findByIdAndOwner(@RequestBody @PathVariable("id") String id) {
		return ResponseEntity.ok(this.productService.findByIdAndOwner(UUID.fromString(id),UUID.fromString(OWNER)));
	}

	@PutMapping("/api/me/products/{id}")
	ResponseEntity<ProductResponse> updateByOwner(@RequestBody UpdateProductRequest updateProductRequest, @PathVariable("id") String id) {
		return ResponseEntity.ok(this.productService.updateByIdAndOwner(updateProductRequest, UUID.fromString(id), UUID.fromString(OWNER)));
	}

	@PutMapping("/api/me/products/{id}/activate")
	ResponseEntity<ProductResponse> activateByIdAndOwner(@PathVariable("id") String id) {
		return ResponseEntity.ok(this.productService.activateByOwner(UUID.fromString(id), UUID.fromString(OWNER)));
	}

	@PutMapping("/api/me/products/{id}/deactivate")
	ResponseEntity<ProductResponse> deactivateByIdAndOwner(@PathVariable("id") String id) {
		return ResponseEntity.ok(this.productService.deactivateByOwner(UUID.fromString(id), UUID.fromString(OWNER)));
	}

}

@Service
@Transactional
class ProductService {

	private final ProductRepository productRepository;
	private final CategoryApi categoryApi;
	private final ProductMapper mapper;
	private final ApplicationEventPublisher publisher;

	ProductService(ProductRepository productRepository, CategoryApi categoryApi, ProductMapper mapper, ApplicationEventPublisher publisher) {
		this.productRepository = productRepository;
		this.categoryApi = categoryApi;
		this.mapper = mapper;
		this.publisher = publisher;
	}

	ProductResponse addByOwner(AddProductRequest addProductRequest, UUID owner, UUID categoryId) {
		if(!this.categoryApi.existsById(categoryId)){
			throw new CategoryNotFoundException("Category with id " + categoryId + "not found");
		}

		Product mappedProduct = this.mapper.mapAddRequestToProduct(addProductRequest,owner);
		Product storedProduct = this.productRepository.save(mappedProduct);
		this.productRepository.linkProductToCategory(storedProduct.id(),categoryId);

		this.publisher.publishEvent(new AddProductEvent(storedProduct.id()));
		return this.mapper.mapProductToResponse(storedProduct);
	}

	ProductResponse activateByOwner(UUID id, UUID owner) {
		Product exisitngProduct = this.productRepository.findByIdAndCredential(id, owner)
				.orElseThrow(() -> new ProductNotFoundException("Product with id " + id + " not found"));

		if (!exisitngProduct.productStatus().status.trim().equals("ACTIVE")) {
			Product updatingProduct = Product.withProductStatusActivated(exisitngProduct, owner);
			Product storedProduct = this.productRepository.save(updatingProduct);
			this.publisher.publishEvent(new UpdateProductEvent(storedProduct.id()));
			return this.mapper.mapProductToResponse(storedProduct);
		}

		throw new ProductAlreadyEnabledException("Product with name " + exisitngProduct.name() + " is already " + "enabled");
	}

	ProductResponse deactivateByOwner(UUID id, UUID owner) {
		Product exisitngProduct = this.productRepository.findByIdAndCredential(id, owner)
				.orElseThrow(() -> new ProductNotFoundException("Product with id " + id + " not found"));

		if (! exisitngProduct.productStatus().status.trim().equals("INACTIVE")) {
			Product updatingProduct = Product.withProductStatusDeactivated(exisitngProduct, owner);
			Product storedProduct = this.productRepository.save(updatingProduct);
			this.publisher.publishEvent(new UpdateProductEvent(storedProduct.id()));
			return this.mapper.mapProductToResponse(storedProduct);
		}

		throw new ProductAlreadyDisabledException("Product with name " + exisitngProduct.name() + " is already " + "disabled");
	}

	ProductResponse updateByIdAndOwner(UpdateProductRequest updateProductRequest, UUID id, UUID owner) {
		Product exisitngProduct = this.productRepository.findByIdAndCredential(id, owner)
				.orElseThrow(() -> new ProductNotFoundException("Product not found with id: " + id));

		Product mappedProduct = this.mapper.mapUpdateRequestToProduct(updateProductRequest, exisitngProduct);
		Product storedProduct = this.productRepository.save(mappedProduct);
		this.publisher.publishEvent(new UpdateProductEvent(storedProduct.id()));
		return this.mapper.mapProductToResponse(storedProduct);
	}

	ProductResponse findByNameAndOwner(String name, UUID owner) {
		Product product = this.productRepository.findByNameAndCredential(name, owner)
				.orElseThrow(() -> new ProductNotFoundException("Product not found: " + name));
		return this.mapper.mapProductToResponse(product);
	}

	ProductResponse findByIdAndOwner(UUID id, UUID owner) {
		Product product = this.productRepository.findByIdAndCredential(id, owner)
				.orElseThrow(() -> new ProductNotFoundException("Product not found with id: " + id));
		return this.mapper.mapProductToResponse(product);
	}

	ProductPagedResponse<ProductResponse> findAllByOwner(UUID owner, Pageable pageable) {
		Page<Product> products = this.productRepository.findAllByCredential(owner, pageable);
		return this.mapper.mapProductToPagedResponse(products);
	}

	ProductPagedResponse<ProductResponse> findAllByProductStatusAndOwner(ProductStatus status, UUID owner, Pageable pageable) {
		Page<Product> products = this.productRepository.findAllByProductStatusAndCredential(status, owner, pageable);
		return this.mapper.mapProductToPagedResponse(products);
	}

}

@Repository
interface ProductRepository extends ListCrudRepository<Product, UUID> {

	Optional<Product> findByIdAndCredential(UUID id, UUID credential);

	Optional<Product> findByNameAndCredential(String name, UUID credential);

	Page<Product> findAllByCredential(UUID credential, Pageable pageable);

	Page<Product> findAllByProductStatusAndCredential(
			@Param("productStatus") ProductStatus productStatus,
			@Param("credential") UUID credential,
			Pageable pageable);

	@Modifying
	@Query("INSERT INTO product_category(product,category) VALUES (:product,:category)")
	void linkProductToCategory(@Param("product")UUID product,@Param("category") UUID category);

	@Modifying
	@Query("DELETE FROM product_category WHERE product= :product")
	void unlinkAllCategories(@Param("product") UUID product);

}

@Table("products")
record Product(
		@Id UUID id,
		@Version Integer version,
		String name,
		String description,
		LocalDateTime insertedAt,
		LocalDateTime updatedAt,
		BigDecimal price,
		ProductStatus productStatus,
		UUID credential) {

	static Product withProductStatusActivated(Product product, UUID owner) {
		return new Product(
				product.id,
				product.version(),
				product.name(),
				product.description(),
				product.insertedAt,
				LocalDateTime.now(),
				product.price,
				ProductStatus.ACTIVE,
				owner
		);
	}

	static Product withProductStatusDeactivated(Product product, UUID owner) {
		return new Product(
				product.id,
				product.version(),
				product.name(),
				product.description(),
				product.insertedAt,
				LocalDateTime.now(),
				product.price,
				ProductStatus.INACTIVE,
				owner
		);
	}

}

@Table("product_category")
record ProductCategory(UUID product, UUID catalog) {}

enum ProductStatus {

	ACTIVE("ACTIVE"), INACTIVE("INACTIVE");

	final String status;

	ProductStatus(String status) {
		this.status = status;
	}

}

record AddProductRequest(
		@NotNull String name,
		@NotBlank String description,
		@NotNull String price) {}

record UpdateProductRequest(
		@NotNull String name,
		@NotBlank String description,
		@NotNull String price) {}

record ProductResponse(
		String id,
		String name,
		String description,
		LocalDateTime insertedAt,
		LocalDateTime updateAt,
		String price,
		String productStatus,
		String credential) {}

record ProductPagedResponse<T>(
		List<T> content,
		int page,
		int size,
		long totalElements,
		int totalPages) {}

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
interface ProductMapper {

	@Mapping(target = "id", expression = "java(java.util.UUID.randomUUID())")
	@Mapping(target = "version", ignore = true)
	@Mapping(target = "insertedAt", expression = "java(java.time.LocalDateTime.now())")
	@Mapping(target = "updatedAt", ignore = true)
	@Mapping(target = "productStatus", constant = "INACTIVE")
	@Mapping(target = "credential", source = "owner")
	Product mapAddRequestToProduct(AddProductRequest addProductRequest,UUID owner);

	default Product mapUpdateRequestToProduct(UpdateProductRequest updateProductRequest, Product product){
		return new Product(
				product.id(),
				product.version(),
				updateProductRequest.name(),
				updateProductRequest.description(),
				product.insertedAt(),
				LocalDateTime.now(),
				new BigDecimal(updateProductRequest.price()),
				ProductStatus.INACTIVE,
				product.credential());
	}

	@Mapping(target = "productStatus", source = "productStatus")
	ProductResponse mapProductToResponse(Product product);

	default ProductPagedResponse<ProductResponse> mapProductToPagedResponse(Page<Product> page) {
		return new ProductPagedResponse<>(page.getContent().stream().map(this::mapProductToResponse).toList(), page.getNumber(),
				page.getSize(), page.getTotalElements(), page.getTotalPages());
	}

	default String map(ProductStatus status) {
		return status != null ? status.name() : null;
	}

}

class ProductAlreadyDisabledException extends RuntimeException {
	ProductAlreadyDisabledException(String message) {
		super(message);
	}
}

class ProductAlreadyEnabledException extends RuntimeException {
	ProductAlreadyEnabledException(String message) {
		super(message);
	}
}

class ProductAlreadyExistsException extends RuntimeException {
	ProductAlreadyExistsException(String message) {
		super(message);
	}
}

class ProductNotFoundException extends RuntimeException {
	ProductNotFoundException(String message) {
		super(message);
	}
}

class CategoryNotFoundException extends RuntimeException {
	CategoryNotFoundException(String message) {
		super(message);
	}
}

// controller // service // repository // model // enum // dto // mapper // exception

