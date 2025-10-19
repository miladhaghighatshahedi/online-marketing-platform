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
package com.mhs.onlinemarketingplatform.advertisement;

import com.mhs.onlinemarketingplatform.advertisement.error.AdvertisementAlreadyDisabledException;
import com.mhs.onlinemarketingplatform.advertisement.error.AdvertisementAlreadyEnabledException;
import com.mhs.onlinemarketingplatform.advertisement.error.AdvertisementNotFoundException;
import com.mhs.onlinemarketingplatform.advertisement.error.CategoryNotFoundException;
import com.mhs.onlinemarketingplatform.catalog.CategoryApi;
import com.mhs.onlinemarketingplatform.advertisement.event.AddAdvertisementEvent;
import com.mhs.onlinemarketingplatform.advertisement.event.UpdateAdvertisementEvent;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * @author Milad Haghighat Shahedi
 */
@Controller
@ResponseBody
class AdvertisementController {

	private final AdvertisementService advertisementService;
	private final static String OWNER = "26712732-813a-4b3b-8ecf-54e47e428160";

	AdvertisementController(AdvertisementService advertisementService) {
		this.advertisementService = advertisementService;
	}

	@PostMapping("/api/me/advertisements")
	ResponseEntity<AdvertisementResponse> addByOwner(@RequestBody AddAdvertisementRequest addAdvertisementRequest) {
		return ResponseEntity.ok(this.advertisementService.addByOwner(addAdvertisementRequest));
	}

	@GetMapping("/api/me/advertisements")
	AdvertisementPagedResponse<AdvertisementResponse> findAllByOwner(@PageableDefault(size = 20) Pageable pageable) {
		return this.advertisementService.findAllByOwner(UUID.fromString(OWNER), pageable);
	}

	@GetMapping(value = "/api/me/advertisements", params = "name")
	ResponseEntity<AdvertisementResponse> findByNameAndOwner(@RequestParam("name") String name) {
		return ResponseEntity.ok(this.advertisementService.findByNameAndOwner(name,UUID.fromString(OWNER)));
	}

	@GetMapping(value = "/api/me/advertisements", params = "status")
	AdvertisementPagedResponse<AdvertisementResponse> findAllByStatusAndOwner(@RequestParam("status") String status, @PageableDefault(size = 20) Pageable pageable) {
		return this.advertisementService.findAllByAdvertisementStatusAndOwner(
				AdvertisementStatus.valueOf(status.trim().toUpperCase()),
				UUID.fromString(OWNER),pageable);
	}

	@GetMapping("/api/me/advertisements/{id}")
	ResponseEntity<AdvertisementResponse> findByIdAndOwner(@RequestBody @PathVariable("id") String id) {
		return ResponseEntity.ok(this.advertisementService.findByIdAndOwner(UUID.fromString(id),UUID.fromString(OWNER)));
	}

	@PutMapping("/api/me/advertisements/{id}")
	ResponseEntity<AdvertisementResponse> updateByOwner(@RequestBody UpdateAdvertisementRequest updateAdvertisementRequest, @PathVariable("id") String id) {
		return ResponseEntity.ok(this.advertisementService.updateByIdAndOwner(updateAdvertisementRequest, UUID.fromString(id), UUID.fromString(OWNER)));
	}

	@PutMapping("/api/me/advertisements/{id}/activate")
	ResponseEntity<AdvertisementResponse> activateByIdAndOwner(@PathVariable("id") String id) {
		return ResponseEntity.ok(this.advertisementService.activateByOwner(UUID.fromString(id), UUID.fromString(OWNER)));
	}

	@PutMapping("/api/me/advertisements/{id}/deactivate")
	ResponseEntity<AdvertisementResponse> deactivateByIdAndOwner(@PathVariable("id") String id) {
		return ResponseEntity.ok(this.advertisementService.deactivateByOwner(UUID.fromString(id), UUID.fromString(OWNER)));
	}

}

@Service
@Transactional
class AdvertisementService {

	private final advertisementRepository advertisementRepository;
	private final CategoryApi categoryApi;
	private final AdvertisementMapper mapper;
	private final ApplicationEventPublisher publisher;

	AdvertisementService(advertisementRepository advertisementRepository,
	                     CategoryApi categoryApi,
	                     AdvertisementMapper mapper,
	                     ApplicationEventPublisher publisher) {
		this.advertisementRepository = advertisementRepository;
		this.categoryApi = categoryApi;
		this.mapper = mapper;
		this.publisher = publisher;
	}

	AdvertisementResponse addByOwner(AddAdvertisementRequest addAdvertisementRequest) {
		if(!this.categoryApi.existsById(addAdvertisementRequest.categoryId())){
			throw new CategoryNotFoundException("Category with id " + addAdvertisementRequest.categoryId() + "not found");
		}

		Advertisement mappedAdvertisement = this.mapper.mapAddRequestToAdvertisement(addAdvertisementRequest);
		Advertisement storedAdvertisement = this.advertisementRepository.save(mappedAdvertisement);

		this.publisher.publishEvent(new AddAdvertisementEvent(storedAdvertisement.id()));
		return this.mapper.mapAdvertisementToResponse(storedAdvertisement);
	}

	AdvertisementResponse activateByOwner(UUID id, UUID owner) {
		Advertisement exisitngAdvertisement = this.advertisementRepository.findByIdAndOwner(id, owner)
				.orElseThrow(() -> new AdvertisementNotFoundException("Advertisement with id " + id + " not found"));

		if (! exisitngAdvertisement.advertisementStatus().status.trim().equals("ACTIVE")) {
			Advertisement updatingAdvertisement = Advertisement.withAdvertisementStatusActivated(exisitngAdvertisement, owner);
			Advertisement storedAdvertisement = this.advertisementRepository.save(updatingAdvertisement);
			this.publisher.publishEvent(new UpdateAdvertisementEvent(storedAdvertisement.id()));
			return this.mapper.mapAdvertisementToResponse(storedAdvertisement);
		}

		throw new AdvertisementAlreadyEnabledException("Advertisement with name " + exisitngAdvertisement.title() + " is already " + "enabled");
	}

	AdvertisementResponse deactivateByOwner(UUID id, UUID owner) {
		Advertisement exisitngAdvertisement = this.advertisementRepository.findByIdAndOwner(id, owner)
				.orElseThrow(() -> new AdvertisementNotFoundException("Advertisement with id " + id + " not found"));

		if (! exisitngAdvertisement.advertisementStatus().status.trim().equals("INACTIVE")) {
			Advertisement updatingAdvertisement = Advertisement.withAdvertisementStatusDeactivated(exisitngAdvertisement, owner);
			Advertisement storedAdvertisement = this.advertisementRepository.save(updatingAdvertisement);
			this.publisher.publishEvent(new UpdateAdvertisementEvent(storedAdvertisement.id()));
			return this.mapper.mapAdvertisementToResponse(storedAdvertisement);
		}

		throw new AdvertisementAlreadyDisabledException("Advertisement with name " + exisitngAdvertisement.title() + " is already " + "disabled");
	}

	AdvertisementResponse updateByIdAndOwner(UpdateAdvertisementRequest updateAdvertisementRequest, UUID id, UUID owner) {
		Advertisement exisitngAdvertisement = this.advertisementRepository.findByIdAndOwner(id, owner)
				.orElseThrow(() -> new AdvertisementNotFoundException("Advertisement not found with id: " + id));

		Advertisement mappedAdvertisement = this.mapper.mapUpdateRequestToAdvertisement(updateAdvertisementRequest, exisitngAdvertisement);
		Advertisement storedAdvertisement = this.advertisementRepository.save(mappedAdvertisement);
		this.publisher.publishEvent(new UpdateAdvertisementEvent(storedAdvertisement.id()));
		return this.mapper.mapAdvertisementToResponse(storedAdvertisement);
	}

	AdvertisementResponse findByNameAndOwner(String name, UUID owner) {
		Advertisement advertisement = this.advertisementRepository.findByTitleAndOwner(name, owner)
				.orElseThrow(() -> new AdvertisementNotFoundException("Advertisement not found: " + name));
		return this.mapper.mapAdvertisementToResponse(advertisement);
	}

	AdvertisementResponse findByIdAndOwner(UUID id, UUID owner) {
		Advertisement advertisement = this.advertisementRepository.findByIdAndOwner(id, owner)
				.orElseThrow(() -> new AdvertisementNotFoundException("Advertisement not found with id: " + id));
		return this.mapper.mapAdvertisementToResponse(advertisement);
	}

	AdvertisementPagedResponse<AdvertisementResponse> findAllByOwner(UUID owner, Pageable pageable) {
		Page<Advertisement> advertisementss = this.advertisementRepository.findAllByOwner(owner, pageable);
		return this.mapper.mapAdvertisementToPagedResponse(advertisementss);
	}

	AdvertisementPagedResponse<AdvertisementResponse> findAllByAdvertisementStatusAndOwner(AdvertisementStatus status, UUID owner, Pageable pageable) {
		Page<Advertisement> advertisementss = this.advertisementRepository.findAllByAdvertisementStatusAndOwner(status, owner, pageable);
		return this.mapper.mapAdvertisementToPagedResponse(advertisementss);
	}

}

@Repository
interface advertisementRepository extends ListCrudRepository<Advertisement, UUID> {

	Optional<Advertisement> findByIdAndOwner(UUID id, UUID credential);

	Optional<Advertisement> findByTitleAndOwner(String title, UUID credential);

	Page<Advertisement> findAllByOwner(UUID credential, Pageable pageable);

	Page<Advertisement> findAllByAdvertisementStatusAndOwner(
			@Param("advertisementStatus") AdvertisementStatus advertisementStatus,
			@Param("credential") UUID credential,
			Pageable pageable);
}

@Table("advertisements")
record Advertisement(
		@Id UUID id,
		@Version Integer version,
		String title,
		String description,
		BigDecimal price,
		AdvertisementType advertisementType,
		AdvertisementStatus advertisementStatus,
		Map<String,Object> attributes,
		Set<Image> images,
		LocalDateTime insertedAt,
		LocalDateTime updatedAt,
		UUID locationId,
		UUID categoryId,
		UUID owner) {

	static Advertisement withAdvertisementStatusActivated(Advertisement advertisement,UUID owner) {
		return new Advertisement(
				advertisement.id,
				advertisement.version(),
				advertisement.title(),
				advertisement.description(),
				advertisement.price,
				advertisement.advertisementType,
				AdvertisementStatus.ACTIVE,
				advertisement.attributes,
				advertisement.images,
				advertisement.insertedAt,
				LocalDateTime.now(),
				advertisement.locationId,
				advertisement.categoryId,
				owner
		);
	}

	static Advertisement withAdvertisementStatusDeactivated(Advertisement advertisement, UUID owner) {
		return new Advertisement(
				advertisement.id,
				advertisement.version(),
				advertisement.title(),
				advertisement.description(),
				advertisement.price,
				advertisement.advertisementType,
				AdvertisementStatus.INACTIVE,
				advertisement.attributes,
				advertisement.images,
				advertisement.insertedAt,
				LocalDateTime.now(),
				advertisement.locationId,
				advertisement.categoryId,
				owner
		);
	}

}

enum AdvertisementType {

	CARS("CARS"),
	APARTEMENTS("APARTEMENTS"),
	MOBILES("MOBILES");

	final String type;

	AdvertisementType(String type) {
		this.type = type;
	}

}

enum AdvertisementStatus {

	ACTIVE("ACTIVE"),
	INACTIVE("INACTIVE");

	final String status;

	AdvertisementStatus(String status) {
		this.status = status;
	}

}

record AddAdvertisementRequest(
		@NotNull String title,
		@NotBlank String description,
		@NotNull String price,
		@NotNull String type,
		@NotNull Map<String,Object> attributes,
		@NotNull UUID locationId,
		@NotNull UUID categoryId,
		@NotNull UUID owner) {}

record UpdateAdvertisementRequest(
		@NotNull String title,
		@NotBlank String description,
		@NotNull String price,
		@NotNull Map<String,Object> attributes,
		@NotNull Set<Image> images,
		@NotNull UUID owner) {}

record AdvertisementResponse(
		String id,
		String title,
		String description,
		String price,
		String type,
		String status,
		Map<String,Object> attributes,
		LocalDateTime insertedAt,
		LocalDateTime updateAt) {}

record AdvertisementPagedResponse<T>(
		List<T> content,
		int page,
		int size,
		long totalElements,
		int totalPages) {}

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
interface AdvertisementMapper {

	@Mapping(target = "id", expression = "java(java.util.UUID.randomUUID())")
	@Mapping(target = "version", ignore = true)
	@Mapping(target = "advertisementStatus", constant = "INACTIVE")
	@Mapping(target = "insertedAt", expression = "java(java.time.LocalDateTime.now())")
	@Mapping(target = "updatedAt", ignore = true)
	Advertisement mapAddRequestToAdvertisement(AddAdvertisementRequest addAdvertisementRequest);

	default Advertisement mapUpdateRequestToAdvertisement(UpdateAdvertisementRequest updateAdvertisementRequest, Advertisement advertisement){
		return new Advertisement(
				advertisement.id(),
				advertisement.version(),
				updateAdvertisementRequest.title(),
				updateAdvertisementRequest.description(),
				new BigDecimal(updateAdvertisementRequest.price()),
				advertisement.advertisementType(),
		        AdvertisementStatus.INACTIVE,
				updateAdvertisementRequest.attributes(),
				updateAdvertisementRequest.images(),
				advertisement.insertedAt(),
				LocalDateTime.now(),
				advertisement.locationId(),
				advertisement.categoryId(),
				advertisement.owner());
	}

	@Mapping(target = "type", source = "advertisementType")
	@Mapping(target = "status", source = "advertisementStatus")
	AdvertisementResponse mapAdvertisementToResponse(Advertisement advertisement);

	default AdvertisementPagedResponse<AdvertisementResponse> mapAdvertisementToPagedResponse(Page<Advertisement> page) {
		return new AdvertisementPagedResponse<>(page.getContent().stream().map(this::mapAdvertisementToResponse).toList(), page.getNumber(),
				page.getSize(), page.getTotalElements(), page.getTotalPages());
	}

	default String map(AdvertisementStatus status) {
		return status != null ? status.name() : null;
	}

}

// controller // service // repository // model // enum // dto // mapper // exception

