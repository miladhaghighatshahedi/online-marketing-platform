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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.github.f4b6a3.uuid.UuidCreator;
import com.mhs.onlinemarketingplatform.catalog.api.CategoryApi;
import com.mhs.onlinemarketingplatform.catalog.config.ImagePathProperties;
import com.mhs.onlinemarketingplatform.catalog.error.*;
import com.mhs.onlinemarketingplatform.common.AuditLogger;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.apache.tomcat.util.http.fileupload.FileUtils;
import org.mapstruct.*;
import org.mapstruct.Mapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.context.event.EventListener;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import static org.springframework.http.MediaType.IMAGE_PNG_VALUE;

/**
 * @author Milad Haghighat Shahedi
 */
@Controller("categoryController")
@ResponseBody
class CategoryController {

    private final CategoryQueryService queryService;
    private final CategoryCommandService commandService;
    private final CategoryImageUploadService imageUploadService;

    public CategoryController(CategoryQueryService queryService, CategoryCommandService commandService, CategoryImageUploadService imageUploadService) {
        this.queryService = queryService;
        this.commandService = commandService;
        this.imageUploadService = imageUploadService;
    }

    @PostMapping("/api/categories")
    ResponseEntity<CategoryApiResponse<CategoryResponse>> addParent(@RequestBody AddParentRequest addParentRequest) {
        CategoryResponse addedParentCategory = this.commandService.addParent(addParentRequest);
        return ResponseEntity.ok(new CategoryApiResponse<>(true,"Category parent added succesfully!",addedParentCategory));
    }

    @PostMapping("/api/categories/descendants")
    ResponseEntity<CategoryApiResponse<CategoryResponse>> addChild(@RequestBody AddChildRequest addChildRequest) {
        CategoryResponse adddedChildCategory = this.commandService.addChild(addChildRequest);
        return ResponseEntity.ok(new CategoryApiResponse<>(true,"Category child added succesfully!",adddedChildCategory));
    }

    @PutMapping("/api/categories")
    ResponseEntity<CategoryApiResponse<CategoryResponse>> update(@RequestBody UpdateParentRequest updateParentRequest) {
        CategoryResponse updatedParentCategory = this.commandService.update(updateParentRequest);
        return ResponseEntity.ok(new CategoryApiResponse<>(true,"Category parent updated succesfully!",updatedParentCategory));
    }

    @PatchMapping("/api/categories")
    ResponseEntity<CategoryApiResponse<CategoryResponse>> patch(@RequestBody PatchParentRequest patchParentRequest) {
        CategoryResponse patchedCategory = this.commandService.patch(patchParentRequest);
        return ResponseEntity.ok(new CategoryApiResponse<>(true,"Category patched succesfully!",patchedCategory));
    }

    @DeleteMapping("/api/categories/{id}")
    ResponseEntity<?> delete(@PathVariable("id") UUID id) {
        this.commandService.delete(id);
        return ResponseEntity.ok(new CategoryApiResponse<>(true,"Category deleted succesfully!",null));
    }

    @PutMapping("/api/categories/{id}/activate")
    ResponseEntity<CategoryApiResponse<CategoryResponse>> activate(@PathVariable("id") UUID id) {
        CategoryResponse activatedCategory = this.commandService.activate(id);
        return ResponseEntity.ok(new CategoryApiResponse<>(true,"Category activated successfully!",activatedCategory));
    }

    @PutMapping("/api/categories/{id}/deactivate")
    ResponseEntity<CategoryApiResponse<CategoryResponse>> deactivate(@PathVariable("id") UUID id) {
        CategoryResponse deactivatedCategory = this.commandService.deactivate(id);
        return ResponseEntity.ok(new CategoryApiResponse<>(true,"Categordey deactivated successfully!",deactivatedCategory));
    }

    @PutMapping("/api/categories/{id}/versioned")
    ResponseEntity<?> activateOrDeactivateParentAndChildrenWithVersioning(@PathVariable("id") UUID id, @RequestBody UpdateCategoryStatusRequest request) {
        this.commandService.activateOrDeactivateParentAndChildrenWithVersioning(id,request.categoryStatus());
        return ResponseEntity.noContent().build();
    }




    @GetMapping("/api/categories/{id}")
    ResponseEntity<CategoryApiResponse<CategoryResponse>> findById(@PathVariable("id") UUID id) {
        CategoryResponse foundCategory = this.queryService.findById(id);
        return ResponseEntity.ok(new CategoryApiResponse<>(true,"Category found successfully!",foundCategory));
    }

    @GetMapping(value = "/api/categories", params = "name")
    ResponseEntity<CategoryApiResponse<CategoryResponse>> findByName(@RequestParam("name") String name) {
        CategoryResponse foundCategory = this.queryService.findByName(name);
        return ResponseEntity.ok(new CategoryApiResponse<>(true,"Category found successfully!",foundCategory));
    }

    @GetMapping(value = "/api/categories", params = "slug")
    ResponseEntity<CategoryApiResponse<CategoryResponse>> findBySlug(@RequestParam("slug") String slug) {
        CategoryResponse foundCategory = this.queryService.findBySlug(slug);
        return ResponseEntity.ok(new CategoryApiResponse<>(true,"Category found successfully!",foundCategory));
    }

    @GetMapping("/api/categories/{id}/with-sub-categories")
    ResponseEntity<CategoryDtoWithSubs> findACategoryWithSubCategoriesById(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(this.queryService.findACategoryWithSubCategoriesById(id));
    }

    @GetMapping(value = "/api/categories/root")
    Page<RootCategoryResponse> findAllrootCategories(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return this.queryService.findAllRootCategories(page,size);
    }

    @GetMapping("/api/categories/{catalogId}/catalog")
    ResponseEntity<CategoryApiResponse<List<CategoryResponse>>> findActiveRootCategoriesByCatalogId(@PathVariable("catalogId") UUID catalogId) {
        List<CategoryResponse> foundCategories = this.queryService.findActiveRootCategoriesByCatalogId(catalogId);
        return ResponseEntity.ok(new CategoryApiResponse<>(true,"Categories found successfully!",foundCategories));
    }




    @PutMapping("/api/categories/image")
    ResponseEntity<String> uploadImage(@RequestParam("id") UUID id,@RequestParam("image") MultipartFile image) {
        this.commandService.uploadImage(id,image);
        return ResponseEntity.accepted().body("Image upload accepted: processing asynchronously...");
    }

    @GetMapping(value = {"/api/categories/image/category/{imageName}"},produces = IMAGE_PNG_VALUE)
    byte[] getImage(@PathVariable("imageName") String imageName) throws Exception {
        return Files.readAllBytes(Paths.get("src/main/resources/image/category/"+this.imageUploadService.removeExtension(imageName)+"/"+imageName));
    }

    @GetMapping("/api/categories/root/count")
    long countRootCategories() {
        return this.queryService.countRootCategories();
    }

}

@Service
class CategoryQueryService {

    private static final Logger logger = LoggerFactory.getLogger(CategoryQueryService.class);

    private final AuditLogger auditLogger;
    private final CategoryRepository repository;
    private final CategoryMapper mapper;
    private final MessageSource messageSource;

    public CategoryQueryService(AuditLogger auditLogger, CategoryRepository repository, CategoryMapper mapper, MessageSource messageSource) {
        this.auditLogger = auditLogger;
        this.repository = repository;
        this.mapper = mapper;
        this.messageSource = messageSource;
    }

    CategoryResponse findById(UUID categoryId) {
        logger.info("Looking up category by ID: {}",categoryId);
        Category category = this.repository.findById(categoryId)
                .orElseThrow(() ->  new CategoryNotFoundException(
                        messageSource.getMessage("error.category.category.with.id.not.found",
                                new Object[]{categoryId},
                                LocaleContextHolder.getLocale()),
                        CategoryErrorCode.CATEGORY_NOT_FOUND));
        return this.mapper.mapCategoryToResponse(category);
    }

    CategoryResponse findByName(String name) {
        logger.info("Looking up category by NAME: {}",name);
        Category category = this.repository.findByName(name)
                .orElseThrow(() ->  new CategoryNotFoundException(
                        messageSource.getMessage("error.category.category.with.name.not.found",
                                new Object[]{name},
                                LocaleContextHolder.getLocale()),
                        CategoryErrorCode.CATEGORY_NOT_FOUND));
        return this.mapper.mapCategoryToResponse(category);
    }

    CategoryResponse findBySlug(String slug) {
        logger.info("Looking up category by SLUG: {}",slug);
        Category category = this.repository.findBySlug(slug)
                .orElseThrow(() ->  new CategoryNotFoundException(
                        messageSource.getMessage("error.category.category.with.slug.not.found",
                                new Object[]{slug},
                                LocaleContextHolder.getLocale()),
                        CategoryErrorCode.CATEGORY_NOT_FOUND));
        return this.mapper.mapCategoryToResponse(category);
    }

    List<CategoryResponse> findActiveRootCategoriesByCatalogId(UUID catalogId) {
        logger.info("Retriving active root categories by catalog ID: {}",catalogId);
        List<Category> existingCategories = this.repository.findActiveRootCategoriesByCatalogId(catalogId);
        return this.mapper.mapListToListOfResponse(existingCategories);
    }

    @Cacheable(key = "#id" , value = "categories")
    public CategoryDtoWithSubs findACategoryWithSubCategoriesById(UUID id) {
        logger.info("Retriving a category and its children by ID: {}",id);
        Category ancestor = this.repository.findById(id)
                .orElseThrow(() -> new CategoryNotFoundException(
                        messageSource.getMessage("error.category.category.with.id.not.found",
                                new Object[]{id},
                                LocaleContextHolder.getLocale()),
                        CategoryErrorCode.CATEGORY_NOT_FOUND));

        List<Category> descendants = this.repository.findDescendants(id);

        List<CategoryDtoWithSubs> descendantDto = descendants.stream()
                .map(mapper::toCategoryDtoWithSubs)
                .toList();

        this.auditLogger.log("CATEGORY_RETRIEVED", "CATEGORY", "Category NAME: "+ancestor.name());
        return this.mapper.toCategoryDtoWithSubs(ancestor, descendantDto);
    }

    Page<RootCategoryResponse> findAllRootCategories(int page, int size) {
        logger.info("Retriving all root categories");
        int offset = page * size;

        List<Category> categories = this.repository.findAllRootCategories(size, offset);

        if (categories.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(), PageRequest.of(page, size), 0);
        }

        long total = this.repository.countRootCategories();

        List<RootCategoryResponse> rootCategoryResponseList = this.mapper.toRootCategoryResponseList(categories);

        this.auditLogger.log("ROOT_CATEGORIES_RETRIEVED_ALL", "CATEGORY", "Category TOTAL: "+total);
        return new PageImpl<>(
                rootCategoryResponseList,
                PageRequest.of(page,size),
                total
        );
    }

    List<Category> findDescendants(UUID id) {
        if(!this.repository.existsById(id)) {
            throw new CategoryNotFoundException(
                    messageSource.getMessage("error.category.category.with.id.not.found",
                            new Object[]{id},
                            LocaleContextHolder.getLocale()),
                    CategoryErrorCode.CATEGORY_NOT_FOUND);}
        List<Category> descendants = this.repository.findDescendants(id);
        if(descendants.isEmpty()){
            logger.info("Category {} has no descendants", id);
        }
        return descendants;
    }

    List<Category> findAncestors(UUID id) {
        if(!this.repository.existsById(id)) {
            throw new CategoryNotFoundException(
                    messageSource.getMessage("error.category.category.with.id.not.found",
                            new Object[]{id},
                            LocaleContextHolder.getLocale()),
                    CategoryErrorCode.CATEGORY_NOT_FOUND);}
        List<Category> ancestors = this.repository.findAncestors(id);
        if(ancestors.isEmpty()){
            logger.info("Category {} has no ancestors", id);
        }
        return ancestors;
    }

    @Cacheable(key = "'rootCategoryCount'",value = "categories")
    public long countRootCategories() {
        return this.repository.countRootCategories();
    }

}

@Service
class CategoryCommandService implements CategoryApi{
    private static final Logger logger = LoggerFactory.getLogger(CategoryCommandService.class);

    private final AuditLogger auditLogger;
    private final CategoryRepository repository;
    private final CategoryClosureRepository categoryClosureRepository;
    private final CategoryQueryService queryService;
    private final CatalogService catalogService;
    private final CategoryImageUploadService imageUploadService;
    private final CategoryMapper mapper;
    private final ImagePathProperties properties;
    private final MessageSource messageSource;

    public CategoryCommandService(AuditLogger auditLogger,
                                  CategoryRepository repository,
                                  CategoryClosureRepository categoryClosureRepository,
                                  CategoryQueryService queryService,
                                  CatalogService catalogService,
                                  CategoryImageUploadService imageUploadService,
                                  CategoryMapper mapper,
                                  ImagePathProperties properties,
                                  MessageSource messageSource) {
        this.auditLogger = auditLogger;
        this.repository = repository;
        this.categoryClosureRepository = categoryClosureRepository;
        this.queryService = queryService;
        this.catalogService = catalogService;
        this.imageUploadService = imageUploadService;
        this.mapper = mapper;
        this.properties = properties;
        this.messageSource = messageSource;
    }

    @CacheEvict(value = "catalogs", allEntries = true)
    public CategoryResponse addParent(AddParentRequest addParentRequest) {
        logger.info("Creating new parent category with name: {}",addParentRequest.name());
        UUID catalogId = UUID.fromString(addParentRequest.catalogId());

        if(!this.catalogService.existsById(catalogId)) {
            throw new CatalogNotFoundException(
                    messageSource.getMessage("error.catalog.catalog.with.id.not.found",
                            new Object[]{catalogId},
                            LocaleContextHolder.getLocale()),
                    CatalogErrorCode.CATALOG_NOT_FOUND);}

        validateDuplicatesByNameAndSlug(addParentRequest,AddParentRequest::name,AddParentRequest::slug);

        Category mappedCategory = this.mapper.mapAddParentToCategory(addParentRequest);

        Category storedCategory = this.repository.save(mappedCategory);
        this.categoryClosureRepository.insertSelf(storedCategory.id());
        this.auditLogger.log("PARENT_CTEGORY_CREATED", "CATEGORY", "Category NAME: " + storedCategory.name());

        return this.mapper.mapCategoryToResponse(this.repository.findById(storedCategory.id()).orElseThrow());
    }

    @Caching(evict = {@CacheEvict(value = "catalogs", allEntries = true), @CacheEvict(value = "categories", allEntries = true)})
    public CategoryResponse addChild(AddChildRequest addChildRequest) {
        logger.info("Creating new child category with name: {}",addChildRequest.name());
        UUID catalogId = UUID.fromString(addChildRequest.catalogId());
        UUID categoryId = UUID.fromString(addChildRequest.categoryId());

        if(!this.catalogService.existsById(catalogId)) {
            throw new CatalogNotFoundException(
                    messageSource.getMessage("error.catalog.catalog.with.id.not.found",
                            new Object[]{catalogId},
                            LocaleContextHolder.getLocale()),
                    CatalogErrorCode.CATALOG_NOT_FOUND);}

        validateDuplicatesByNameAndSlug(addChildRequest,AddChildRequest::name,AddChildRequest::slug);

        Category existingCategory = this.repository.findById(categoryId)
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

        Category storedChildCategory = this.repository.save(mappedCategory);
        this.categoryClosureRepository.insertSelf(storedChildCategory.id());
        this.categoryClosureRepository.insertClosure(categoryId,storedChildCategory.id());
        this.auditLogger.log("CHILD_CATEGORY_CREATED", "CATEGORY", "Category NAME: " + storedChildCategory.name());


        return this.mapper.mapCategoryToResponse(storedChildCategory);
    }

    @Caching(evict = {@CacheEvict(value = "catalogs", allEntries = true), @CacheEvict(value = "categories", allEntries = true)})
    public CategoryResponse update(UpdateParentRequest updateParentRequest) {
        logger.info("Updating exisiting category with name: {}",updateParentRequest.name());
        UUID catalogId = UUID.fromString(updateParentRequest.catalogId());
        UUID id = UUID.fromString(updateParentRequest.id());

        if(!this.catalogService.existsById(catalogId)) {
            throw new CatalogNotFoundException(
                    messageSource.getMessage("error.catalog.catalog.with.id.not.found",
                            new Object[]{catalogId},
                            LocaleContextHolder.getLocale()),
                    CatalogErrorCode.CATALOG_NOT_FOUND);}

        Category existingCategory = this.repository.findById(id)
                .orElseThrow(() -> new CategoryNotFoundException(
                        messageSource.getMessage("error.category.category.with.id.not.found",
                                new Object[]{id},
                                LocaleContextHolder.getLocale()),
                        CategoryErrorCode.CATEGORY_NOT_FOUND));

        if(!existingCategory.name().equals(updateParentRequest.name()) || !existingCategory.slug().equals(updateParentRequest.slug())) {
            boolean exists = this.repository.existsByNameOrSlugAndNotId(updateParentRequest.name(), updateParentRequest.slug(), id);
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
        Category storedCategory = this.repository.save(mappedCategory);
        this.auditLogger.log("CATALOG_UPDATED", "CATALOG", "Catalog NAME: " + storedCategory.name());

        return this.mapper.mapCategoryToResponse(this.repository.findById(storedCategory.id()).orElseThrow());
    }

    @Caching(evict = {@CacheEvict(value = "catalogs", allEntries = true), @CacheEvict(value = "categories", allEntries = true)})
    public CategoryResponse patch(PatchParentRequest patchParentRequest) {
        logger.info("Patching exisiting category with name: {}",patchParentRequest.name());
        UUID catalogId = UUID.fromString(patchParentRequest.catalogId());
        UUID id = UUID.fromString(patchParentRequest.id());

        if(!this.catalogService.existsById(catalogId)) {
            throw new CatalogNotFoundException(
                    messageSource.getMessage("error.catalog.catalog.with.id.not.found",
                            new Object[]{catalogId},
                            LocaleContextHolder.getLocale()),
                    CatalogErrorCode.CATALOG_NOT_FOUND);}

        Category existingCategory = this.repository.findById(id)
                .orElseThrow(() -> new CategoryNotFoundException(
                        messageSource.getMessage("error.category.category.with.id.not.found",
                                new Object[]{id},
                                LocaleContextHolder.getLocale()),
                        CategoryErrorCode.CATEGORY_NOT_FOUND));

        String existingName = patchParentRequest.name();
        String existingslug = patchParentRequest.slug();

        if(patchParentRequest.name() != null && !existingName.equals(patchParentRequest.name())) {
            boolean exists = this.repository.existsByName(patchParentRequest.name());
            if(exists) {
                throw new CategoryAlreadyExistsException(
                        messageSource.getMessage("error.category.category.with.name.exists",
                                new Object[]{patchParentRequest.name()},
                                LocaleContextHolder.getLocale()),
                        CategoryErrorCode.CATEGORY_ALREADY_EXISTS);}}

        if(patchParentRequest.slug() != null && !existingslug.equals(patchParentRequest.slug())) {
            boolean exists = this.repository.existsBySlug(patchParentRequest.slug());
            if(exists) {
                throw new CategoryAlreadyExistsException(
                        messageSource.getMessage("error.category.category.with.slug.exists",
                                new Object[]{patchParentRequest.slug()},
                                LocaleContextHolder.getLocale()),
                        CategoryErrorCode.CATEGORY_ALREADY_EXISTS);}}


        if(!existingCategory.catalogId().equals(catalogId)){
            throw new CategoryNotBelongToCatalog(
                    messageSource.getMessage("error.category.category.not.belong.to.catalog",
                            new Object[]{id},
                            LocaleContextHolder.getLocale()),
                    CategoryErrorCode.CATEGORY_NOT_BELONG_TO_CATALOG);}

        Category mappedCategory = this.mapper.mapPatchToCategory(patchParentRequest,existingCategory);
        Category storedCategory = this.repository.save(mappedCategory);
        this.auditLogger.log("CATALOG_UPDATED", "CATALOG", "Catalog NAME: " + storedCategory.name());

        return this.mapper.mapCategoryToResponse(this.repository.findById(storedCategory.id()).orElseThrow());
    }

    @Caching(evict = {@CacheEvict(value = "catalogs", allEntries = true), @CacheEvict(value = "categories", allEntries = true)})
    public void delete(UUID id) {
        Category category = this.repository.findById(id)
                .orElseThrow(() ->  new CategoryNotFoundException(
                        messageSource.getMessage("error.category.category.with.id.not.found",
                                new Object[]{id},
                                LocaleContextHolder.getLocale()),
                        CategoryErrorCode.CATEGORY_NOT_FOUND));
        logger.info("Deleting exisiting category with id: {} and name: {}",category.id(),category.name());

        List<Category> descendants = this.queryService.findDescendants(id);
        if(!descendants.isEmpty()){
            logger.info("Category {} has descendants",category.name());
            return;
        }
        this.repository.delete(id);
        this.repository.deleteById(id);
        this.deleteImage(id);
        logger.info("Deleting exisiting category with name: {}",category.name());
    }

    CategoryResponse activate(UUID categoryId) {
        Category exsitingCategory = this.repository.findById(categoryId)
                .orElseThrow(() ->  new CategoryNotFoundException(
                        messageSource.getMessage("error.category.category.with.id.not.found",
                                new Object[]{categoryId},
                                LocaleContextHolder.getLocale()),
                        CategoryErrorCode.CATEGORY_NOT_FOUND));
        logger.info("Activate a category by ID: {} and Name: {}",categoryId,exsitingCategory.name());

        if(exsitingCategory.categoryStatus() == CategoryStatus.INACTIVE) {
            Category activatedCategory = this.mapper.toActivate(exsitingCategory);
            Category storedCategory = this.repository.save(activatedCategory);
            this.auditLogger.log("CATEGORY_ACTIVATED", "CATEGORY", "Category NAME: "+exsitingCategory.name());

            return this.mapper.mapCategoryToResponse(storedCategory);
        }

        throw new CategoryAlreadyActivatedException(
                messageSource.getMessage("error.category.category.already.activated",
                        new Object[]{categoryId,exsitingCategory.name()},
                        LocaleContextHolder.getLocale()),
                CategoryErrorCode.CATEGORY_ALREADY_ACTIVATED);
    }

    CategoryResponse deactivate(UUID categoryId) {
        logger.info("Deactivate a category by ID: {}",categoryId);
        Category exsitingCategory = this.repository.findById(categoryId)
                .orElseThrow(() ->  new CategoryNotFoundException(
                        messageSource.getMessage("error.category.category.with.id.not.found",
                                new Object[]{categoryId},
                                LocaleContextHolder.getLocale()),
                        CategoryErrorCode.CATEGORY_NOT_FOUND));
        logger.info("Deactivate a category by ID: {} and NAME: {}",categoryId,exsitingCategory.name());

        if(exsitingCategory.categoryStatus() == CategoryStatus.ACTIVE) {
            Category deactivatedCategory = this.mapper.toDeactivate(exsitingCategory);
            Category storedCategory = this.repository.save(deactivatedCategory);
            this.auditLogger.log("CATEGORY_DEACTIVATED", "CATEGORY", "Category NAME: "+exsitingCategory.name());
            return this.mapper.mapCategoryToResponse(storedCategory);
        }

        throw new CategoryAlreadyDeactivatedException(
                messageSource.getMessage("error.category.category.already.deactivated",
                        new Object[]{categoryId,exsitingCategory.name()},
                        LocaleContextHolder.getLocale()),
                CategoryErrorCode.CATEGORY_ALREADY_DEACTIVATED);
    }

    void activateOrDeactivateParentAndChildrenWithVersioning(UUID id, CategoryStatus status) {
        Category category = this.repository.findById(id)
                .orElseThrow(() ->  new CategoryNotFoundException(
                        messageSource.getMessage("error.category.category.with.id.not.found",
                                new Object[]{id},
                                LocaleContextHolder.getLocale()),
                        CategoryErrorCode.CATEGORY_NOT_FOUND));
        logger.info("Activate or Deactivate category {} and its children by its ID: {}",category.name(),id);

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
        List<Category> children = this.repository.findDescendantsForActivationOrDeactivationWithVersioning(categoryId);

        if (children.isEmpty()) {
            logger.info("Category {} has no descendants to deactivate", categoryId);
            return;
        }

        List<Category> deactivatedChildren = this.mapper.toDeactivateList(children);
        this.repository.saveAll(deactivatedChildren);
        this.auditLogger.log("CHILDREN_CATEGORY_DEACTIVATED", "CATEGORY", "Category ID: "+categoryId);
    }

    void activateParentAndChildrenWithVersioning(UUID categoryId){
        activate(categoryId);
        List<Category> children = this.repository.findDescendantsForActivationOrDeactivationWithVersioning(categoryId);

        if (children.isEmpty()) {
            logger.info("Category {} has no descendants to deactivate", categoryId);
            return;
        }
        List<Category> deactivatedChildren = this.mapper.toActivateList(children);
        this.repository.saveAll(deactivatedChildren);
        this.auditLogger.log("CHILDREN_CATEGORY_ACTIVATED", "CATEGORY", "Category ID: "+categoryId);
    }

    public boolean existsById(UUID categoryId){
        return this.repository.existsById(categoryId);
    }

    void uploadImage(UUID categoryId, MultipartFile image) {
        logger.info("Uploading a new photo for a category with the ID {}",categoryId);
        if(!this.repository.existsById(categoryId)){
            throw new CategoryNotFoundException(
                    messageSource.getMessage("error.category.category.with.id.not.found",
                            new Object[]{categoryId},
                            LocaleContextHolder.getLocale()),
                    CategoryErrorCode.CATEGORY_NOT_FOUND);}

        Path imageBasePath = this.imageUploadService.createMainDirectoryIfNotExists(categoryId,properties.getCategoryImagePath());
        byte[] file = this.imageUploadService.preloadImage(image);
        String baseUrl = ServletUriComponentsBuilder.fromCurrentContextPath().toUriString();

        this.imageUploadService.storeImagesIntoFileSystemAsync(categoryId,file,imageBasePath,baseUrl);
    }

    private void deleteImage(UUID id) {
        logger.info("Deleting exisiting directory with id: {} ",id);
        try {
            Path categoryFolder = Paths.get(properties.getCategoryImagePath(),id.toString()).toAbsolutePath().normalize();
            if(Files.exists(categoryFolder)) {
                FileUtils.deleteDirectory(categoryFolder.toFile());
            }
            this.auditLogger.log("CATEGORY_DIRECTORY_DELETED", "CATEGORY", "Category sub directory: " + id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    <T> void validateDuplicatesByNameAndSlug(T request,Function<T,String> nameParam,Function<T,String> slugParam){
        String name = nameParam.apply(request);
        String slug = slugParam.apply(request);
        boolean existsByName = this.repository.existsByName(name);
        boolean existsBySlug = this.repository.existsBySlug(slug);
        if(existsByName || existsBySlug) {
            throw new CategoryAlreadyExistsException(
                    messageSource.getMessage("error.category.category.with.duplicate.name.or.slug",
                            new Object[]{name, slug},
                            LocaleContextHolder.getLocale()),
                    CategoryErrorCode.CATEGORY_ALREADY_EXISTS);
        }
    }

}

@Service
class CategoryImageUploadService {

    private static final Logger logger = LoggerFactory.getLogger(CatalogImageUploadService.class);
    private final AuditLogger auditLogger;
    private final ApplicationEventPublisher applicationEventPublisher;

    public CategoryImageUploadService(
            AuditLogger auditLogger,
            ApplicationEventPublisher applicationEventPublisher) {
        this.auditLogger = auditLogger;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    Path createMainDirectoryIfNotExists(UUID id, String path) {
        if(id == null) {
            throw new RuntimeException("Exception in creating the main directory ");
        }
        logger.info("Main directory with the id: {} and path: {} created", id, path);
        try {
            Path direcetory = Paths.get(path, id.toString()).toAbsolutePath().normalize();
            if (!Files.exists(direcetory)) {
                Files.createDirectories(direcetory);
            }
            return direcetory;
        } catch (Exception e) {
            throw new RuntimeException("Exception in creating a main directory to store image");
        }
    }

    private String imageExtension(String imageName) {
        return Optional.of(imageName)
                .filter(name -> name.contains("."))
                .map(name -> "." + name.substring(imageName.lastIndexOf(".") + 1))
                .orElse(".png");
    }

    String removeExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return filename;
        }

        int lastDotIndex = filename.lastIndexOf(".");
        if (lastDotIndex > 0) {
            return filename.substring(0, lastDotIndex);
        }

        return filename;
    }

    byte[] preloadImage(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    String prepareImageUpload(UUID imageId, byte[] imageFile, Path imageBasePath,String baseUrl) {
        logger.info("Stroing an image with size ({} bytes) and id: {} and path: {} ",imageFile.length, imageId, imageBasePath);
        String imageName = imageId.toString() + ".png";
        try {
            Path imagePath = imageBasePath.resolve(imageName);
            Files.write(imagePath, imageFile);
            return baseUrl + "/api/categories/image/category/" + imageName;
        } catch (Exception e) {
            throw new RuntimeException("Exception happened while storing image in to the advertisement directory",e);
        }
    }

    @Async("categoryImageTaskExecutor")
    public void storeImagesIntoFileSystemAsync(UUID imageId, byte[] imagefile, Path imageBasePath, String baseUrl){
        logger.info("Calling ASYNC method with thread {} to store image with name {} and size {} ",Thread.currentThread().getName(),imageId,imagefile.length);
        String storedImageUrl = null;
        try {
            storedImageUrl = prepareImageUpload(imageId, imagefile, imageBasePath,baseUrl);
            this.auditLogger.log("ASYNC_CATEGORY_IMAGE_FILE_STORED", "ASYNC_CATEGORY_IMAGE_FILE", "ASYNC_CATEGORY_IMAGE_FILE stored with the url " + storedImageUrl);
            this.applicationEventPublisher.publishEvent(new UpdateCategoryImageUrlEvent(imageId,storedImageUrl));
        } catch (Exception e) {
            logger.error("Exception happened while stroing the image asynchronously",e);
        }
        this.auditLogger.log("ASYNC_CATEGORY_IMAGE_FILE_STORED", "ASYNC_CATEGORY_IMAGE_FILE_STORED", "ASYNC_CATEGORY_IMAGE_FILE_STORED one image stored successfully = " + imageId);
        logger.info("One image processed successfully with the name {}",imageId);
    }

}

@Repository("categoryRepository")
interface CategoryRepository extends CrudRepository<Category, UUID>{

    Optional<Category> findById(UUID id);

    Optional<Category> findByName(String name);

    Optional<Category> findBySlug(String slug);

    @Query("""
          SELECT id,name FROM categories
          LEFT JOIN category_closure cc ON cc.child_id = id AND cc.depth = 1
          WHERE catalog_id = :catalogId AND category_status = 'ACTIVE' AND cc.parent_id is NULL
          order by created_at
    """)
    List<Category> findActiveRootCategoriesByCatalogId(@Param("catalogId") UUID id);

    @Query("""
        SELECT c.id, c.name,c.slug,c.description,
        c.category_status,c.created_at,c.updated_at, c.image_url
        FROM categories c
        WHERE NOT EXISTS (
        SELECT 1 FROM category_closure cc WHERE cc.child_id = c.id AND cc.depth = 1 )
        ORDER BY c.created_at LIMIT :size OFFSET :offset
    """)
    List<Category> findAllRootCategories(int size,int offset);

    @Query("""
         SELECT c.* FROM categories c
         JOIN category_closure cc ON cc.child_id = c.id
          WHERE cc.parent_id = :parentId AND cc.depth = 1 order by created_at
    """)
    List<Category> findDescendants(@Param("parentId") UUID id);

    @Query("""
      SELECT c.* FROM categories c
                JOIN category_closure cc ON cc.parent_id = c.id
                WHERE cc.child_id = :childId AND cc.depth > 0
                ORDER BY cc.depth
    """)
    List<Category> findAncestors(@Param("childId") UUID id);

    @Query("SELECT c.* FROM categories c WHERE c.id IN (SELECT cc.child_id FROM category_closure cc WHERE cc.parent_id = :id)")
    List<Category> findDescendantsForActivationOrDeactivationWithVersioning(@Param("id") UUID id);

    boolean existsByName(@Param("name") String name);

    boolean existsBySlug(@Param("slug") String slug);

    boolean existsById(@Param("id") UUID id);

    @Query("SELECT COUNT(*) > 0 FROM categories c WHERE (c.name = :name OR c.slug = :slug) AND c.id <> :id")
    boolean existsByNameOrSlugAndNotId(@Param("name") String name, @Param("slug") String slug, @Param("id") UUID id);

    @Modifying
    @Query("DELETE FROM category_closure WHERE child_id = :id OR parent_id = :id")
    void delete(@Param("id") UUID id);

    @Query("SELECT COUNT(*) FROM categories c WHERE NOT EXISTS ( SELECT 1 FROM category_closure cc WHERE cc.child_id = c.id AND cc.depth = 1 )")
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
        String imageUrl,
        UUID catalogId) {}

@Table("category_closure")
record CategoryClosure (
     UUID parentId,
     UUID childId,
     int depth) {}

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
        @NotNull String imageUrl,
        @NotNull String catalogId) {}

record UpdateParentRequest(
        @NotNull String id,
        @NotNull String name,
        @NotBlank String description,
        @NotNull String slug,
        @NotNull String imageUrl,
        @NotNull String catalogId) {}

record PatchParentRequest(
        @NotNull String id,
        String name,
        String description,
        String slug,
        String imageUrl,
        @NotNull String catalogId) {}

record AddChildRequest(
        @NotNull String name,
        @NotBlank String description,
        @NotNull String slug,
        @NotNull String imageUrl,
        @NotNull String categoryId,
        @NotNull String catalogId) {}

record UpdateCategoryStatusRequest(CategoryStatus categoryStatus) {}

@JsonInclude(JsonInclude.Include.NON_NULL)
record CategoryResponse(
        String id,
        String name,
        String description,
        String createdAt,
        String updatedAt,
        String status,
        String slug,
        String imageUrl,
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
        String imageUrl,
        String slug) {}

record CategoryDtoWithSubs (
        UUID id,
        String name,
        String slug,
        String description,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String imageUrl,
        UUID catalogId,
        List<CategoryDtoWithSubs> subCategories
) {}

record CategoryApiResponse<T>(
        boolean success,
        String message,
        T data
) {}

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE,imports = {UuidCreator.class, LocalDateTime.class})
interface CategoryMapper {

    @Mapping(target = "id", expression = "java(UuidCreator.getTimeOrderedEpoch())")
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "categoryStatus", constant = "INACTIVE")
    @Mapping(target = "createdAt", expression = "java(LocalDateTime.now())")
    @Mapping(target = "updatedAt", ignore = true)
    Category mapAddParentToCategory(AddParentRequest request);

    @Mapping(target = "id", expression = "java(UuidCreator.getTimeOrderedEpoch())")
    @Mapping(target = "createdAt", expression = "java(LocalDateTime.now())")
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "categoryStatus", constant = "INACTIVE")
    Category mapAddChildToCategory(AddChildRequest request);

    @Mapping(target = "id", source = "category.id")
    @Mapping(target = "version", source = "category.version")
    @Mapping(target = "name", expression = "java(request.name() != null ? request.name() : category.name())")
    @Mapping(target = "description", expression = "java(request.description() != null ? request.description() : category.description())")
    @Mapping(target = "createdAt", source = "category.createdAt")
    @Mapping(target = "updatedAt", expression = "java(LocalDateTime.now())")
    @Mapping(target = "categoryStatus", constant = "INACTIVE")
    @Mapping(target = "slug", expression = "java(request.slug() != null ? request.slug() : category.slug())")
    @Mapping(target = "imageUrl",  source = "category.imageUrl")
    @Mapping(target = "catalogId", source = "category.catalogId")
    Category mapUpdateToCategory(UpdateParentRequest request, Category category);


    @Mapping(target = "id", source = "category.id")
    @Mapping(target = "version", source = "category.version")
    @Mapping(target = "name", expression = "java(request.name() != null ? request.name() : category.name())")
    @Mapping(target = "description", expression = "java(request.description() != null ? request.description() : category.description())")
    @Mapping(target = "createdAt", source = "category.createdAt")
    @Mapping(target = "updatedAt", expression = "java(LocalDateTime.now())")
    @Mapping(target = "categoryStatus", constant = "INACTIVE")
    @Mapping(target = "slug", expression = "java(request.slug() != null ? request.slug() : category.slug())")
    @Mapping(target = "imageUrl",  source = "category.imageUrl")
    @Mapping(target = "catalogId", source = "category.catalogId")
    Category mapPatchToCategory(PatchParentRequest request, Category category);

    @Mapping(target = "updatedAt", expression = "java(LocalDateTime.now())")
    @Mapping(target = "version", source = "category.version")
    @Mapping(target = "imageUrl", source = "newImageUrl")
    Category mapCategoryWithImage(String newImageUrl,Category category);

    @Mapping(target = "id", source = "id")
    @Mapping(target = "name", source = "name")
    @Mapping(target = "description", source = "description")
    @Mapping(target = "status", source = "categoryStatus")
    @Mapping(target = "slug", source = "slug")
    @Mapping(target = "imageUrl", source = "imageUrl")
    @Mapping(target = "createdAt", source = "createdAt")
    @Mapping(target = "updatedAt", source = "updatedAt")
    RootCategoryResponse toRootCategoryResponse(Category category);

    List<RootCategoryResponse> toRootCategoryResponseList(List<Category> list);

    List<CategoryResponse> mapListToListOfResponse(Iterable<Category> categories);

    @Mapping(target = "id", source = "id")
    @Mapping(target = "name", source = "name")
    @Mapping(target = "slug", source = "slug")
    @Mapping(target = "description", source = "description")
    @Mapping(target = "status", source = "categoryStatus")
    @Mapping(target = "imageUrl", source = "imageUrl")
    @Mapping(target = "createdAt", source = "createdAt")
    @Mapping(target = "updatedAt", source = "updatedAt")
    @Mapping(target = "catalogId", source = "catalogId")
    @Mapping(target = "subCategories", ignore = true)
    CategoryDtoWithSubs toCategoryDtoWithSubs(Category category);

    CategoryDtoWithSubs toCategoryDtoWithSubs(Category category, List<CategoryDtoWithSubs> subCategories);

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

// controller // service // repository // model // mapper // enum // dto  // exception

record UpdateCategoryImageUrlEvent(UUID id,String url) {}

@Component
class UpdateCategoryImageUrlEventHandler {

    private final MessageSource messageSource;
    private final AuditLogger auditLogger;
    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;

    public UpdateCategoryImageUrlEventHandler(
            MessageSource messageSource,
            AuditLogger auditLogger,
            CategoryRepository categoryRepository,
            CategoryMapper categoryMapper) {
        this.messageSource = messageSource;
        this.auditLogger = auditLogger;
        this.categoryRepository = categoryRepository;
        this.categoryMapper = categoryMapper;
    }

    @Transactional
    @EventListener
    public void handleImageStored(UpdateCategoryImageUrlEvent event) {
        Category category = this.categoryRepository.findById(event.id())
                .orElseThrow(() ->  new CategoryNotFoundException(
                        messageSource.getMessage("error.category.category.with.id.not.found",
                                new Object[]{event.id()},
                                LocaleContextHolder.getLocale()),
                        CategoryErrorCode.CATEGORY_NOT_FOUND));

        Category updatedCategoryWithImage = this.categoryMapper.mapCategoryWithImage(event.url(),category);
        this.categoryRepository.save(updatedCategoryWithImage);
        this.auditLogger.log("CATEGORY_IMAGE_FILE_UPDATED", "CATEGORY_IMAGE_UPLOAD_EVENT", "CATEGORY_IMAGE_FILE updated for the Id: " + event.id());
    }
}

