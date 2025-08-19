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

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.amqp.core.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
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
	private final static String CREDENTIAL = "26712732-813a-4b3b-8ecf-54e47e428160";

	public ProductController(ProductService productService) {
		this.productService = productService;
	}

	@PostMapping("/api/products")
	ResponseEntity<ProductResponse> add(@RequestBody AddProductRequest addProductRequest) {
		return ResponseEntity.ok(this.productService.add(addProductRequest,UUID.fromString(CREDENTIAL)));
	}

	@GetMapping("/api/products")
	ResponseEntity<List<ProductResponse>> findAll() {
		return ResponseEntity.ok(this.productService.findAllByCredential(UUID.fromString(CREDENTIAL)));
	}

	@GetMapping(value = "/api/products",params = "name")
	ResponseEntity<ProductResponse> findByName(@RequestParam("name") String name) {
		return ResponseEntity.ok(this.productService.findByName(name));
	}

	@GetMapping(value = "/api/products",params = "status")
	public ResponseEntity<List<ProductResponse>> findAllByStatus(@RequestParam("status") String status) {
		return ResponseEntity.ok(this.productService.findAllByCredentialAndStatus(UUID.fromString(CREDENTIAL), ProductStatus.valueOf(status.trim().toUpperCase())));
	}

	@GetMapping("/api/products/{id}")
	ResponseEntity<ProductResponse> findById(@RequestBody @PathVariable("id") String id) {
		return ResponseEntity.ok(this.productService.findById(UUID.fromString(id)));
	}

	@PutMapping("/api/products/{id}")
	ResponseEntity<ProductResponse> update(@RequestBody UpdateProductRequest updateProductRequest, @PathVariable("id") String id) {
		return ResponseEntity.ok(this.productService.update(updateProductRequest, UUID.fromString(id), UUID.fromString(CREDENTIAL)));
	}

	@PutMapping("/api/products/{id}/enable")
	public ResponseEntity<ProductResponse> enable(@PathVariable("id") String id) {
		return ResponseEntity.ok(this.productService.enable(UUID.fromString(id),UUID.fromString(CREDENTIAL)));
	}

	@PutMapping("/api/products/{id}/disable")
	public ResponseEntity<ProductResponse> disable(@PathVariable("id") String id) {
		return ResponseEntity.ok(this.productService.disable(UUID.fromString(id),UUID.fromString(CREDENTIAL)));
	}

}

@Service
@Transactional
class ProductService {

	private final ProductRepository productRepository;
	private final ApplicationEventPublisher publisher;

	public ProductService(ProductRepository productRepository, ApplicationEventPublisher publisher) {
		this.productRepository = productRepository;
		this.publisher = publisher;
	}

	ProductResponse add(AddProductRequest addProductRequest,UUID credential) {
		Product product = Product.createNewProduct(addProductRequest,credential);
		Product storedProduct = productRepository.save(product);
		this.publisher.publishEvent(new ProductAddEvent(storedProduct.id()));
		return ProductResponse.from(storedProduct, true);

	}

	ProductResponse enable(UUID productId, UUID credential) {
		Product exisitngProduct = productRepository.findByIdAndCredential(productId, credential)
				.orElseThrow(() -> new ProductNotFoundException("Product with id " + productId + " not found"));

		if(!exisitngProduct.productStatus().status.trim().equals("ENABLED")){
			Product updatingProduct = Product.withProductStatus(exisitngProduct, credential);
			Product storedProduct = productRepository.save(updatingProduct);
			return ProductResponse.from(storedProduct,true);
		}

		throw new ProductAlreadyEnabledException("Product with name " + exisitngProduct.name() + " is already enabled");
	}

	ProductResponse disable(UUID productId, UUID credential) {
		Product exisitngProduct = productRepository.findByIdAndCredential(productId, credential)
				.orElseThrow(() -> new ProductNotFoundException("Product with id " + productId + " not found"));

		if(!exisitngProduct.productStatus().status.trim().equals("DISABLED")){
			Product updatingProduct = Product.withProductStatus(exisitngProduct, credential);
			Product storedProduct = productRepository.save(updatingProduct);
			return ProductResponse.from(storedProduct,true);
		}

		throw new ProductAlreadyDisabledException("Product with name " + exisitngProduct.name() + " is already disabled");
	}

	ProductResponse update(UpdateProductRequest updateProductRequest, UUID productId, UUID credential) {

		Product product = productRepository.findByIdAndCredential(productId, credential)
				.orElseThrow(() -> new ProductNotFoundException("Product not found with id: " + productId));

		Product newProduct = new Product(
				product.id(),
				product.version(),
				updateProductRequest.name(),
				updateProductRequest.description(),
				product.insertDate(),
				LocalDateTime.now(),
				new BigDecimal(updateProductRequest.price().trim()),
				ProductStatus.DISABLED,
				product.credential());

		Product storedProduct = productRepository.save(newProduct);
		this.publisher.publishEvent(new ProductUpdateEvent(storedProduct.id()));
		return ProductResponse.from(storedProduct, true);
	}

	ProductResponse findByName(String name) {
		Product product = productRepository.findByName(name).orElseThrow(() -> new ProductNotFoundException("Product not found: " + name));
		return ProductResponse.from(product, true);
	}

	ProductResponse findById(UUID productId) {
		Product product = productRepository.findById(productId)
				.orElseThrow(() -> new ProductNotFoundException("Product not found with id: " + productId));
		return ProductResponse.from(product, true);
	}

	List<ProductResponse> findAllByCredential(UUID credential) {
		List<Product> products = productRepository.findByCredential(credential);
		return products.stream().map(product -> ProductResponse.from(product, true)).toList();
	}

	List<ProductResponse> findAllByCredentialAndStatus(UUID credential,ProductStatus status) {
		List<Product> products = productRepository.findByCredentialAndProductStatus(credential,status);
		return products.stream().map(product -> ProductResponse.from(product, true)).toList();
	}

}

@Repository
interface ProductRepository extends CrudRepository<Product, UUID> {
	Optional<Product> findById(UUID id);
	Optional<Product> findByName(String name);
	Optional<Product> findByIdAndCredential(UUID id, UUID credential);
	List<Product> findByCredential(UUID credential);
	List<Product> findByCredentialAndProductStatus( @Param("credential") UUID credential,@Param("productStatus") ProductStatus productStatus);
}

@Table("products")
record Product( @Id UUID id, @Version Integer version, String name, String description, LocalDateTime insertDate, LocalDateTime updateDate, BigDecimal price, ProductStatus productStatus, UUID credential) {

	public static Product createNewProduct(AddProductRequest addProductRequest,UUID credential) {
		return new Product(
				UUID.randomUUID(),
				null,
				addProductRequest.name(),
				addProductRequest.description(),
				LocalDateTime.now(),
				null,
				new BigDecimal(addProductRequest.price().trim()),
				ProductStatus.DISABLED,
				credential);
	}

	public static Product withProductStatus(Product product,UUID credential){
		return new Product(
				product.id,
				product.version(),
				product.name(),
				product.description(),
				product.insertDate,
				LocalDateTime.now(),
				product.price,
				ProductStatus.ENABLED,
				credential);
	}

}

enum ProductStatus {

	IN_STOCK("IN_STOCK"),
	OUT_OF_STUCK("OUT_OF_STUCK"),
	DISABLED("DISABLED"),
	ENABLED("ENABLED");

	final String status;

	ProductStatus(String status) {
		this.status = status;
	}

}

record AddProductRequest( @NotNull String name, @NotBlank String description, @NotNull String price) { }

record UpdateProductRequest( @NotNull String name, @NotBlank String description, @NotNull String price) { }

record ProductResponse(String id, String name, String description, LocalDateTime insertDate, LocalDateTime updateDate, String price, String productStatus, String credential) {

	public static ProductResponse from(Product product, boolean includeSensitive) {
		return new ProductResponse(
				includeSensitive ? product.id().toString() : null,
				product.name(),
				product.description(),
				product.insertDate(),
				product.updateDate(),
				String.valueOf(product.price()),
				product.productStatus().toString(),
				includeSensitive ? product.credential().toString() : null
		);
	}

}

class ProductAlreadyExistsException extends RuntimeException {
	public ProductAlreadyExistsException(String message) {
		super(message);
	}
}

class ProductNotFoundException extends RuntimeException {
	public ProductNotFoundException(String message) {
		super(message);
	}
}

class ProductAlreadyEnabledException extends RuntimeException {
	public ProductAlreadyEnabledException(String message) {
		super(message);
	}
}

class ProductAlreadyDisabledException extends RuntimeException {
	public ProductAlreadyDisabledException(String message) {
		super(message);
	}
}

// controller // service // repository // model // enum // dto // exception

@Configuration
class RabbitMqProductsIntegrationConfig {

	static final String PRODUCT_Q = "products";

	@Bean
	Binding productBinding(Queue productQueue, Exchange productExchange){
		return BindingBuilder.bind(productQueue).to(productExchange).with(PRODUCT_Q).noargs();
	}

	@Bean
	Exchange productExchange(){
		return ExchangeBuilder.directExchange(PRODUCT_Q).build();
	}

	@Bean
	Queue productQueue(){
		return QueueBuilder.durable(PRODUCT_Q).build();
	}
}


