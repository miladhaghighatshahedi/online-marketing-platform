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
import com.mhs.onlinemarketingplatform.catalog.config.ImagePathProperties;
import com.mhs.onlinemarketingplatform.catalog.error.CatalogAlreadyExistsException;
import com.mhs.onlinemarketingplatform.catalog.error.CatalogErrorCode;
import com.mhs.onlinemarketingplatform.catalog.error.CatalogNotFoundException;
import com.mhs.onlinemarketingplatform.common.AuditLogger;
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
import org.springframework.data.domain.Pageable;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.web.PageableDefault;
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
import java.util.*;
import java.util.List;

import static org.springframework.http.MediaType.IMAGE_PNG_VALUE;

/**
 * @author Milad Haghighat Shahedi
 */
@Controller("catalogController")
@ResponseBody
class CatalogController {

	private final CatalogService catalogService;
	private final CatalogImageUploadService catalogImageUploadService;


	public CatalogController(
			CatalogService catalogService,
			CatalogImageUploadService catalogImageUploadService) {
		this.catalogService = catalogService;
		this.catalogImageUploadService = catalogImageUploadService;
	}

	@PostMapping("/api/catalogs")
	ResponseEntity<CatalogApiResponse<CatalogResponse>> add(@RequestBody AddCatalogRequest addCatalogRequest) {
		CatalogResponse addedCatalog = this.catalogService.add(addCatalogRequest);
		return ResponseEntity.ok(new CatalogApiResponse<>(true,"Catalog saved successfully!",addedCatalog));
	}

	@PutMapping("/api/catalogs")
	ResponseEntity<CatalogApiResponse<CatalogResponse>> update(@RequestBody UpdateCatalogRequest updateCatalogRequest) {
		CatalogResponse updatedCatalog = this.catalogService.update(updateCatalogRequest);
		return ResponseEntity.ok(new CatalogApiResponse<>(true,"Catalog updated successfully!",updatedCatalog));
	}

	@PatchMapping("/api/catalogs")
	ResponseEntity<CatalogApiResponse<CatalogResponse>> patch(@RequestBody PatchCatalogRequest patchCatalogRequest) {
		CatalogResponse patchedCatalog = this.catalogService.patch(patchCatalogRequest);
		return ResponseEntity.ok(new CatalogApiResponse<>(true,"Catalog patched successfully!",patchedCatalog));
	}

	@DeleteMapping("/api/catalogs/{id}")
	ResponseEntity<?> delete(@PathVariable("id") UUID id) {
		this.catalogService.delete(id);
		return ResponseEntity.ok(new CatalogApiResponse<>(true,"Catalog deleted successfully!",null));
	}

	@GetMapping("/api/catalogs/{id}")
	ResponseEntity<CatalogApiResponse<CatalogResponse>> findById(@PathVariable("id") UUID id) {
		CatalogResponse foundCatalog = this.catalogService.findById(id);
		return ResponseEntity.ok(new CatalogApiResponse<>(true,"Catalog found successfully!",foundCatalog));
	}

	@GetMapping(value = "/api/catalogs",params = "name")
	ResponseEntity<CatalogApiResponse<CatalogResponse>> findByName(@RequestParam("name") String name) {
		CatalogResponse foundCatalog = this.catalogService.findByName(name);
		return ResponseEntity.ok(new CatalogApiResponse<>(true,"Catalog found successfully!",foundCatalog));
	}

	@GetMapping(value = "/api/catalogs",params = "slug")
	ResponseEntity<CatalogApiResponse<CatalogResponse>> findBySlug(@RequestParam("slug") String slug) {
		CatalogResponse foundCatalog = this.catalogService.findBySlug(slug);
		return ResponseEntity.ok(new CatalogApiResponse<>(true,"Catalog found successfully!",foundCatalog));
	}

	@GetMapping("/api/catalogs/{id}/with-root-categories")
	ResponseEntity<CatalogDto> findACatalogWithRootCategoriesById(@PathVariable("id") UUID id) {
		return ResponseEntity.ok(this.catalogService.findACatalogWithRootCategoriesById(id));
	}

	@GetMapping("/api/catalogs")
	ResponseEntity<CatalogApiResponse<List<CatalogResponse>>> findAll() {
		List<CatalogResponse> foundCatalogs = this.catalogService.findAll();
		return ResponseEntity.ok(new CatalogApiResponse<>(true,"Catalogs found successfully!",foundCatalogs));
	}

	@GetMapping("/api/catalogs/ordered")
	CatalogPagedResponse<CatalogResponse> findAllOrderByCreatedAt(@PageableDefault(size = 6) Pageable pageable) {
		return this.catalogService.findAllOrderByCreatedAt(pageable);
	}

	@PutMapping("/api/catalogs/image")
	ResponseEntity<String> uploadImage(@RequestParam("id") UUID id,@RequestParam("image") MultipartFile image) {
		this.catalogService.uploadImage(id,image);
		return ResponseEntity.accepted().body("Image upload accepted: processing asynchronously...");
	}

	@GetMapping(value = "/api/catalogs/image/catalog/{imageName}",produces = IMAGE_PNG_VALUE)
	byte[] getImage(@PathVariable("imageName") String imageName) throws Exception{
		return Files.readAllBytes(Paths.get("src/main/resources/image/catalog/"+this.catalogImageUploadService.removeExtension(imageName)+"/"+imageName));
	}

}

@Service("catalogService")
@Transactional
class CatalogService {

	private static final Logger logger = LoggerFactory.getLogger(CatalogService.class);
	private final AuditLogger auditLogger;

	private final CatalogRepository catalogRepository;
	private final CatalogMapper catalogMapper;
	private final MessageSource messageSource;
	private final ImagePathProperties imagePathProperties;
	private final CatalogImageUploadService catalogImageUploadService;

	public CatalogService(
			AuditLogger auditLogger,
			CatalogRepository catalogRepository,
			CatalogMapper catalogMapper,
			MessageSource messageSource,
			ImagePathProperties imagePathProperties,
			CatalogImageUploadService catalogImageUploadService) {
		this.auditLogger = auditLogger;
		this.catalogRepository = catalogRepository;
		this.catalogMapper = catalogMapper;
		this.messageSource = messageSource;
		this.imagePathProperties = imagePathProperties;
		this.catalogImageUploadService = catalogImageUploadService;
	}


	@Caching(evict = {
			@CacheEvict(value = "catalogsPage", allEntries = true), @CacheEvict(value = "catalog", allEntries = true),
			@CacheEvict(value = "catalog-name", allEntries = true), @CacheEvict(value = "catalog-slug", allEntries = true),
			@CacheEvict(value = "catalogs", allEntries = true), @CacheEvict(value = "catalogsPage", allEntries = true)
	})
	public CatalogResponse add(AddCatalogRequest addCatalogRequest) {
		logger.info("Creating new catalog with name: {}",addCatalogRequest.name());

		boolean existsByName = this.catalogRepository.existsByName(addCatalogRequest.name());
		boolean existsBySlug = this.catalogRepository.existsBySlug(addCatalogRequest.slug());

		if(existsByName || existsBySlug) {
			throw new CatalogAlreadyExistsException(
					messageSource.getMessage("error.catalog.catalog.already.exists",
							new Object[]{addCatalogRequest.name(),
									addCatalogRequest.slug()},
							LocaleContextHolder.getLocale()),
					CatalogErrorCode.CATALOG_ALREADY_EXISTS);}

		Catalog mappedCatalog = this.catalogMapper.mapAddRequestToCatalog(addCatalogRequest);
		Catalog storedCatalog = this.catalogRepository.save(mappedCatalog);
		this.auditLogger.log("CATALOG_CREATED", "CATALOG", "Catalog ID: " + storedCatalog.id());

		return this.catalogMapper.mapCatalogToResponse(storedCatalog);
	}

	@Caching(evict = {
			@CacheEvict(value = "catalogsPage", allEntries = true), @CacheEvict(value = "catalog", allEntries = true),
			@CacheEvict(value = "catalog-name", allEntries = true), @CacheEvict(value = "catalog-slug", allEntries = true),
			@CacheEvict(value = "catalogs", allEntries = true), @CacheEvict(value = "catalogsPage", allEntries = true)
	})
	public CatalogResponse update(UpdateCatalogRequest updateCatalogRequest) {
		logger.info("Updating exisiting catalog with name: {}", updateCatalogRequest.name());
		UUID id = UUID.fromString(updateCatalogRequest.id());

		Catalog exisitngCatalog = this.catalogRepository.findById(id).orElseThrow(() ->
				new CatalogNotFoundException(
						messageSource.getMessage("error.catalog.catalog.with.id.not.found",
								new Object[]{updateCatalogRequest.id()},
								LocaleContextHolder.getLocale()),
						CatalogErrorCode.CATALOG_NOT_FOUND));

		if (! exisitngCatalog.name().equals(updateCatalogRequest.name()) || ! exisitngCatalog.slug().equals(updateCatalogRequest.slug())) {
			boolean exists = catalogRepository.existsByNameOrSlugAndNotId(updateCatalogRequest.name(), updateCatalogRequest.slug(), id);
			if (exists) {
				throw new CatalogAlreadyExistsException(
						messageSource.getMessage("error.catalog.catalog.with.duplicate.name.or.slug",
								new Object[]{updateCatalogRequest.name(), updateCatalogRequest.slug()},
								LocaleContextHolder.getLocale()),
						CatalogErrorCode.CATALOG_ALREADY_EXISTS);
			}
		}

		Catalog mappedCatalog = this.catalogMapper.mapUpdateRequestToCatalog(updateCatalogRequest,exisitngCatalog);
		Catalog storedCatalog = this.catalogRepository.save(mappedCatalog);
		this.auditLogger.log("CATEGORY_UPDATED", "CATEGORY", "Category NAME: " + storedCatalog.name());

		return this.catalogMapper.mapCatalogToResponse(storedCatalog);

	}

	@Caching(evict = {
			@CacheEvict(value = "catalogsPage", allEntries = true), @CacheEvict(value = "catalog", allEntries = true),
			@CacheEvict(value = "catalog-name", allEntries = true), @CacheEvict(value = "catalog-slug", allEntries = true),
			@CacheEvict(value = "catalogs", allEntries = true), @CacheEvict(value = "catalogsPage", allEntries = true)
	})
	public CatalogResponse patch(PatchCatalogRequest patchCatalogRequest) {
		logger.info("Patching exisiting catalog with name: {}",patchCatalogRequest.name());
		UUID id = UUID.fromString(patchCatalogRequest.id());


		Catalog exisitngCatalog = this.catalogRepository.findById(id)
				.orElseThrow(() -> new CatalogNotFoundException(
						messageSource.getMessage("error.catalog.catalog.with.id.not.found",
								new Object[]{patchCatalogRequest.id()},
								LocaleContextHolder.getLocale()),
						CatalogErrorCode.CATALOG_NOT_FOUND));

		String existingName = exisitngCatalog.name();
		String existingSlug = exisitngCatalog.slug();

		if(patchCatalogRequest.name() != null && !existingName.equals(patchCatalogRequest.name())) {
			boolean exists = this.catalogRepository.existsByName(patchCatalogRequest.name());
			if(exists) {
				throw new CatalogAlreadyExistsException(
						messageSource.getMessage("error.catalog.catalog.with.name.exists",
								new Object[]{patchCatalogRequest.name()},
								LocaleContextHolder.getLocale()),
						CatalogErrorCode.CATALOG_ALREADY_EXISTS);}}

		if(patchCatalogRequest.slug() != null && !existingSlug.equals(patchCatalogRequest.slug())) {
			boolean exists = this.catalogRepository.existsBySlug(patchCatalogRequest.slug());
			if(exists) {
				throw new CatalogAlreadyExistsException(
						messageSource.getMessage("error.catalog.catalog.with.slug.exists",
								new Object[]{patchCatalogRequest.slug()},
								LocaleContextHolder.getLocale()),
						CatalogErrorCode.CATALOG_ALREADY_EXISTS);}}

		Catalog mappedCatalog = this.catalogMapper.mapPatchRequestToCatalog(patchCatalogRequest,exisitngCatalog);
		Catalog storedCatalog = this.catalogRepository.save(mappedCatalog);
		this.auditLogger.log("CATEGORY_UPDATED", "CATEGORY", "Category NAME: " + storedCatalog.name());

		return this.catalogMapper.mapCatalogToResponse(storedCatalog);
	}

	@Caching(evict = {
			@CacheEvict(value = "catalogsPage", allEntries = true), @CacheEvict(value = "catalog", allEntries = true),
			@CacheEvict(value = "catalog-name", allEntries = true), @CacheEvict(value = "catalog-slug", allEntries = true),
			@CacheEvict(value = "catalogs", allEntries = true), @CacheEvict(value = "catalogsPage", allEntries = true)
	})
	public void delete(UUID id) {
		Catalog catalog = this.catalogRepository.findById(id)
				.orElseThrow(() -> new CatalogNotFoundException(
						messageSource.getMessage("error.catalog.catalog.with.id.not.found",
								new Object[]{id},
								LocaleContextHolder.getLocale()),
						CatalogErrorCode.CATALOG_NOT_FOUND));
		logger.info("Deleting exisiting catalog with id: {} and name: {}",catalog.id(),catalog.name());
		this.catalogRepository.delete(catalog);
		this.deleteImage(catalog.id());
		this.auditLogger.log("CATALOG_DELETED", "CATALOG", "Catalog NAME: " + catalog.name());
	}

	@Cacheable(key = "#id" , value = "catalog")
	public CatalogResponse findById(UUID id) {
		logger.info("Looking up catalog by ID: {}",id);
		Catalog catalog = this.catalogRepository.findById(id)
				.orElseThrow(() -> new CatalogNotFoundException(
						messageSource.getMessage("error.catalog.catalog.with.id.not.found",
								new Object[]{id},
								LocaleContextHolder.getLocale()),
						CatalogErrorCode.CATALOG_NOT_FOUND));

		return this.catalogMapper.mapCatalogToResponse(catalog);
	}

	@Cacheable(key = "#name" , value = "catalog-name")
	public CatalogResponse findByName(String name) {
		logger.info("Looking up catalog by NAME: {}",name);
		Catalog catalog = this.catalogRepository.findByName(name)
				.orElseThrow(() -> new CatalogNotFoundException(
						messageSource.getMessage("error.catalog.catalog.with.name.not.found",
								new Object[]{name},
								LocaleContextHolder.getLocale()),
						CatalogErrorCode.CATALOG_NOT_FOUND));

		return this.catalogMapper.mapCatalogToResponse(catalog);
	}

	@Cacheable(key = "#slug" , value = "catalog-slug")
	public CatalogResponse findBySlug(String slug) {
		logger.info("Looking up catalog by SLUG: {}",slug);
		Catalog catalog = this.catalogRepository.findBySlug(slug)
				.orElseThrow(() -> new CatalogNotFoundException(
						messageSource.getMessage("error.catalog.catalog.with.slug.not.found",
								new Object[]{slug},
								LocaleContextHolder.getLocale()),
						CatalogErrorCode.CATALOG_NOT_FOUND));

		return this.catalogMapper.mapCatalogToResponse(catalog);
	}

	@Cacheable(value = "catalogs")
	public List<CatalogResponse> findAll() {
		logger.info("Retriving all catalogs ");
		List<Catalog> exisitingCatalogs = this.catalogRepository.findAll();
		return this.catalogMapper.mapListToListOfResponse(exisitingCatalogs);
	}

	@Cacheable(key = "#id",value = "catalogs")
	public CatalogDto findACatalogWithRootCategoriesById(UUID id) {
		logger.info("Retriving a catalog and its children by ID: {}",id);
		List<CatalogWithRootCategory> rows = this.catalogRepository.findACatalogWithRootCategoriesById(id);

		if(rows == null || rows.isEmpty()) {
			throw new CatalogNotFoundException(
					messageSource.getMessage("error.catalog.catalog.with.id.not.found",
							new Object[]{id},
							LocaleContextHolder.getLocale()),
					CatalogErrorCode.CATALOG_NOT_FOUND);}

		CatalogWithRootCategory catalog = rows.get(0);
		List<CategoryDto> categories = rows.stream()
				.filter(row -> row.category_id() != null)
				.map(catalogMapper::mapToCategoryDto)
				.distinct()
				.toList();

		this.auditLogger.log("CATALOG_RETRIEVED", "CATALOG", "Catalog NAME: "+catalog.catalog_name());
		return this.catalogMapper.mapToCatalogDto(catalog,categories);
	}

	@Cacheable(key = "#pageable.pageNumber + '-' + #pageable.pageSize",value = "catalogsPage")
	public CatalogPagedResponse<CatalogResponse> findAllOrderByCreatedAt(Pageable pageable) {
		logger.info("Retriving all catalogs");
		Page<Catalog> catalogs = this.catalogRepository.findAllByOrderByCreatedAt(pageable);
		this.auditLogger.log("CATALOGS_RETRIEVED_ALL", "CATALOG", "Catalog TOTAL: "+catalogs.getTotalElements());
		return this.catalogMapper.mapCatalogToPagedResponse(catalogs);
	}

	public void uploadImage(UUID catalogId, MultipartFile image) {
		logger.info("Uploading a new photo for a catalog with the ID {}",catalogId);
		 if(!this.catalogRepository.existsById(catalogId)) {
			 throw new CatalogNotFoundException(
					 messageSource.getMessage("error.catalog.catalog.with.id.not.found",
							 new Object[]{catalogId},
							 LocaleContextHolder.getLocale()),
					 CatalogErrorCode.CATALOG_NOT_FOUND);}

		Path imageBasePath = this.catalogImageUploadService.createMainDirectoryIfNotExists(catalogId,imagePathProperties.getCatalogImagePath());
        byte[] file = this.catalogImageUploadService.preloadImage(image);
		String baseUrl = ServletUriComponentsBuilder.fromCurrentContextPath().toUriString();

		this.catalogImageUploadService.storeImagesIntoFileSystemAsync(catalogId, file, imageBasePath, baseUrl);
	}

	public void deleteImage(UUID id) {
		logger.info("Deleting exisiting directory with id: {} ",id);
		try {
			Path catalogFolder = Paths.get(imagePathProperties.getCatalogImagePath(),id.toString()).toAbsolutePath().normalize();

			if(Files.exists(catalogFolder)) {
				FileUtils.deleteDirectory(catalogFolder.toFile());
			}

			this.auditLogger.log("CATALOG_DIRECTORY_DELETED", "CATALOG", "Catalog directory: " + id);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	boolean existsById(UUID id) {
		return this.catalogRepository.existsById(id);
	}

}

@Service
class CatalogImageUploadService {

	private static final Logger logger = LoggerFactory.getLogger(CatalogImageUploadService.class);
	private final AuditLogger auditLogger;
	private final ApplicationEventPublisher applicationEventPublisher;

	public CatalogImageUploadService(
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

	String imageExtension(String imageName) {
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
			return baseUrl + "/api/catalogs/image/catalog/" + imageName;
		} catch (Exception e) {
			throw new RuntimeException("Exception happened while storing image in to the advertisement directory",e);
		}
	}

	@Async("catalogImageTaskExecutor")
	public void storeImagesIntoFileSystemAsync(UUID imageId, byte[] imageFile,Path imageBasePath,String baseUrl){
		logger.info("Calling ASYNC method with thread {} to store image with name {} and size {} ",Thread.currentThread().getName(),imageId,imageFile.length);
		String storedImageUrl = null;
		try {
			storedImageUrl = prepareImageUpload(imageId, imageFile, imageBasePath,baseUrl);
			this.auditLogger.log("ASYNC_CATALOG_IMAGE_FILE_STORED", "ASYNC_CATALOG_IMAGE_FILE", "ASYNC_CATALOG_IMAGE_FILE stored with the url " + storedImageUrl);
			this.applicationEventPublisher.publishEvent(new UpdateCatalogImageUrlEvent(imageId,storedImageUrl));
		} catch (Exception e) {
			logger.error("Exception happened while stroing the image asynchronously",e);
		}
		this.auditLogger.log("ASYNC_CATALOG_IMAGE_FILE_STORED", "ASYNC_CATALOG_IMAGE_FILE_STORED", "ASYNC_CATALOG_IMAGE_FILE_STORED one image stored successfully = " + imageId);
		logger.info("One image processed successfully with the name {}",imageId);
	}

}

@Repository("catalogRepository")
interface CatalogRepository extends ListCrudRepository<Catalog, UUID> {

	@Query("SELECT id,name FROM catalogs ORDER BY created_at")
	List<Catalog> findAll();

	Optional<Catalog> findById(UUID id);

	Optional<Catalog> findBySlug(String slug);

	Optional<Catalog> findByName(String name);

	boolean existsByName(@Param("name") String name);

	boolean existsBySlug(@Param("slug") String slug);

	@Query("""
			SELECT COUNT(*) > 0 FROM catalogs c
			 WHERE (c.name = :name OR c.slug = :slug) AND c.id <> :id
			 """)
	boolean existsByNameOrSlugAndNotId(@Param("name") String name, @Param("slug") String slug, @Param("id") UUID id);

	Page<Catalog> findAllByOrderByCreatedAt(Pageable pageable);

	@Query(""" 
	        SELECT
	        cat.id   AS catalog_id,
	        cat.name AS catalog_name,
	        cat.slug AS catalog_slug,
	        cat.description AS catalog_description,
	        cat.created_at AS catalog_created_at,
	        cat.updated_at AS catalog_updated_at,
	        cat.image_url AS catalog_image_url,
	        c.id     AS category_id,
	        c.name   AS category_name,
	        c.slug   AS category_slug,
	        c.description AS category_description,
	        c.category_status AS category_status,
	        c.created_at as category_created_at,
	        c.image_url as category_image_url
	        FROM catalogs cat
	        LEFT JOIN categories c ON cat.id = c.catalog_id
	        LEFT JOIN category_closure cc ON cc.child_id = c.id AND cc.depth = 1
	        WHERE cat.id = :catalogId AND cc.parent_id is NULL
            order by c.created_at
    """)
	List<CatalogWithRootCategory> findACatalogWithRootCategoriesById(@Param("catalogId") UUID id);

	@Query("SELECT COUNT(*) FROM catalogs")
	long countCatalogs();

}

@Table("catalogs")
record Catalog(
		@Id UUID id,
		@Version Integer version,
		String name,
		String slug,
		String description,
		LocalDateTime createdAt,
		LocalDateTime updatedAt,
		String imageUrl
) {}

record AddCatalogRequest(
		@NotNull String name,
		@NotNull String description,
		@NotNull String slug) {}

record UpdateCatalogRequest(
		@NotNull String id,
		@NotNull String name,
		@NotNull String description,
		@NotNull String slug) {}

record PatchCatalogRequest(
		@NotNull String id,
		String name,
		String description,
		String slug) {}

@JsonInclude(JsonInclude.Include.NON_NULL)
record CatalogResponse(
		String id,
		String name,
		String description,
		String slug,
		LocalDateTime createdAt,
		LocalDateTime updatedAt,
		String imageUrl) {}

record CatalogPagedResponse<T>(
		List<T> content,
		int page,
		int size,
		long totalElements,
		int totalPages) {}

record CatalogDto(
		UUID id,
		String name,
		String slug,
		String description,
		LocalDateTime createdAt,
		LocalDateTime updatedAt,
		String imageUrl,
		List<CategoryDto> rootCategories
) {}

record CategoryDto(
		UUID id,
		String name,
		String slug,
		String description,
		String status,
		LocalDateTime createdAt,
		String imageUrl,
		UUID catalogId
) {}

record CatalogWithRootCategory(
		UUID catalog_id,
		String catalog_name,
		String catalog_slug,
		String catalog_description,
		LocalDateTime catalog_created_at,
		LocalDateTime catalog_updated_at,
		String catalog_image_url,
		UUID category_id,
		String category_name,
		String category_slug,
		String category_description,
		String category_status,
		LocalDateTime category_created_at,
		String category_image_url
) {}

record CatalogApiResponse<T>(
		boolean response,
		String message,
		T data
) {}

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE,imports = {UuidCreator.class, LocalDateTime.class})
interface CatalogMapper {

	@Mapping(target = "id", expression = "java(UuidCreator.getTimeOrderedEpoch())")
	@Mapping(target = "version", ignore = true)
	@Mapping(target = "createdAt", expression = "java(LocalDateTime.now())")
	@Mapping(target = "updatedAt", ignore = true)
	Catalog mapAddRequestToCatalog(AddCatalogRequest addCatalogRequest);

	@Mapping(target = "id", source = "catalog.id")
	@Mapping(target = "version", source = "catalog.version")
	@Mapping(target = "name", expression = "java(request.name() != null ? request.name() : catalog.name())")
	@Mapping(target = "slug", expression = "java(request.slug() != null ? request.slug() : catalog.slug())")
	@Mapping(target = "description", expression = "java(request.description() != null ? request.description() : catalog.description())")
	@Mapping(target = "createdAt", source = "catalog.createdAt")
	@Mapping(target = "updatedAt", expression = "java(LocalDateTime.now())")
	@Mapping(target = "imageUrl",source = "catalog.imageUrl")
	Catalog mapUpdateRequestToCatalog(UpdateCatalogRequest request,Catalog catalog);

	@Mapping(target = "id", source = "catalog.id")
	@Mapping(target = "version", source = "catalog.version")
	@Mapping(target = "name", expression = "java(request.name() != null ? request.name() : catalog.name())")
	@Mapping(target = "slug", expression = "java(request.slug() != null ? request.slug() : catalog.slug())")
	@Mapping(target = "description", expression = "java(request.description() != null ? request.description() : catalog.description())")
	@Mapping(target = "createdAt", source = "catalog.createdAt")
	@Mapping(target = "updatedAt", expression = "java(LocalDateTime.now())")
	@Mapping(target = "imageUrl", source = "catalog.imageUrl")
	Catalog mapPatchRequestToCatalog(PatchCatalogRequest request,Catalog catalog);

	@Mapping(target = "updatedAt", expression = "java(LocalDateTime.now())")
	@Mapping(target = "version", source = "catalog.version")
	@Mapping(target = "imageUrl", source = "newImageUrl")
	Catalog mapCatalogWithImage(String newImageUrl,Catalog catalog);

	CatalogResponse mapCatalogToResponse(Catalog catalog);

	default CatalogPagedResponse<CatalogResponse> mapCatalogToPagedResponse(Page<Catalog> page) {
		return new CatalogPagedResponse<>(
				page.getContent().stream().map(this::mapCatalogToResponse).toList(),
				page.getNumber(),
				page.getSize(),
				page.getTotalElements(),
				page.getTotalPages());
	}

	@Mapping(target = "id", source = "firstRow.catalog_id")
	@Mapping(target = "name", source = "firstRow.catalog_name")
	@Mapping(target = "slug", source = "firstRow.catalog_slug")
	@Mapping(target = "description", source = "firstRow.catalog_description")
	@Mapping(target = "createdAt", source = "firstRow.catalog_created_at")
	@Mapping(target = "updatedAt", source = "firstRow.catalog_updated_at")
	@Mapping(target = "imageUrl", source = "firstRow.catalog_image_url")
	@Mapping(target = "rootCategories", source = "categories")
	CatalogDto mapToCatalogDto(CatalogWithRootCategory firstRow,List<CategoryDto> categories);

	@Mapping(target = "id", source = "category_id")
	@Mapping(target = "name", source = "category_name")
	@Mapping(target = "slug", source = "category_slug")
	@Mapping(target = "description", source = "category_description")
	@Mapping(target = "status", source = "category_status")
	@Mapping(target = "createdAt", source = "category_created_at")
	@Mapping(target = "imageUrl", source = "category_image_url")
	@Mapping(target = "catalogId", source = "catalog_id")
	CategoryDto mapToCategoryDto(CatalogWithRootCategory row);

	List<CatalogResponse> mapListToListOfResponse(Iterable<Catalog> catalogs);

}

// controller // service // repository // model // enum // dto // mapper // exception

record UpdateCatalogImageUrlEvent(UUID id,String url) {}

@Component
class UpdateCatalogImageUrlEventHandler {

	private final MessageSource messageSource;
	private final AuditLogger auditLogger;
    private final CatalogRepository catalogRepository;
	private final CatalogMapper catalogMapper;

	public UpdateCatalogImageUrlEventHandler(
			MessageSource messageSource,
			AuditLogger auditLogger,
			CatalogRepository catalogRepository,
			CatalogMapper catalogMapper) {
		this.messageSource = messageSource;
		this.auditLogger = auditLogger;
		this.catalogRepository = catalogRepository;
		this.catalogMapper = catalogMapper;
	}

	@Transactional
	@EventListener
	public void handleImageStored(UpdateCatalogImageUrlEvent event) {
		Catalog catalog = this.catalogRepository.findById(event.id())
				.orElseThrow(() -> new CatalogNotFoundException(
						messageSource.getMessage("error.catalog.catalog.with.id.not.found",
								new Object[]{event.id()},
								LocaleContextHolder.getLocale()),
						CatalogErrorCode.CATALOG_NOT_FOUND));

		Catalog updatedCatalogWithImage = this.catalogMapper.mapCatalogWithImage(event.url(),catalog);
		this.catalogRepository.save(updatedCatalogWithImage);
		this.auditLogger.log("CATALOG_IMAGE_FILE_UPDATED", "CATALOG_IMAGE_UPLOAD_EVENT", "CATALOG_IMAGE_FILE updated for the Id: " + event.id());
	}
}
