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
package com.mhs.onlinemarketingplatform.catalog;

import com.mhs.onlinemarketingplatform.catalog.event.AddCategoryEvent;
import com.mhs.onlinemarketingplatform.catalog.event.UpdateCategoryEvent;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * @author Milad Haghighat Shahedi
 */
@Controller("categoryController")
@ResponseBody
class CategoryController {

    private final CategoryService categoryService;

    CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @PostMapping("/api/categories")
    ResponseEntity<CategoryResponse> add(@RequestBody AddCategoryRequest addCategoryRequest) {
        return ResponseEntity.ok(this.categoryService.add(addCategoryRequest));
    }

    @PutMapping("/api/categories")
    ResponseEntity<CategoryResponse> update(@RequestBody UpdateCategoryRequest updateCategoryRequest) {
        return ResponseEntity.ok(this.categoryService.update(updateCategoryRequest));
    }

    @GetMapping("/api/categories/{id}")
    ResponseEntity<CategoryResponse> findById(@PathVariable("id") String id) {
        return ResponseEntity.ok(this.categoryService.findById(UUID.fromString(id)));
    }

    @GetMapping(value = "/api/categories", params = "name")
    ResponseEntity<CategoryResponse> findByName(@RequestParam("name") String name) {
        return ResponseEntity.ok(this.categoryService.findByName(name));
    }

    @GetMapping(value = "/api/categories", params = "slug")
    ResponseEntity<CategoryResponse> findBySlug(@RequestParam("slug") String slug) {
        return ResponseEntity.ok(this.categoryService.findBySlug(slug));
    }

    @PutMapping("/api/categories/{id}/activate")
    ResponseEntity<CategoryResponse> active(@PathVariable("id") String id) {
        return ResponseEntity.ok(this.categoryService.activate(UUID.fromString(id)));
    }

    @PutMapping("/api/categories/{id}/deactivate")
     ResponseEntity<CategoryResponse> inactive(@PathVariable("id") String id) {
        return ResponseEntity.ok(this.categoryService.deactivate(UUID.fromString(id)));
    }

    @GetMapping("/api/categories")
    CategoryPagedResponse<CategoryResponse> findAll(@PageableDefault(size = 20) Pageable pageable) {
        return this.categoryService.findAll(pageable);
    }

    @DeleteMapping("/api/categories/{id}")
    ResponseEntity<?> deleteById(@PathVariable("id") String id) {
        this.categoryService.delete(UUID.fromString(id));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/categories/{parentId}/sub-category")
    ResponseEntity<CategoryResponse> addSubCategory(@RequestBody AddChildCategoryRequest addChildCategoryRequest,@PathVariable("parentId") String parentId) {
        return ResponseEntity.ok(this.categoryService.addChildCategory(addChildCategoryRequest,UUID.fromString(parentId)));
    }

}

@Service("categoryService")
@Transactional
class CategoryService implements CategoryApi {

    private final CategoryRepository categoryRepository;
    private final CatalogService catalogService;
    private final CategoryMapper mapper;
    private final ApplicationEventPublisher publisher;

    public CategoryService(CategoryRepository categoryRepository,
                           CatalogService catalogService,
                           CategoryMapper mapper,
                           ApplicationEventPublisher publisher) {
        this.categoryRepository = categoryRepository;
        this.catalogService = catalogService;
        this.mapper = mapper;
        this.publisher = publisher;
    }

    CategoryResponse add(AddCategoryRequest addCategoryRequest) {
        UUID catalogId = UUID.fromString(addCategoryRequest.catalogId());

        if(!this.catalogService.existsById(catalogId)) {
            throw new CatalogNotFoundException("Catalog with id " + catalogId + " not found");
        }

        boolean existsByName = this.categoryRepository.existsByName(addCategoryRequest.name());
        boolean existsBySlug = this.categoryRepository.existsBySlug(addCategoryRequest.slug());
        if(existsByName || existsBySlug) {
            throw new CategoryAlreadyExistsException("Category with duplicate name or slug " + addCategoryRequest.name() + " " + addCategoryRequest.slug() + " already exists");
        }

        Category mappedCategory = this.mapper.mapAddRequestToCategory(addCategoryRequest);

        Category storedCategory = this.categoryRepository.save(mappedCategory);
        this.publisher.publishEvent(new AddCategoryEvent(storedCategory.id()));

        return this.mapper.mapCategoryToResponse(this.categoryRepository.findById(storedCategory.id()).orElseThrow());
    }

    CategoryResponse update(UpdateCategoryRequest updateCategoryRequest) {
        UUID catalogId = UUID.fromString(updateCategoryRequest.catalogId());
        UUID id = UUID.fromString(updateCategoryRequest.id());

        if(!this.catalogService.existsById(catalogId)) {
            throw new CatalogNotFoundException("Catalog with id " + catalogId + " not found");
        }

        boolean existsByName = this.categoryRepository.existsByName(updateCategoryRequest.name());
        boolean existsBySlug = this.categoryRepository.existsBySlug(updateCategoryRequest.slug());
        if(existsByName || existsBySlug) {
            throw new CategoryAlreadyExistsException("Category with duplicate name or slug " + updateCategoryRequest.name() + " " + updateCategoryRequest.slug() + " already exists");
        }

        Category existingCategory = this.categoryRepository.findById(id)
                .orElseThrow(() -> new CategoryNotFoundException("Category with id " + id + " not found"));

        if(!existingCategory.catalogId().equals(catalogId)){
            throw new CategoryNotBelongToCatalog("Category with id " + id + " does not belong to this catalog");
        }

        Category mappedCategory = this.mapper.mapUpdateRequestToCategory(updateCategoryRequest,existingCategory);
        Category storedCategory = this.categoryRepository.save(mappedCategory);
        this.publisher.publishEvent(new AddCategoryEvent(storedCategory.id()));
        return this.mapper.mapCategoryToResponse(this.categoryRepository.findById(storedCategory.id()).orElseThrow());
    }

    CategoryResponse findById(UUID categoryId) {
        Category category = this.categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CategoryNotFoundException("Category with id " + categoryId + " not found"));
        return this.mapper.mapCategoryToResponse(category);
    }

    CategoryResponse findByName(String name) {
        Category category = this.categoryRepository.findByName(name)
                .orElseThrow(() -> new CategoryNotFoundException("Category with name " + name + " not found"));
        return this.mapper.mapCategoryToResponse(category);
    }

    CategoryResponse findBySlug(String slug) {
        Category category = this.categoryRepository.findBySlug(slug)
                .orElseThrow(() -> new CategoryNotFoundException("Category with slug " + slug + " not found"));
        return this.mapper.mapCategoryToResponse(category);
    }

    CategoryResponse activate(UUID categoryId) {
        Category exsitingCategory = this.categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CategoryNotFoundException("Category " + "with id " + categoryId + " not found"));

        if (! exsitingCategory.categoryStatus().status.trim().equals("ACTIVE")) {
            Category updatedCategory = Category.withCategoryStatusActivated(exsitingCategory);
            Category storedCategory = this.categoryRepository.save(updatedCategory);
            this.publisher.publishEvent(new UpdateCategoryEvent(storedCategory.id()));
            return this.mapper.mapCategoryToResponse(storedCategory);
        }

        throw new CategoryAlreadyDeactivatedException("Category with name " + exsitingCategory.name() + " is already disabled");
    }

    CategoryResponse deactivate(UUID categoryId) {
        Category exsitingCategory = this.categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CategoryNotFoundException("Category " + "with id " + categoryId + " not found"));

        if (! exsitingCategory.categoryStatus().status.trim().equals("INACTIVE")) {
            Category updatedCategory = Category.withCategoryStatusDeactivated(exsitingCategory);
            Category storedCategory = this.categoryRepository.save(updatedCategory);
            this.publisher.publishEvent(new UpdateCategoryEvent(storedCategory.id()));
            return this.mapper.mapCategoryToResponse(storedCategory);
        }

        throw new CategoryIdAlreadyActiveException("Category with name " + exsitingCategory.name() + " is already activated");
    }

    CategoryPagedResponse<CategoryResponse> findAll(Pageable pageable) {
        Page<Category> categories = this.categoryRepository.findAll(pageable);
        return this.mapper.mapCategoryToPagedResponse(categories);
    }

    void delete(UUID categoryId) {
        Category category = this.categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CategoryNotFoundException("Category with id " + categoryId + " not found"));

        this.categoryRepository.delete(category);
    }

    CategoryResponse addChildCategory(AddChildCategoryRequest addChildCategoryRequest, UUID parentId) {
        UUID catalogId = UUID.fromString(addChildCategoryRequest.catalogId());

        if(!this.catalogService.existsById(catalogId)) {
            throw new CatalogNotFoundException("Catalog with id " + catalogId + " not found");
        }

        boolean existsByName = this.categoryRepository.existsByName(addChildCategoryRequest.name());
        boolean existsBySlug = this.categoryRepository.existsBySlug(addChildCategoryRequest.slug());
        if(existsByName || existsBySlug) {
            throw new CategoryAlreadyExistsException("Category with duplicate name or slug " + addChildCategoryRequest.name() + " " + addChildCategoryRequest.slug() + " already exists");
        }

        Category existingCategory = this.categoryRepository.findById(parentId)
                .orElseThrow(() -> new CategoryNotFoundException("Category with id " + parentId + " not found"));

        if(!existingCategory.catalogId().equals(catalogId)){
            throw new CategoryNotBelongToCatalog("Category with id " + parentId + " does not belong to this catalog");
        }

        Category mappedCategory = this.mapper.mapAddChildRequestYoCategory(addChildCategoryRequest,parentId);

        Category storedChildCategory = this.categoryRepository.save(mappedCategory);
        return this.mapper.mapCategoryToResponse(storedChildCategory);
    }

    public boolean existsById(UUID categoryId){
        return this.categoryRepository.existsById(categoryId);
    }
}

@Repository("categoryRepository")
interface CategoryRepository extends ListCrudRepository<Category, UUID> {

    Optional<Category> findById(UUID id);

    Optional<Category> findByName(String name);

    Optional<Category> findBySlug(String slug);

    Page<Category> findAll(Pageable pageable);

    boolean existsByName(@Param("name") String name);

    boolean existsBySlug(@Param("slug") String slug);

    boolean existsById(@Param("id") UUID id);

}

@Table("categories")
record Category(
        @Id UUID id,
        @Version Integer version,
        String name,
        String description,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        CategoryStatus categoryStatus,
        String slug,
        UUID categoryId,
        UUID catalogId) {

    static Category withCategoryStatusActivated(Category category) {
        return new Category(
                category.id,
                category.version,
                category.name,
                category.description,
                category.createdAt,
                LocalDateTime.now(),
                CategoryStatus.ACTIVE,
                category.slug,
                category.categoryId,
                category.catalogId);
    }

    static Category withCategoryStatusDeactivated(Category category) {
        return new Category(
                category.id,
                category.version,
                category.name,
                category.description,
                category.updatedAt,
                LocalDateTime.now(),
                CategoryStatus.INACTIVE,
                category.slug,
                category.categoryId,
                category.catalogId);
    }
}

enum CategoryStatus {

    ACTIVE("ACTIVE"), INACTIVE("INACTIVE");

    final String status;

    CategoryStatus(String status) {
        this.status = status;
    }
}

record AddCategoryRequest(
        @NotNull String name,
        @NotBlank String description,
        @NotNull String slug,
        @NotNull String catalogId) {}

record AddChildCategoryRequest(
        @NotNull String name,
        @NotBlank String description,
        @NotNull String slug,
        @NotNull String catalogId) {}

record UpdateCategoryRequest(
        @NotNull String id,
        @NotNull String name,
        @NotBlank String description,
        @NotNull String slug,
        @NotNull String catalogId) {}

record CategoryResponse(
        String id,
        String name,
        String description,
        String createdAt,
        String updatedAt,
        String status,
        String slug,
        String categoryId,
        String catalogId) {}

record CategoryPagedResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {}

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
interface CategoryMapper {

    @Mapping(target = "id", expression = "java(java.util.UUID.randomUUID())")
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "categoryStatus", constant = "INACTIVE")
    @Mapping(target = "createdAt", expression = "java(java.time.LocalDateTime.now())")
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "categoryId", ignore = true)
    Category mapAddRequestToCategory(AddCategoryRequest addCategoryRequest);

    @Mapping(target = "id", expression = "java(java.util.UUID.randomUUID())")
    @Mapping(target = "createdAt", expression = "java(java.time.LocalDateTime.now())")
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "categoryStatus", constant = "INACTIVE")
    @Mapping(target = "categoryId", source = "parentId")
    Category mapAddChildRequestYoCategory(AddChildCategoryRequest AddChildCategoryRequest,UUID parentId);

    default Category mapUpdateRequestToCategory(UpdateCategoryRequest request, Category category) {
        return new Category(
                category.id(),
                category.version(),
                request.name() != null ? request.name() :category.name(),
                request.description() != null ? request.description() :category.description(),
                category.createdAt(),
                LocalDateTime.now(),
                CategoryStatus.INACTIVE,
                request.slug() != null ? request.slug() :category.slug(),
                category.categoryId(),
                category.catalogId());
    }

    @Mapping(target = "status", source = "categoryStatus")
    CategoryResponse mapCategoryToResponse(Category category);

    default CategoryPagedResponse<CategoryResponse> mapCategoryToPagedResponse(Page<Category> page) {
        return new CategoryPagedResponse<>(
                page.getContent().stream().map(this::mapCategoryToResponse).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    default String map(CategoryStatus status) {
        return status != null ? status.name() : null;
    }

}

class CategoryNotFoundException extends RuntimeException {
    CategoryNotFoundException(String message) {
        super(message);
    }
}

class CategoryIdAlreadyActiveException extends RuntimeException {
    CategoryIdAlreadyActiveException(String message) {
        super(message);
    }
}

class CategoryAlreadyDeactivatedException extends RuntimeException {
    CategoryAlreadyDeactivatedException(String message) {
        super(message);
    }
}

class CategoryAlreadyExistsException extends RuntimeException {
    CategoryAlreadyExistsException(String message) {
        super(message);
    }
}

class CategoryNotBelongToCatalog extends RuntimeException {
    public CategoryNotBelongToCatalog(String message) {
        super(message);
    }
}

// controller // service // repository // model // enum // dto // mapper // exception


