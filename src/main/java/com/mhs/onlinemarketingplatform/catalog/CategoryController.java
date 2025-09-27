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
import org.mapstruct.*;
import org.mapstruct.Mapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

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
    ResponseEntity<CategoryResponse> addParent(@RequestBody AddParentRequest addParentRequest) {
        return ResponseEntity.ok(this.categoryService.addAncestor(addParentRequest));
    }

    @PostMapping("/api/categories/descendants")
    ResponseEntity<CategoryResponse> addChild(@RequestBody AddChildRequest addChildRequest) {
        return ResponseEntity.ok(this.categoryService.addDescendant(addChildRequest));
    }

    @PutMapping("/api/categories")
    ResponseEntity<CategoryResponse> update(@RequestBody UpdateParentRequest updateParentRequest) {
        return ResponseEntity.ok(this.categoryService.update(updateParentRequest));
    }

    @GetMapping("/api/categories/{id}")
    ResponseEntity<CategoryResponse> findById(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(this.categoryService.findById(id));
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
    ResponseEntity<CategoryResponse> activate(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(this.categoryService.activate(id));
    }

    @PutMapping("/api/categories/{id}/deactivate")
    ResponseEntity<CategoryResponse> deactivate(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(this.categoryService.deactivate(id));
    }

    @GetMapping(value = "/api/categories")
    CategoryPagedResponse<CategoryResponse> findAll(@PageableDefault(size = 10) Pageable pageable) {
        return this.categoryService.findAll(pageable);
    }

    @GetMapping(value = "/api/categories/root")
    Page<RootCategoryResponse> findAllrootCategories(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return this.categoryService.findAllRootCategoried(page,size);
    }

    @GetMapping("/api/categories/ancestors/{id}")
    List<Category> findAllAncestors(@PathVariable("id") UUID id) {
        return this.categoryService.findAncestors(id);
    }

    @GetMapping("/api/categories/descendants/{id}")
    List<Category> findAllDescendants(@PathVariable("id") UUID id) {
        return this.categoryService.findDescendants(id);
    }

    @PutMapping("/api/categories/{id}")
    ResponseEntity<?> activateOrDeactivateParentAndChildrenWithoutVersioning(
            @PathVariable("id") UUID id,
            @RequestBody UpdateCategoryStatusRequest request) {
        this.categoryService.activateOrDeactivateParentAndChildrenWithoutVersioning(id,request.categoryStatus());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/api/categories/{id}/versioned")
    ResponseEntity<?> activateOrDeactivateParentAndChildrenWithVersioning(
            @PathVariable("id") UUID id,
            @RequestBody UpdateCategoryStatusRequest request) {

        this.categoryService.activateOrDeactivateParentAndChildrenWithVersioning(id,request.categoryStatus());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/categories/{id}")
    ResponseEntity<?> deleteCategory(@PathVariable("id") UUID id) {
        this.categoryService.deleteById(id);
        return ResponseEntity.noContent().build();

    }

    @GetMapping("/api/categories/root/count")
    long countRootCategories() {
        return this.categoryService.countRootCategories();
    }

}

@Service("categoryService")
@Transactional
class CategoryService implements CategoryApi {

    private static final Logger log = LoggerFactory.getLogger(CategoryService.class);

    private final CategoryRepository categoryRepository;
    private final CategoryClosureRepository categoryClosureRepository;
    private final CatalogService catalogService;
    private final CategoryMapper mapper;
    private final ApplicationEventPublisher publisher;
    private final MessageSource messageSource;

    public CategoryService(
            CategoryRepository categoryRepository,
            CategoryClosureRepository categoryClosureRepository,
            CatalogService catalogService,
            CategoryMapper mapper,
            ApplicationEventPublisher publisher,
            MessageSource messageSource) {
        this.categoryRepository = categoryRepository;
        this.categoryClosureRepository = categoryClosureRepository;
        this.catalogService = catalogService;
        this.mapper = mapper;
        this.publisher = publisher;
        this.messageSource = messageSource;
    }

    CategoryResponse addAncestor(AddParentRequest addParentRequest) {
        UUID catalogId = UUID.fromString(addParentRequest.catalogId());

        if(!this.catalogService.existsById(catalogId)) {
            throw new CatalogNotFoundException(
                    messageSource.getMessage("error.catalog.catalog.with.id.not.found",
                            new Object[]{catalogId},
                            LocaleContextHolder.getLocale()),
                            CatalogErrorCode.CATALOG_NOT_FOUND);}

        validateDuplicatesByNameAndSlug(addParentRequest,AddParentRequest::name,AddParentRequest::slug);

        Category mappedCategory = this.mapper.mapAddParentToCategory(addParentRequest);

        Category storedCategory = this.categoryRepository.save(mappedCategory);
        this.categoryClosureRepository.insertSelf(storedCategory.id());

        this.publisher.publishEvent(new AddCategoryEvent(storedCategory.id()));
        return this.mapper.mapCategoryToResponse(this.categoryRepository.findById(storedCategory.id()).orElseThrow());
    }

    CategoryResponse addDescendant(AddChildRequest addChildRequest) {
        UUID catalogId = UUID.fromString(addChildRequest.catalogId());
        UUID categoryId = UUID.fromString(addChildRequest.parentId());

        if(!this.catalogService.existsById(catalogId)) {
            throw new CatalogNotFoundException(
                            messageSource.getMessage("error.catalog.catalog.with.id.not.found",
                                    new Object[]{catalogId},
                                    LocaleContextHolder.getLocale()),
                                    CatalogErrorCode.CATALOG_NOT_FOUND);}

        validateDuplicatesByNameAndSlug(addChildRequest,AddChildRequest::name,AddChildRequest::slug);

        Category existingCategory = this.categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CategoryNotFoundException(
                        messageSource.getMessage("error.category.category.with.id.not.found",
                                new Object[]{categoryId},
                                LocaleContextHolder.getLocale()),
                                CategoryErrorCode.CATEGORY_NOT_FOUND));

        if(!existingCategory.catalogId().equals(catalogId)){
            throw new CategoryNotBelongToCatalog(
                    messageSource.getMessage("error.category.category.not.belong.to.catalog",
                            new Object[]{categoryId},
                            LocaleContextHolder.getLocale()),
                            CategoryErrorCode.CATEGORY_NOT_BELONG_TO_CATALOG);}

        Category mappedCategory = this.mapper.mapAddChildToCategory(addChildRequest);

        Category storedChildCategory = this.categoryRepository.save(mappedCategory);
        this.categoryClosureRepository.insertSelf(storedChildCategory.id());
        this.categoryClosureRepository.insertClosure(categoryId,storedChildCategory.id());

        this.publisher.publishEvent(new AddCategoryEvent(storedChildCategory.id()));
        return this.mapper.mapCategoryToResponse(storedChildCategory);
    }

    CategoryResponse update(UpdateParentRequest updateParentRequest) {
        UUID catalogId = UUID.fromString(updateParentRequest.catalogId());
        UUID id = UUID.fromString(updateParentRequest.id());

        if(!this.catalogService.existsById(catalogId)) {
            throw new CatalogNotFoundException(
                    messageSource.getMessage("error.catalog.catalog.with.id.not.found",
                            new Object[]{catalogId},
                            LocaleContextHolder.getLocale()),
                            CatalogErrorCode.CATALOG_NOT_FOUND);}

        Category existingCategory = this.categoryRepository.findById(id)
                .orElseThrow(() -> new CategoryNotFoundException(
                        messageSource.getMessage("error.category.category.with.id.not.found",
                                                  new Object[]{id},
                                                  LocaleContextHolder.getLocale()),
                                                  CategoryErrorCode.CATEGORY_NOT_FOUND));

        if(!existingCategory.name().equals(updateParentRequest.name()) || !existingCategory.slug().equals(updateParentRequest.slug())) {
            boolean exists = this.categoryRepository.existsByNameOrSlugAndNotId(updateParentRequest.name(), updateParentRequest.slug(), id);
            if (exists) {
                throw new CategoryAlreadyExistsException(
                        messageSource.getMessage("error.category.category.with.duplicate.name.or.slug",
                                new Object[]{updateParentRequest.name(),
                                        updateParentRequest.slug()},
                                LocaleContextHolder.getLocale()),
                        CategoryErrorCode.CATEGORY_ALREADY_EXISTS);
            }
        }

        if(!existingCategory.catalogId().equals(catalogId)){
           throw new CategoryNotBelongToCatalog(
                    messageSource.getMessage("error.category.category.not.belong.to.catalog",
                            new Object[]{id},
                            LocaleContextHolder.getLocale()),
                            CategoryErrorCode.CATEGORY_NOT_BELONG_TO_CATALOG);}

        Category mappedCategory = this.mapper.mapUpdateToCategory(updateParentRequest,existingCategory);
        Category storedCategory = this.categoryRepository.save(mappedCategory);
        this.publisher.publishEvent(new UpdateCategoryEvent(storedCategory.id()));
        return this.mapper.mapCategoryToResponse(this.categoryRepository.findById(storedCategory.id()).orElseThrow());
    }

    CategoryResponse findById(UUID categoryId) {
        Category category = this.categoryRepository.findById(categoryId)
                .orElseThrow(() ->  new CategoryNotFoundException(
                        messageSource.getMessage("error.category.category.with.id.not.found",
                                new Object[]{categoryId},
                                LocaleContextHolder.getLocale()),
                                CategoryErrorCode.CATEGORY_NOT_FOUND));
        return this.mapper.mapCategoryToResponse(category);
    }

    CategoryResponse findByName(String name) {
        Category category = this.categoryRepository.findByName(name)
                .orElseThrow(() ->  new CategoryNotFoundException(
                        messageSource.getMessage("error.category.category.with.name.not.found",
                                new Object[]{name},
                                LocaleContextHolder.getLocale()),
                                CategoryErrorCode.CATEGORY_NOT_FOUND));
        return this.mapper.mapCategoryToResponse(category);
    }

    CategoryResponse findBySlug(String slug) {
        Category category = this.categoryRepository.findBySlug(slug)
                .orElseThrow(() ->  new CategoryNotFoundException(
                        messageSource.getMessage("error.category.category.with.slug.not.found",
                                new Object[]{slug},
                                LocaleContextHolder.getLocale()),
                                CategoryErrorCode.CATEGORY_NOT_FOUND));
        return this.mapper.mapCategoryToResponse(category);
    }

    CategoryResponse activate(UUID categoryId) {
        Category exsitingCategory = this.categoryRepository.findById(categoryId)
                .orElseThrow(() ->  new CategoryNotFoundException(
                        messageSource.getMessage("error.category.category.with.id.not.found",
                                new Object[]{categoryId},
                                LocaleContextHolder.getLocale()),
                                CategoryErrorCode.CATEGORY_NOT_FOUND));

        if(exsitingCategory.categoryStatus() == CategoryStatus.INACTIVE) {
            Category updatedCategory = Category.withCategoryStatusActivated(exsitingCategory);
            Category storedCategory = this.categoryRepository.save(updatedCategory);
            this.publisher.publishEvent(new UpdateCategoryEvent(storedCategory.id()));
            return this.mapper.mapCategoryToResponse(storedCategory);
        }

        throw new CategoryAlreadyActivatedException(
                messageSource.getMessage("error.category.category.already.activated",
                        new Object[]{categoryId},
                        LocaleContextHolder.getLocale()),
                        CategoryErrorCode.CATEGORY_ALREADY_ACTIVATED);
    }

    CategoryResponse deactivate(UUID categoryId) {
        Category exsitingCategory = this.categoryRepository.findById(categoryId)
                .orElseThrow(() ->  new CategoryNotFoundException(
                        messageSource.getMessage("error.category.category.with.id.not.found",
                        new Object[]{categoryId},
                        LocaleContextHolder.getLocale()),
                        CategoryErrorCode.CATEGORY_NOT_FOUND));

        if(exsitingCategory.categoryStatus() == CategoryStatus.ACTIVE) {
            Category updatedCategory = Category.withCategoryStatusDeactivated(exsitingCategory);
            Category storedCategory = this.categoryRepository.save(updatedCategory);
            this.publisher.publishEvent(new UpdateCategoryEvent(storedCategory.id()));
            return this.mapper.mapCategoryToResponse(storedCategory);
        }
        throw new CategoryAlreadyDeactivatedException(
                messageSource.getMessage("error.category.category.already.deactivated",
                        new Object[]{categoryId},
                        LocaleContextHolder.getLocale()),
                        CategoryErrorCode.CATEGORY_ALREADY_DEACTIVATED);
    }

    CategoryPagedResponse<CategoryResponse> findAll(Pageable pageable) {
        Page<Category> categories = this.categoryRepository.findAll(pageable);
        return this.mapper.mapCategoryToPagedResponse(categories);
    }

    Page<RootCategoryResponse> findAllRootCategoried(int page,int size) {
        int offset = page * size;

        List<Category> categories = this.categoryRepository.findAllRootCategories(size, offset);

        if (categories.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(), PageRequest.of(page, size), 0);
        }

        long total = this.categoryRepository.countRootCategories();

        List<RootCategoryResponse> rootCategoryResponseList = this.mapper.toRootCategoryResponseList(categories);

        return new PageImpl<>(
                rootCategoryResponseList,
                PageRequest.of(page,size),
                total
        );
    }

    List<Category> findDescendants(UUID id) {
        findById(id);
        List<Category> descendants = this.categoryRepository.findDescendants(id);
        if(descendants.isEmpty()){
            log.info("Category.jsx {} has no descendants", id);
        }
        return descendants;
    }

    List<Category> findAncestors(UUID id) {
        findById(id);
        List<Category> ancestors = this.categoryRepository.findAncestors(id);
        if(ancestors.isEmpty()){
            log.info("Category.jsx {} has no ancestors", id);
        }
        return ancestors;
    }

    void activateOrDeactivateParentAndChildrenWithoutVersioning(UUID categoryId, CategoryStatus categoryStatus) {
       existsById(categoryId);
       this.categoryRepository.findDescendantsForActivationOrDeactivationWithoutVersioning(categoryId,categoryStatus);
    }

    void activateOrDeactivateParentAndChildrenWithVersioning(UUID id, CategoryStatus status) {
        existsById(id);
        if(status == CategoryStatus.ACTIVE)
        {
            activateParentAndChildrenWithVersioning(id);
        }
        else if(status == CategoryStatus.INACTIVE)
        {
            deactivateParentAndChildrenWithVersioning(id);
        }
        else
        {
            throw new IllegalArgumentException("Unsupported status:" + status);
        }
    }

    void deactivateParentAndChildrenWithVersioning(UUID categoryId){
        deactivate(categoryId);
        List<Category> children = this.categoryRepository.findDescendantsForActivationOrDeactivationWithVersioning(categoryId);

        if (children.isEmpty()) {
            log.info("Category.jsx {} has no descendants to deactivate", categoryId);
            return;
        }

        List<Category> deactivatedChildren = this.mapper.toDeactivateList(children);
        this.categoryRepository.saveAll(deactivatedChildren);
    }

    void activateParentAndChildrenWithVersioning(UUID categoryId){
        activate(categoryId);
        List<Category> children = this.categoryRepository.findDescendantsForActivationOrDeactivationWithVersioning(categoryId);

        if (children.isEmpty()) {
            log.info("Category.jsx {} has no descendants to deactivate", categoryId);
            return;
        }
        List<Category> deactivatedChildren = this.mapper.toActivateList(children);
        this.categoryRepository.saveAll(deactivatedChildren);
    }

    void deleteById(UUID id) {
        findById(id);
        List<Category> descendants = findDescendants(id);
        if(!descendants.isEmpty()){
            log.info("Category.jsx {} has descendants ", id);
            return;
        }
        this.categoryRepository.delete(id);
        this.categoryRepository.deleteById(id);
    }

    public boolean existsById(UUID categoryId){
        return this.categoryRepository.existsById(categoryId);
    }

    <T> void validateDuplicatesByNameAndSlug(T request,Function<T,String> nameParam,Function<T,String> slugParam){
        String name = nameParam.apply(request);
        String slug = slugParam.apply(request);
        boolean existsByName = this.categoryRepository.existsByName(name);
        boolean existsBySlug = this.categoryRepository.existsBySlug(slug);
        if(existsByName || existsBySlug) {
            throw new CategoryAlreadyExistsException(
                    messageSource.getMessage("error.category.category.with.duplicate.name.or.slug",
                            new Object[]{name, slug},
                            LocaleContextHolder.getLocale()),
                            CategoryErrorCode.CATEGORY_ALREADY_EXISTS);
        }
    }

    long countRootCategories() {
        return this.categoryRepository.countRootCategories();
    }

}

@Repository("categoryRepository")
interface CategoryRepository extends CrudRepository<Category, UUID>{

    Optional<Category> findById(UUID id);

    Optional<Category> findByName(String name);

    Optional<Category> findBySlug(String slug);

    Page<Category> findAll(Pageable pageable);

    @Query("""
        SELECT
        c.id,
        c.name,
        c.slug,
        c.description,
        c.category_status,
        c.created_at,
        c.updated_at
        FROM categories c
        WHERE NOT EXISTS (
        SELECT 1
        FROM category_closure cc
        WHERE cc.child_id = c.id
        AND cc.depth = 1
        )
        ORDER BY c.created_at LIMIT :size OFFSET :offset
    """)
    List<Category> findAllRootCategories(int size,int offset);

    @Query("""
         SELECT c.* FROM categories c
                 JOIN category_closure cc ON cc.child_id = c.id
                 WHERE cc.parent_id = :parentId AND cc.depth = 1
    """)
    List<Category> findDescendants(@Param("parentId") UUID id);

    @Query("""
      SELECT c.* FROM categories c
                JOIN category_closure cc ON cc.parent_id = c.id
                WHERE cc.child_id = :childId AND cc.depth > 0
                ORDER BY cc.depth
    """)
    List<Category> findAncestors(@Param("childId") UUID id);

    @Modifying
    @Query("""
        UPDATE categories SET category_status = :categoryStatus
        WHERE id IN (
            SELECT cc.child_id FROM category_closure cc WHERE cc.parent_id= :id
        )
    """)
    void findDescendantsForActivationOrDeactivationWithoutVersioning(@Param("id") UUID id, @Param("categoryStatus") CategoryStatus categoryStatus);

    @Query("""
        SELECT c.* FROM categories c WHERE c.id IN (
            SELECT cc.child_id FROM category_closure cc WHERE cc.parent_id = :id
        )
    """)
    List<Category> findDescendantsForActivationOrDeactivationWithVersioning(@Param("id") UUID id);

    boolean existsByName(@Param("name") String name);

    boolean existsBySlug(@Param("slug") String slug);

    boolean existsById(@Param("id") UUID id);

    @Query("SELECT COUNT(*) > 0 FROM categories c WHERE (c.name = :name OR c.slug = :slug) AND c.id <> :id")
    boolean existsByNameOrSlugAndNotId(@Param("name") String name, @Param("slug") String slug, @Param("id") UUID id);

    @Modifying
    @Query("""
            DELETE FROM category_closure WHERE child_id = :id OR parent_id = :id
            """)
    void delete(@Param("id") UUID id);

    @Query("""
        SELECT COUNT(*) FROM categories c WHERE NOT EXISTS ( SELECT 1 FROM category_closure cc WHERE cc.child_id = c.id AND cc.depth = 1 )
    """)
    long countRootCategories();

}

@Repository("categoryClosureRepository")
interface CategoryClosureRepository extends CrudRepository<CategoryClosure,UUID> {

    @Modifying
    @Query("""
         INSERT INTO category_closure(parent_id,child_id,depth) values (:childId,:childId,0)
    """)
    void insertSelf(@Param("childId") UUID childId);

    @Modifying
    @Query("""
        INSERT INTO category_closure(parent_id, child_id, depth)
        SELECT p.parent_id, c.child_id, p.depth + c.depth + 1
        FROM category_closure p, category_closure c
        WHERE p.child_id = :parentId AND c.parent_id = :childId
    """)
    void insertClosure(@Param("parentId") UUID parentId,@Param("childId") UUID childId);

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
                category.catalogId);
    }
}

@Table("category_closure")
record CategoryClosure (
     UUID parentId,
     UUID childId,
     int depth) {}

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
interface CategoryMapper {

    @Mapping(target = "id", expression = "java(java.util.UUID.randomUUID())")
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "categoryStatus", constant = "INACTIVE")
    @Mapping(target = "createdAt", expression = "java(java.time.LocalDateTime.now())")
    @Mapping(target = "updatedAt", ignore = true)
    Category mapAddParentToCategory(AddParentRequest request);

    @Mapping(target = "id", expression = "java(java.util.UUID.randomUUID())")
    @Mapping(target = "createdAt", expression = "java(java.time.LocalDateTime.now())")
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "categoryStatus", constant = "INACTIVE")
    Category mapAddChildToCategory(AddChildRequest request);

    default Category mapUpdateToCategory(UpdateParentRequest request, Category category) {
        return new Category(
                category.id(),
                category.version(),
                request.name() != null ? request.name() :category.name(),
                request.description() != null ? request.description() :category.description(),
                category.createdAt(),
                LocalDateTime.now(),
                CategoryStatus.INACTIVE,
                request.slug() != null ? request.slug() :category.slug(),
                category.catalogId());
    }

    @Mapping(target = "id", source = "id")
    @Mapping(target = "name", source = "name")
    @Mapping(target = "description", source = "description")
    @Mapping(target = "status", source = "categoryStatus")
    @Mapping(target = "slug", source = "slug")
    @Mapping(target = "createdAt", source = "createdAt")
    @Mapping(target = "updatedAt", source = "updatedAt")
    RootCategoryResponse toRootCategoryResponse(Category category);

    List<RootCategoryResponse> toRootCategoryResponseList(List<Category> list);


    @Named("toDeactivate")
    @Mapping(target = "categoryStatus", constant = "INACTIVE")
    @Mapping(target = "updatedAt", expression = "java(java.time.LocalDateTime.now())")
    Category toDeactivate(Category category);

    @IterableMapping(qualifiedByName = "toDeactivate")
    List<Category> toDeactivateList(List<Category> category);

    @Named("toActivate")
    @Mapping(target = "categoryStatus", constant = "ACTIVE")
    @Mapping(target = "updatedAt", expression = "java(java.time.LocalDateTime.now())")
    Category toActivate(Category category);

    @IterableMapping(qualifiedByName = "toActivate")
    List<Category> toActivateList(List<Category> category);

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

enum CategoryStatus {

    ACTIVE("ACTIVE"),
    INACTIVE("INACTIVE");

    final String status;

    CategoryStatus(String status) {
        this.status = status;
    }
}

record AddParentRequest(
        @NotNull String name,
        @NotBlank String description,
        @NotNull String slug,
        @NotNull String catalogId) {}

record UpdateParentRequest(
        @NotNull String id,
        @NotNull String name,
        @NotBlank String description,
        @NotNull String slug,
        @NotNull String catalogId) {}

record AddChildRequest(
        @NotNull String name,
        @NotBlank String description,
        @NotNull String slug,
        @NotNull String parentId,
        @NotNull String catalogId) {}

record UpdateCategoryStatusRequest(
        CategoryStatus categoryStatus) {}

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

record RootCategoryResponse(
        String id,
        String name,
        String description,
        String createdAt,
        String updatedAt,
        String status,
        String slug) {}

class CategoryNotFoundException extends RuntimeException {

    private final CategoryErrorCode errorCode;

    public CategoryNotFoundException(String message, CategoryErrorCode errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public CategoryErrorCode getErrorCode() {
        return errorCode;
    }
}

class CategoryAlreadyActivatedException extends RuntimeException {

    private final CategoryErrorCode errorCode;

    public CategoryAlreadyActivatedException(String message, CategoryErrorCode errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public CategoryErrorCode getErrorCode() {
        return errorCode;
    }
}

class CategoryAlreadyDeactivatedException extends RuntimeException {
    private final CategoryErrorCode errorCode;

    public CategoryAlreadyDeactivatedException(String message, CategoryErrorCode errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public CategoryErrorCode getErrorCode() {
        return errorCode;
    }
}

class CategoryAlreadyExistsException extends RuntimeException {
    private final CategoryErrorCode errorCode;

    public CategoryAlreadyExistsException(String message, CategoryErrorCode errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public CategoryErrorCode getErrorCode() {
        return errorCode;
    }
}

class CategoryNotBelongToCatalog extends RuntimeException {
    private final CategoryErrorCode errorCode;

    public CategoryNotBelongToCatalog(String message, CategoryErrorCode errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public CategoryErrorCode getErrorCode() {
        return errorCode;
    }
}

enum CategoryErrorCode {
    CATEGORY_NOT_FOUND,
    CATEGORY_ALREADY_ACTIVATED,
    CATEGORY_ALREADY_DEACTIVATED,
    CATEGORY_ALREADY_EXISTS,
    CATEGORY_NOT_BELONG_TO_CATALOG
}

// controller // service // repository // model // mapper // enum // dto  // exception


