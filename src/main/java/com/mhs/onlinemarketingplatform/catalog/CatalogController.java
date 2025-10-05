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

import com.mhs.onlinemarketingplatform.catalog.error.CatalogAlreadyExistsException;
import com.mhs.onlinemarketingplatform.catalog.error.CatalogErrorCode;
import com.mhs.onlinemarketingplatform.catalog.error.CatalogNotFoundException;
import com.mhs.onlinemarketingplatform.catalog.event.AddCatalogEvent;
import com.mhs.onlinemarketingplatform.catalog.event.UpdateCatalogEvent;
import com.mhs.onlinemarketingplatform.common.AuditLogger;
import jakarta.validation.constraints.NotNull;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
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
import java.util.*;
import java.util.List;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/**
 * @author Milad Haghighat Shahedi
 */
@Controller("catalogController")
@ResponseBody
class CatalogController {

	private final CatalogService catalogService;

	CatalogController(CatalogService catalogService) {
		this.catalogService = catalogService;
	}

	@PostMapping("/api/catalogs")
	ResponseEntity<CatalogResponse> add(@RequestBody AddCatalogRequest addCatalogRequest) {
		return ResponseEntity.ok(this.catalogService.add(addCatalogRequest));
	}

	@PutMapping("/api/catalogs")
	ResponseEntity<CatalogResponse> update(@RequestBody UpdateCatalogRequest updateCatalogRequest) {
		return ResponseEntity.ok(this.catalogService.update(updateCatalogRequest));
	}

	@DeleteMapping("/api/catalogs/{id}")
	ResponseEntity<?> delete(@PathVariable("id") UUID id) {
		this.catalogService.delete(id);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/api/catalogs/{id}")
	ResponseEntity<CatalogResponse> findById(@PathVariable("id") UUID id) {
		return ResponseEntity.ok(this.catalogService.findById(id));
	}

	@GetMapping(value = "/api/catalogs",params = "name")
	ResponseEntity<CatalogResponse> findByName(@RequestParam("name") String name) {
		return ResponseEntity.ok(this.catalogService.findByName(name));
	}

	@GetMapping(value = "/api/catalogs",params = "slug")
	ResponseEntity<CatalogResponse> findBySlug(@RequestParam("slug") String slug) {
		return ResponseEntity.ok(this.catalogService.findBySlug(slug));
	}

	@GetMapping("/api/catalogs/{id}/with-root-categories")
	ResponseEntity<CatalogDto> findACatalogWithRootCategoriesById(@PathVariable("id") UUID id) {
		return ResponseEntity.ok(this.catalogService.findACatalogWithRootCategoriesById(id));
	}

	@GetMapping("/api/catalogs")
	CatalogPagedResponse<CatalogResponse> findAllOrderByCreatedAt(@PageableDefault(size = 6) Pageable pageable) {
		return this.catalogService.findAllOrderByCreatedAt(pageable);
	}

}

@Service("catalogService")
@Transactional
class CatalogService {

	private static final Logger logger = LoggerFactory.getLogger(CatalogService.class);
	private final AuditLogger auditLogger;

	private final CatalogRepository catalogRepository;
	private final CatalogMapper catalogMapper;
	private final ApplicationEventPublisher publisher;
	private final MessageSource messageSource;

	public CatalogService(
			AuditLogger auditLogger,
			CatalogRepository catalogRepository,
			CatalogMapper catalogMapper,
			ApplicationEventPublisher publisher,
			MessageSource messageSource) {
		this.auditLogger = auditLogger;
		this.catalogRepository = catalogRepository;
		this.catalogMapper = catalogMapper;
		this.publisher = publisher;
		this.messageSource = messageSource;
	}

	@CacheEvict(value = "catalogsPage", allEntries = true)
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
		this.publisher.publishEvent(new AddCatalogEvent(storedCatalog.id()));
		return this.catalogMapper.mapCatalogToResponse(storedCatalog);
	}

	@CacheEvict(value = "catalogsPage", allEntries = true)
	public CatalogResponse update(UpdateCatalogRequest updateCatalogRequest) {
		logger.info("Updating exisiting catalog with name: {}",updateCatalogRequest.name());
		UUID id = UUID.fromString(updateCatalogRequest.id());

		Catalog exisitngCatalog = this.catalogRepository.findById(id)
				.orElseThrow(() -> new CatalogNotFoundException(
						messageSource.getMessage("error.catalog.catalog.with.id.not.found",
								new Object[]{updateCatalogRequest.id()},
								LocaleContextHolder.getLocale()),
						CatalogErrorCode.CATALOG_NOT_FOUND));

		if(!exisitngCatalog.name().equals(updateCatalogRequest.name()) || !exisitngCatalog.slug().equals(updateCatalogRequest.slug())) {
			boolean exists = catalogRepository.existsByNameOrSlugAndNotId(updateCatalogRequest.name(), updateCatalogRequest.slug(), id);
			if (exists) {
				throw new CatalogAlreadyExistsException(
						messageSource.getMessage("error.catalog.catalog.with.duplicate.name.or.slug",
								new Object[]{updateCatalogRequest.name(),
										updateCatalogRequest.slug()},
								LocaleContextHolder.getLocale()),
						CatalogErrorCode.CATALOG_ALREADY_EXISTS);
			}
		}

		Catalog mappedCatalog = this.catalogMapper.mapUpdateRequestToCatalog(updateCatalogRequest,exisitngCatalog);
		Catalog storedCatalog = this.catalogRepository.save(mappedCatalog);
		this.auditLogger.log("CATEGORY_UPDATED", "CATEGORY", "Category NAME: " + storedCatalog.name());

		this.publisher.publishEvent(new UpdateCatalogEvent(storedCatalog.id()));
		return this.catalogMapper.mapCatalogToResponse(storedCatalog);
	}

	@CacheEvict(value = "catalogsPage", allEntries = true)
	public void delete(UUID id) {
		Catalog catalog = this.catalogRepository.findById(id)
				.orElseThrow(() -> new CatalogNotFoundException(
						messageSource.getMessage("error.catalog.catalog.with.id.not.found",
								new Object[]{id},
								LocaleContextHolder.getLocale()),
						CatalogErrorCode.CATALOG_NOT_FOUND));
		logger.info("Deleting exisiting catalog with id: {} and name: {}",catalog.id(),catalog.name());

		this.catalogRepository.delete(catalog);
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

	public String uploadImage(UUID catalogId, MultipartFile file) {
		logger.info("Uploading new photo by for a catalog");
		Catalog catalog = this.catalogRepository.findById(catalogId)
				.orElseThrow(() -> new CatalogNotFoundException(
						messageSource.getMessage("error.catalog.catalog.with.id.not.found",
								new Object[]{catalogId},
								LocaleContextHolder.getLocale()),
						CatalogErrorCode.CATALOG_NOT_FOUND));

		String imageUrl = prepareImageUpload(catalogId,file);
		Catalog updatedCatalogWithImage = this.catalogMapper.mapCatalogWithImage(imageUrl,catalog);
		this.catalogRepository.save(updatedCatalogWithImage);
		return imageUrl;
	}

	private String prepareImageUpload(UUID id, MultipartFile image) {
		String imageName =  id + imageExtension(image.getOriginalFilename());
		try {
			Path storageLocation = Paths.get("").toAbsolutePath().normalize();
			if(!Files.exists(storageLocation)) {
				Files.createDirectories(storageLocation);
			}
			Files.copy(image.getInputStream(),storageLocation.resolve(id + imageExtension(image.getOriginalFilename())),REPLACE_EXISTING);
			return ServletUriComponentsBuilder
					.fromCurrentContextPath()
					.path("/catalogs/image/" + imageName).toString();
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

	boolean existsById(UUID id) {
		return this.catalogRepository.existsById(id);
	}

}

@Repository("catalogRepository")
interface CatalogRepository extends ListCrudRepository<Catalog, UUID> {

	Optional<Catalog> findById(UUID id);

	Optional<Catalog> findBySlug(String slug);

	Optional<Catalog> findByName(String name);

	boolean existsByName(@Param("name") String name);

	boolean existsBySlug(@Param("slug") String slug);

	@Query("""
			SELECT COUNT(*) > 0 FROM Catalogs c
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
	        c.id     AS category_id,
	        c.name   AS category_name,
	        c.slug   AS category_slug,
	        c.description AS category_description,
	        c.category_status AS category_status,
	        c.created_at as category_created_at
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

record uploadCatalogImageRequest(
		@NotNull String id,
		@NotNull String imageUrl) {}

record CatalogResponse(
		String id,
		String name,
		String description,
		String slug,
		LocalDateTime createdAt,
		LocalDateTime updatedAt) {}

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
		LocalDateTime category_created_at
) {}

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
interface CatalogMapper {

	@Mapping(target = "id", expression = "java(java.util.UUID.randomUUID())")
	@Mapping(target = "version", ignore = true)
	@Mapping(target = "createdAt", expression = "java(java.time.LocalDateTime.now())")
	@Mapping(target = "updatedAt", ignore = true)
	Catalog mapAddRequestToCatalog(AddCatalogRequest addCatalogRequest);

	default Catalog mapUpdateRequestToCatalog(UpdateCatalogRequest request, Catalog  catalog) {
		return new Catalog(
				catalog.id(),
				catalog.version(),
				request.name() != null ? request.name() :catalog.name(),
				request.description() != null ? request.description() :catalog.description(),
				request.slug() != null ? request.slug() :catalog.slug(),
				catalog.createdAt(),
				LocalDateTime.now(),
				catalog.imageUrl()
				);
	}

	default Catalog mapCatalogWithImage(String imageUrl,Catalog catalog) {
		return new Catalog(
				catalog.id(),
				catalog.version(),
				catalog.name(),
				catalog.slug(),
				catalog.description(),
				catalog.createdAt(),
				LocalDateTime.now(),
				imageUrl
		);
	}

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
	@Mapping(target = "catalogId", source = "catalog_id")
	CategoryDto mapToCategoryDto(CatalogWithRootCategory row);
}


// controller // service // repository // model // enum // dto // mapper // exception


