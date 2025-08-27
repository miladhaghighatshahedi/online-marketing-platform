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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

	@PutMapping("/api/catalogs/{id}")
	ResponseEntity<CatalogResponse> update(@RequestBody UpdateCatalogRequest updateCatalogRequest, @PathVariable("id") String id) {
		return ResponseEntity.ok(this.catalogService.update(updateCatalogRequest,UUID.fromString(id)));
	}

	@GetMapping("/api/catalogs/{id}")
	ResponseEntity<CatalogResponse> findById(@PathVariable("id") String id) {
		return ResponseEntity.ok(this.catalogService.findById(UUID.fromString(id)));
	}

	@GetMapping(value = "/api/catalogs",params = "name")
	ResponseEntity<CatalogResponse> findByName(@RequestParam("name") String name) {
		return ResponseEntity.ok(this.catalogService.findByName(name));
	}

	@GetMapping(value = "/api/catalogs",params = "slug")
	ResponseEntity<CatalogResponse> findBySlug(@RequestParam("slug") String slug) {
		return ResponseEntity.ok(this.catalogService.findBySlug(slug));
	}

	@GetMapping("/api/catalogs")
	CatalogPagedResponse<CatalogResponse> findAll(@PageableDefault(size = 20) Pageable pageable) {
		return this.catalogService.findAll(pageable);
	}

	@DeleteMapping("/api/catalogs/{id}")
	CatalogPagedResponse<CatalogResponse> deleteById(@PageableDefault(size = 20) Pageable pageable,@PathVariable("id") String id) {
		return this.catalogService.delete(pageable,UUID.fromString(id));
	}

}

@Service("catalogService")
@Transactional
class CatalogService {

	private final CatalogRepository catalogRepository;
	private final CatalogMapper catalogMapper;
	private final ApplicationEventPublisher publisher;

	public CatalogService(CatalogRepository catalogRepository, CatalogMapper catalogMapper, ApplicationEventPublisher publisher) {
		this.catalogRepository = catalogRepository;
		this.catalogMapper = catalogMapper;
		this.publisher = publisher;
	}

	CatalogResponse add(AddCatalogRequest addCatalogRequest) {
		boolean existsByName = this.catalogRepository.existsByName(addCatalogRequest.name());
		boolean existsBySlug = this.catalogRepository.existsBySlug(addCatalogRequest.slug());

		if(existsByName || existsBySlug) {
			throw new CatalogAlreadyExistsException(
					String.format("Catalog with duplicate name %s or slug %s already exists",addCatalogRequest.name(),addCatalogRequest.slug()));
		}

		Catalog mappedCatalog = this.catalogMapper.mapAddRequestToCatalog(addCatalogRequest);
		Catalog storedCatalog = this.catalogRepository.save(mappedCatalog);
		this.publisher.publishEvent(new AddCatalogEvent(storedCatalog.id()));
		return this.catalogMapper.mapCatalogToResponse(storedCatalog);
	}

	CatalogResponse update(UpdateCatalogRequest updateCatalogRequest,UUID id) {
		Catalog exisitngCatalog = this.catalogRepository.findById(id)
				.orElseThrow(() -> new CatalogNotFoundException("Catalog with id " + id + " not found"));

		boolean existsByName = this.catalogRepository.existsByName(updateCatalogRequest.name());
		boolean existsBySlug = this.catalogRepository.existsBySlug(updateCatalogRequest.slug());

		if(existsByName || existsBySlug) {
			throw new CatalogAlreadyExistsException(
					String.format("Catalog with duplicate name %s or slug %s already exists",updateCatalogRequest.name(),updateCatalogRequest.slug()));
		}

		Catalog mappedCatalog = this.catalogMapper.mapUpdateRequestToCatalog(updateCatalogRequest,exisitngCatalog);
		Catalog storedCatalog = this.catalogRepository.save(mappedCatalog);
		this.publisher.publishEvent(new UpdateCatalogEvent(storedCatalog.id()));
		return this.catalogMapper.mapCatalogToResponse(storedCatalog);
	}

	CatalogResponse findById(UUID id) {
		Catalog catalog = this.catalogRepository.findById(id)
				.orElseThrow(() -> new SlugNotFoundException("Catalog with id " + id + " not found"));

		return this.catalogMapper.mapCatalogToResponse(catalog);
	}

	CatalogResponse findByName(String name) {
		Catalog catalog = this.catalogRepository.findByName(name)
				.orElseThrow(() -> new SlugNotFoundException("Catalog with name " + name + " not found"));

		return this.catalogMapper.mapCatalogToResponse(catalog);
	}

	CatalogResponse findBySlug(String slug) {
		Catalog catalog = this.catalogRepository.findBySlug(slug)
				.orElseThrow(() -> new SlugNotFoundException("Catalog with slug " + slug + " not found"));

		return this.catalogMapper.mapCatalogToResponse(catalog);
	}

	CatalogPagedResponse<CatalogResponse> findAll(Pageable pageable) {
		Page<Catalog> catalogs = this.catalogRepository.findAll(pageable);
		return this.catalogMapper.mapCatalogToPagedResponse(catalogs);
	}

	CatalogPagedResponse<CatalogResponse> delete(Pageable pageable,UUID id) {
		Catalog catalog = this.catalogRepository.findById(id)
				.orElseThrow(() -> new SlugNotFoundException("Catalog with id " + id + " not found"));

		this.catalogRepository.delete(catalog);
		return findAll(pageable);
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

	boolean existsByName(@Param("name") String name);

	boolean existsBySlug(@Param("slug") String slug);

}

@Table("catalogs")
record Catalog(
		@Id UUID id,
		@Version Integer version,
		String name,
		String description,
		String slug) {}

record AddCatalogRequest(
		@NotNull String name,
		@NotNull String description,
		@NotNull String slug) {}

record UpdateCatalogRequest(
		@NotNull String name,
		@NotNull String description,
		@NotNull String slug) {}

record CatalogResponse(
		String id,
		String name,
		String description,
		String slug) {}

record CatalogPagedResponse<T>(
		List<T> content,
		int page,
		int size,
		long totalElements,
		int totalPages) {}

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
interface CatalogMapper {

	@Mapping(target = "id", expression = "java(java.util.UUID.randomUUID())")
	@Mapping(target = "version", ignore = true)
	Catalog mapAddRequestToCatalog(AddCatalogRequest addCatalogRequest);

	default Catalog mapUpdateRequestToCatalog(UpdateCatalogRequest request, Catalog  catalog) {
		return new Catalog(
				catalog.id(),
				catalog.version(),
				request.name() != null ? request.name() :catalog.name(),
				request.description() != null ? request.description() :catalog.description(),
				request.slug() != null ? request.slug() :catalog.slug());
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

class CatalogAlreadyExistsException extends RuntimeException {
	CatalogAlreadyExistsException(String message) {
		super(message);
	}
}

class CatalogNotFoundException extends RuntimeException {
	CatalogNotFoundException(String message) {
		super(message);
	}
}

class SlugNotFoundException extends RuntimeException {
	SlugNotFoundException(String message) {
		super(message);
	}
}

// controller // service // repository // model // enum // dto // mapper // exception

