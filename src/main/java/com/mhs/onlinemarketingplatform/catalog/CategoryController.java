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

import com.mhs.onlinemarketingplatform.catalog.config.ApiProperties;
import com.mhs.onlinemarketingplatform.catalog.error.*;
import com.mhs.onlinemarketingplatform.catalog.event.AddCategoryEvent;
import com.mhs.onlinemarketingplatform.catalog.event.UpdateCategoryEvent;
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
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.springframework.http.MediaType.IMAGE_PNG_VALUE;

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

    @DeleteMapping("/api/categories/{id}")
    ResponseEntity<?> delete(@PathVariable("id") UUID id) {
        this.categoryService.delete(id);
        return ResponseEntity.noContent().build();

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

    @GetMapping("/api/categories/{id}/with-sub-categories")
    ResponseEntity<CategoryDtoWithSubs> findACategoryWithSubCategoriesById(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(this.categoryService.findACategoryWithSubCategoriesById(id));
    }

    @GetMapping(value = "/api/categories/root")
    Page<RootCategoryResponse> findAllrootCategories(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return this.categoryService.findAllRootCategories(page,size);
    }

    @GetMapping("/api/categories/ancestors/{id}")
    List<Category> findAllAncestors(@PathVariable("id") UUID id) {
        return this.categoryService.findAncestors(id);
    }

    @GetMapping("/api/categories/descendants/{id}")
    List<Category> findAllDescendants(@PathVariable("id") UUID id) {
        return this.categoryService.findDescendants(id);
    }

    @PutMapping("/api/categories/{id}/activate")
    ResponseEntity<CategoryResponse> activate(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(this.categoryService.activate(id));
    }

    @PutMapping("/api/categories/{id}/deactivate")
    ResponseEntity<CategoryResponse> deactivate(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(this.categoryService.deactivate(id));
    }

    @PutMapping("/api/categories/{id}/versioned")
    ResponseEntity<?> activateOrDeactivateParentAndChildrenWithVersioning(
            @PathVariable("id") UUID id,
            @RequestBody UpdateCategoryStatusRequest request) {

        this.categoryService.activateOrDeactivateParentAndChildrenWithVersioning(id,request.categoryStatus());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/api/categories/image")
    ResponseEntity<String> uploadImage(@RequestParam("id") UUID id,@RequestParam("image") MultipartFile image) {
        return ResponseEntity.ok(this.categoryService.uploadImage(id,image));
    }

    @GetMapping(value = {"/api/categories/image/category/{imageName}"},produces = IMAGE_PNG_VALUE)
    byte[] getImage(@PathVariable("imageName") String imageName) throws Exception{
        return Files.readAllBytes(Paths.get("src/main/resources/image/category/"+this.categoryService.removeExtension(imageName)+"/"+imageName));
    }

    @GetMapping("/api/categories/root/count")
    long countRootCategories() {
        return this.categoryService.countRootCategories();
    }

}

@Service("categoryService")
@Transactional
class CategoryService implements CategoryApi {

    private static final Logger logger = LoggerFactory.getLogger(CategoryService.class);
    private final AuditLogger auditLogger;

    private final CategoryRepository categoryRepository;
    private final CategoryClosureRepository categoryClosureRepository;
    private final CatalogService catalogService;
    private final CategoryMapper categoryMapper;
    private final ApplicationEventPublisher publisher;
    private final MessageSource messageSource;
    private final ApiProperties properties;

    public CategoryService(
            AuditLogger auditLogger,
            CategoryRepository categoryRepository,
            CategoryClosureRepository categoryClosureRepository,
            CatalogService catalogService,
            CategoryMapper categoryMapper,
            ApplicationEventPublisher publisher,
            MessageSource messageSource,
            ApiProperties properties) {
        this.auditLogger = auditLogger;
        this.categoryRepository = categoryRepository;
        this.categoryClosureRepository = categoryClosureRepository;
        this.catalogService = catalogService;
        this.categoryMapper = categoryMapper;
        this.publisher = publisher;
        this.messageSource = messageSource;
        this.properties = properties;
    }

    @CacheEvict(value = "catalogs", allEntries = true)
    public CategoryResponse addAncestor(AddParentRequest addParentRequest) {
        logger.info("Creating new parent category with name: {}",addParentRequest.name());
        UUID catalogId = UUID.fromString(addParentRequest.catalogId());

        if(!this.catalogService.existsById(catalogId)) {
            throw new CatalogNotFoundException(
                    messageSource.getMessage("error.catalog.catalog.with.id.not.found",
                            new Object[]{catalogId},
                            LocaleContextHolder.getLocale()),
                            CatalogErrorCode.CATALOG_NOT_FOUND);}

        validateDuplicatesByNameAndSlug(addParentRequest,AddParentRequest::name,AddParentRequest::slug);

        Category mappedCategory = this.categoryMapper.mapAddParentToCategory(addParentRequest);

        Category storedCategory = this.categoryRepository.save(mappedCategory);
        this.categoryClosureRepository.insertSelf(storedCategory.id());
        this.auditLogger.log("PARENT_CTEGORY_CREATED", "CATEGORY", "Category NAME: " + storedCategory.name());

        this.publisher.publishEvent(new AddCategoryEvent(storedCategory.id()));
        return this.categoryMapper.mapCategoryToResponse(this.categoryRepository.findById(storedCategory.id()).orElseThrow());
    }

    @Caching(evict = {@CacheEvict(value = "catalogs", allEntries = true), @CacheEvict(value = "categories", allEntries = true)})
    public CategoryResponse addDescendant(AddChildRequest addChildRequest) {
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

        Category mappedCategory = this.categoryMapper.mapAddChildToCategory(addChildRequest);

        Category storedChildCategory = this.categoryRepository.save(mappedCategory);
        this.categoryClosureRepository.insertSelf(storedChildCategory.id());
        this.categoryClosureRepository.insertClosure(categoryId,storedChildCategory.id());
        this.auditLogger.log("CHILD_CATEGORY_CREATED", "CATEGORY", "Category NAME: " + storedChildCategory.name());

        this.publisher.publishEvent(new AddCategoryEvent(storedChildCategory.id()));
        return this.categoryMapper.mapCategoryToResponse(storedChildCategory);
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

        Category mappedCategory = this.categoryMapper.mapUpdateToCategory(updateParentRequest,existingCategory);
        Category storedCategory = this.categoryRepository.save(mappedCategory);
        this.auditLogger.log("CATALOG_UPDATED", "CATALOG", "Catalog NAME: " + storedCategory.name());

        this.publisher.publishEvent(new UpdateCategoryEvent(storedCategory.id()));
        return this.categoryMapper.mapCategoryToResponse(this.categoryRepository.findById(storedCategory.id()).orElseThrow());
    }

    @Caching(evict = {@CacheEvict(value = "catalogs", allEntries = true), @CacheEvict(value = "categories", allEntries = true)})
    public void delete(UUID id) {
        Category category = this.categoryRepository.findById(id)
                .orElseThrow(() ->  new CategoryNotFoundException(
                        messageSource.getMessage("error.category.category.with.id.not.found",
                                new Object[]{id},
                                LocaleContextHolder.getLocale()),
                        CategoryErrorCode.CATEGORY_NOT_FOUND));
        logger.info("Deleting exisiting category with id: {} and name: {}",category.id(),category.name());

        List<Category> descendants = findDescendants(id);
        if(!descendants.isEmpty()){
            logger.info("Category {} has descendants",category.name());
            return;
        }
        this.categoryRepository.delete(id);
        this.categoryRepository.deleteById(id);
        this.deleteImage(id);
        logger.info("Deleting exisiting category with name: {}",category.name());
    }

    CategoryResponse findById(UUID categoryId) {
        logger.info("Looking up category by ID: {}",categoryId);
        Category category = this.categoryRepository.findById(categoryId)
                .orElseThrow(() ->  new CategoryNotFoundException(
                        messageSource.getMessage("error.category.category.with.id.not.found",
                                new Object[]{categoryId},
                                LocaleContextHolder.getLocale()),
                                CategoryErrorCode.CATEGORY_NOT_FOUND));
        return this.categoryMapper.mapCategoryToResponse(category);
    }

    CategoryResponse findByName(String name) {
        logger.info("Looking up category by NAME: {}",name);
        Category category = this.categoryRepository.findByName(name)
                .orElseThrow(() ->  new CategoryNotFoundException(
                        messageSource.getMessage("error.category.category.with.name.not.found",
                                new Object[]{name},
                                LocaleContextHolder.getLocale()),
                                CategoryErrorCode.CATEGORY_NOT_FOUND));
        return this.categoryMapper.mapCategoryToResponse(category);
    }

    CategoryResponse findBySlug(String slug) {
        logger.info("Looking up category by SLUG: {}",slug);
        Category category = this.categoryRepository.findBySlug(slug)
                .orElseThrow(() ->  new CategoryNotFoundException(
                        messageSource.getMessage("error.category.category.with.slug.not.found",
                                new Object[]{slug},
                                LocaleContextHolder.getLocale()),
                                CategoryErrorCode.CATEGORY_NOT_FOUND));
        return this.categoryMapper.mapCategoryToResponse(category);
    }

    @Cacheable(key = "#id" , value = "categories")
    public CategoryDtoWithSubs findACategoryWithSubCategoriesById(UUID id) {
        logger.info("Retriving a category and its children by ID: {}",id);
        Category ancestor = this.categoryRepository.findById(id)
                .orElseThrow(() -> new CategoryNotFoundException(
                        messageSource.getMessage("error.category.category.with.id.not.found",
                                new Object[]{id},
                                LocaleContextHolder.getLocale()),
                        CategoryErrorCode.CATEGORY_NOT_FOUND));

        List<Category> descendants = this.categoryRepository.findDescendants(id);

        List<CategoryDtoWithSubs> descendantDto = descendants.stream()
                .map(categoryMapper::toCategoryDtoWithSubs)
                .toList();

        this.auditLogger.log("CATEGORY_RETRIEVED", "CATEGORY", "Category NAME: "+ancestor.name());
        return this.categoryMapper.toCategoryDtoWithSubs(ancestor, descendantDto);
    }

    Page<RootCategoryResponse> findAllRootCategories(int page, int size) {
        logger.info("Retriving all root categories");
        int offset = page * size;

        List<Category> categories = this.categoryRepository.findAllRootCategories(size, offset);

        if (categories.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(), PageRequest.of(page, size), 0);
        }

        long total = this.categoryRepository.countRootCategories();

        List<RootCategoryResponse> rootCategoryResponseList = this.categoryMapper.toRootCategoryResponseList(categories);

        this.auditLogger.log("ROOT_CATEGORIES_RETRIEVED_ALL", "CATEGORY", "Category TOTAL: "+total);
        return new PageImpl<>(
                rootCategoryResponseList,
                PageRequest.of(page,size),
                total
        );
    }

    CategoryResponse activate(UUID categoryId) {
        logger.info("Activate a category by ID: {}",categoryId);
        Category exsitingCategory = this.categoryRepository.findById(categoryId)
                .orElseThrow(() ->  new CategoryNotFoundException(
                        messageSource.getMessage("error.category.category.with.id.not.found",
                                new Object[]{categoryId},
                                LocaleContextHolder.getLocale()),
                                CategoryErrorCode.CATEGORY_NOT_FOUND));

        if(exsitingCategory.categoryStatus() == CategoryStatus.INACTIVE) {
            Category updatedCategory = Category.withCategoryStatusActivated(exsitingCategory);
            Category storedCategory = this.categoryRepository.save(updatedCategory);
            this.auditLogger.log("CATEGORY_ACTIVATED", "CATEGORY", "Category NAME: "+exsitingCategory.name());

            this.publisher.publishEvent(new UpdateCategoryEvent(storedCategory.id()));
            return this.categoryMapper.mapCategoryToResponse(storedCategory);
        }

        throw new CategoryAlreadyActivatedException(
                messageSource.getMessage("error.category.category.already.activated",
                        new Object[]{categoryId},
                        LocaleContextHolder.getLocale()),
                        CategoryErrorCode.CATEGORY_ALREADY_ACTIVATED);
    }

    CategoryResponse deactivate(UUID categoryId) {
        logger.info("Deactivate a category by ID: {}",categoryId);
        Category exsitingCategory = this.categoryRepository.findById(categoryId)
                .orElseThrow(() ->  new CategoryNotFoundException(
                        messageSource.getMessage("error.category.category.with.id.not.found",
                        new Object[]{categoryId},
                        LocaleContextHolder.getLocale()),
                        CategoryErrorCode.CATEGORY_NOT_FOUND));

        if(exsitingCategory.categoryStatus() == CategoryStatus.ACTIVE) {
            Category updatedCategory = Category.withCategoryStatusDeactivated(exsitingCategory);
            Category storedCategory = this.categoryRepository.save(updatedCategory);
            this.auditLogger.log("CATEGORY_DEACTIVATED", "CATEGORY", "Category NAME: "+exsitingCategory.name());
            this.publisher.publishEvent(new UpdateCategoryEvent(storedCategory.id()));
            return this.categoryMapper.mapCategoryToResponse(storedCategory);
        }

        throw new CategoryAlreadyDeactivatedException(
                messageSource.getMessage("error.category.category.already.deactivated",
                        new Object[]{categoryId},
                        LocaleContextHolder.getLocale()),
                        CategoryErrorCode.CATEGORY_ALREADY_DEACTIVATED);
    }

    List<Category> findDescendants(UUID id) {
        findById(id);
        List<Category> descendants = this.categoryRepository.findDescendants(id);
        if(descendants.isEmpty()){
            logger.info("Category {} has no descendants", id);
        }
        return descendants;
    }

    List<Category> findAncestors(UUID id) {
        findById(id);
        List<Category> ancestors = this.categoryRepository.findAncestors(id);
        if(ancestors.isEmpty()){
            logger.info("Category {} has no ancestors", id);
        }
        return ancestors;
    }

    void activateOrDeactivateParentAndChildrenWithVersioning(UUID id, CategoryStatus status) {
        Category category = this.categoryRepository.findById(id)
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
        List<Category> children = this.categoryRepository.findDescendantsForActivationOrDeactivationWithVersioning(categoryId);

        if (children.isEmpty()) {
            logger.info("Category {} has no descendants to deactivate", categoryId);
            return;
        }

        List<Category> deactivatedChildren = this.categoryMapper.toDeactivateList(children);
        this.categoryRepository.saveAll(deactivatedChildren);
        this.auditLogger.log("CHILDREN_CATEGORY_DEACTIVATED", "CATEGORY", "Category ID: "+categoryId);
    }

    void activateParentAndChildrenWithVersioning(UUID categoryId){
        activate(categoryId);
        List<Category> children = this.categoryRepository.findDescendantsForActivationOrDeactivationWithVersioning(categoryId);

        if (children.isEmpty()) {
            logger.info("Category {} has no descendants to deactivate", categoryId);
            return;
        }
        List<Category> deactivatedChildren = this.categoryMapper.toActivateList(children);
        this.categoryRepository.saveAll(deactivatedChildren);
        this.auditLogger.log("CHILDREN_CATEGORY_ACTIVATED", "CATEGORY", "Category ID: "+categoryId);
    }

    public String uploadImage(UUID categoryId, MultipartFile file) {
        logger.info("Uploading new photo for a category");
        Category category = this.categoryRepository.findById(categoryId)
                .orElseThrow(() ->  new CategoryNotFoundException(
                        messageSource.getMessage("error.category.category.with.id.not.found",
                                new Object[]{categoryId},
                                LocaleContextHolder.getLocale()),
                        CategoryErrorCode.CATEGORY_NOT_FOUND));

        String imageUrl = prepareImageUpload(categoryId,file);
        Category updatedCatalogWithImage = this.categoryMapper.mapCategoryWithImage(imageUrl,category);
        this.categoryRepository.save(updatedCatalogWithImage);
        this.auditLogger.log("CATEGORY_IMAGE_UPDATED", "CATEGORY", "Category NAME: "+category.name());
        return imageUrl;
    }

    public void deleteImage(UUID id) {
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

    private String prepareImageUpload(UUID id, MultipartFile image) {
        String imageName =  id + imageExtension(image.getOriginalFilename());
        try {

            Path categoryFolder = Paths.get(properties.getCategoryImagePath(),id.toString()).toAbsolutePath().normalize();
            if(!Files.exists(categoryFolder)) {
                Files.createDirectories(categoryFolder);
            }

            Path imagePath = categoryFolder.resolve(imageName);
            Files.copy(image.getInputStream(),imagePath,REPLACE_EXISTING);
            return ServletUriComponentsBuilder
                    .fromCurrentContextPath()
                    .path("/api/categories/image/category/" + imageName).toUriString();
        } catch (Exception e) {
            throw new RuntimeException("");
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

    @Cacheable(key = "'rootCategoryCount'",value = "categories")
    public long countRootCategories() {
        return this.categoryRepository.countRootCategories();
    }

    public boolean existsById(UUID categoryId){
        return this.categoryRepository.existsById(categoryId);
    }

}

@Repository("categoryRepository")
interface CategoryRepository extends CrudRepository<Category, UUID>{

    Optional<Category> findById(UUID id);

    Optional<Category> findByName(String name);

    Optional<Category> findBySlug(String slug);

    @Query("""
        SELECT
        c.id,
        c.name,
        c.slug,
        c.description,
        c.category_status,
        c.created_at,
        c.updated_at,
        c.image_url
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
                category.imageUrl,
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
                category.imageUrl,
                category.catalogId);
    }
}

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

record AddChildRequest(
        @NotNull String name,
        @NotBlank String description,
        @NotNull String slug,
        @NotNull String imageUrl,
        @NotNull String categoryId,
        @NotNull String catalogId) {}

record UpdateCategoryStatusRequest(CategoryStatus categoryStatus) {}

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
                category.imageUrl(),
                category.catalogId());
    }

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


