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

import com.mhs.onlinemarketingplatform.catalog.event.AddCatalogEvent;
import com.mhs.onlinemarketingplatform.catalog.event.UpdateCatalogEvent;
import jakarta.validation.constraints.NotNull;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
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

import java.time.LocalDateTime;
import java.util.*;

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

	@GetMapping(value = "/api/catalogs/with-root-categories")
	Page<CatalogDto> findAllWithRootCategories(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
		return this.catalogService.findAllWithRootCategories(page,size);
	}

	@GetMapping("/api/catalogs")
	CatalogPagedResponse<CatalogResponse> findAll(@PageableDefault(size = 20) Pageable pageable) {
		return this.catalogService.findAll(pageable);
	}

	@DeleteMapping("/api/catalogs/{id}")
	ResponseEntity<?> deleteById(@PathVariable("id") UUID id) {
		this.catalogService.delete(id);
		return ResponseEntity.noContent().build();
	}

}

@Service("catalogService")
@Transactional
class CatalogService {

	private final CatalogRepository catalogRepository;
	private final CatalogMapper catalogMapper;
	private final ApplicationEventPublisher publisher;
	private final MessageSource messageSource;

	public CatalogService(
			CatalogRepository catalogRepository,
			CatalogMapper catalogMapper,
			ApplicationEventPublisher publisher,
			MessageSource messageSource) {
		this.catalogRepository = catalogRepository;
		this.catalogMapper = catalogMapper;
		this.publisher = publisher;
		this.messageSource = messageSource;
	}

	CatalogResponse add(AddCatalogRequest addCatalogRequest) {
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
		this.publisher.publishEvent(new AddCatalogEvent(storedCatalog.id()));
		return this.catalogMapper.mapCatalogToResponse(storedCatalog);
	}

	CatalogResponse update(UpdateCatalogRequest updateCatalogRequest) {
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
		this.publisher.publishEvent(new UpdateCatalogEvent(storedCatalog.id()));
		return this.catalogMapper.mapCatalogToResponse(storedCatalog);
	}

	CatalogResponse findById(UUID id) {
		Catalog catalog = this.catalogRepository.findById(id)
				.orElseThrow(() -> new CatalogNotFoundException(
						messageSource.getMessage("error.catalog.catalog.with.id.not.found",
								new Object[]{id},
								LocaleContextHolder.getLocale()),
						CatalogErrorCode.CATALOG_NOT_FOUND));

		return this.catalogMapper.mapCatalogToResponse(catalog);
	}

	CatalogResponse findByName(String name) {
		Catalog catalog = this.catalogRepository.findByName(name)
				.orElseThrow(() -> new CatalogNotFoundException(
						messageSource.getMessage("error.catalog.catalog.with.name.not.found",
								new Object[]{name},
								LocaleContextHolder.getLocale()),
						CatalogErrorCode.CATALOG_NOT_FOUND));

		return this.catalogMapper.mapCatalogToResponse(catalog);
	}

	CatalogResponse findBySlug(String slug) {
		Catalog catalog = this.catalogRepository.findBySlug(slug)
				.orElseThrow(() -> new CatalogNotFoundException(
						messageSource.getMessage("error.catalog.catalog.with.slug.not.found",
								new Object[]{slug},
								LocaleContextHolder.getLocale()),
						CatalogErrorCode.CATALOG_NOT_FOUND));

		return this.catalogMapper.mapCatalogToResponse(catalog);
	}

	Page<CatalogDto> findAllWithRootCategories(int page, int size) {
		int offset = page * size;

		List<CatalogWithRootCategory> rows = this.catalogRepository.findAllWithRootCategories(size, offset);

		Map<UUID, CatalogDto> catalogMap = new LinkedHashMap<>();
		for (CatalogWithRootCategory row : rows) {
			catalogMap.computeIfAbsent(
					row.catalog_id(), id -> new CatalogDto(
							row.catalog_id(),
							row.catalog_name(),
							row.catalog_slug(),
							row.catalog_description(),
							row.catalog_created_at(),
							row.catalog_updated_at(),
							new ArrayList<>()));


			if (row.catalog_id() != null) {
				catalogMap.get(row.catalog_id()).rootCategories().add(new CategoryDto(row.category_id(), row.category_name(), row.category_slug()));
			}
		}

		long total = catalogRepository.countCatalogs();

		return new PageImpl<>(new ArrayList<>(catalogMap.values()), PageRequest.of(page, size), total);
	}

	CatalogPagedResponse<CatalogResponse> findAll(Pageable pageable) {
		Page<Catalog> catalogs = this.catalogRepository.findAll(pageable);
		return this.catalogMapper.mapCatalogToPagedResponse(catalogs);
	}

	void delete(UUID id) {
		Catalog catalog = this.catalogRepository.findById(id)
				.orElseThrow(() -> new CatalogNotFoundException(
						messageSource.getMessage("error.catalog.catalog.with.id.not.found",
								new Object[]{id},
								LocaleContextHolder.getLocale()),
						CatalogErrorCode.CATALOG_NOT_FOUND));

		this.catalogRepository.delete(catalog);
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

	Page<Catalog> findAll(Pageable pageable);

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
		c.slug   AS category_slug
		FROM catalogs cat
		LEFT JOIN categories c ON cat.id = c.catalog_id
		LEFT JOIN category_closure cc ON cc.child_id = c.id AND cc.depth = 1 WHERE cc.parent_id is NULL
		ORDER BY cat.created_at LIMIT :limit OFFSET :offset
	""")
	List<CatalogWithRootCategory> findAllWithRootCategories(int limit, int offset);

	boolean existsByName(@Param("name") String name);

	boolean existsBySlug(@Param("slug") String slug);

	@Query("SELECT COUNT(*) > 0 FROM Catalogs c WHERE (c.name = :name OR c.slug = :slug) AND c.id <> :id")
	boolean existsByNameOrSlugAndNotId(@Param("name") String name, @Param("slug") String slug, @Param("id") UUID id);

	@Query("SELECT COUNT(*) FROM catalogs")
	long countCatalogs();

}

@Table("catalogs")
record Catalog(
		@Id UUID id,
		@Version Integer version,
		String name,
		String description,
		String slug,
		LocalDateTime createdAt,
		LocalDateTime updatedAt
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
		List<CategoryDto> rootCategories
) {}

record CategoryDto(
		UUID id,
		String name,
		String slug
) {}

record CatalogWithRootCategory(
		UUID catalog_id,
		String catalog_name,
		String catalog_slug,
		String catalog_description,
		LocalDateTime catalog_created_at,
		LocalDateTime catalog_updated_at,
		UUID category_id,
		String category_name,
		String category_slug
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
				LocalDateTime.now());
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
}

class CatalogNotFoundException extends RuntimeException {
	private final CatalogErrorCode code;

	public CatalogNotFoundException(String message, CatalogErrorCode code) {
		super(message);
		this.code = code;
	}

	public CatalogErrorCode getCode() {
		return code;
	}
}

class CatalogAlreadyExistsException extends RuntimeException {
	private final CatalogErrorCode code;

	public CatalogAlreadyExistsException(String message, CatalogErrorCode code) {
		super(message);
		this.code = code;
	}

	public CatalogErrorCode getCode() {
		return code;
	}
}

enum CatalogErrorCode {
	CATALOG_NOT_FOUND,
	CATALOG_ALREADY_EXISTS,
}

// controller // service // repository // model // enum // dto // mapper // exception


