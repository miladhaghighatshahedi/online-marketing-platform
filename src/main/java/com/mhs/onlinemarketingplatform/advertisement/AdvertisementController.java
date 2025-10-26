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

import com.github.f4b6a3.uuid.UuidCreator;
import com.mhs.onlinemarketingplatform.advertisement.error.advertisement.*;
import com.mhs.onlinemarketingplatform.advertisement.error.category.CategoryNotFoundException;
import com.mhs.onlinemarketingplatform.catalog.CategoryApi;
import com.mhs.onlinemarketingplatform.advertisement.event.AddAdvertisementEvent;
import com.mhs.onlinemarketingplatform.advertisement.event.UpdateAdvertisementEvent;
import com.mhs.onlinemarketingplatform.advertisement.error.category.CategoryErrorCode;
import com.mhs.onlinemarketingplatform.common.AuditLogger;
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

	@PutMapping("/api/me/advertisements")
	ResponseEntity<AdvertisementResponse> updateByOwner(@RequestBody UpdateAdvertisementRequest updateAdvertisementRequest) {
		return ResponseEntity.ok(this.advertisementService.updateByOwner(updateAdvertisementRequest, UUID.fromString(OWNER)));
	}

	@DeleteMapping("/api/me/advertisements/{id}")
	ResponseEntity<?> delete(@PathVariable("id") UUID id) {
		this.advertisementService.delete(id);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/api/me/advertisements/{id}")
	ResponseEntity<AdvertisementResponse> findById(@PathVariable("id") String id) {
		return ResponseEntity.ok(this.advertisementService.findById(UUID.fromString(id)));
	}

	@GetMapping("/api/me/advertisements/{id}/owner")
	ResponseEntity<AdvertisementResponse> findByIdAndOwner(@PathVariable("id") String id) {
		return ResponseEntity.ok(this.advertisementService.findByIdAndOwnerId(UUID.fromString(id),UUID.fromString(OWNER)));
	}

	@GetMapping(value = "/api/me/advertisements", params = "name")
	ResponseEntity<AdvertisementResponse> findByNameAndOwner(@RequestParam("name") String name) {
		return ResponseEntity.ok(this.advertisementService.findByTitleAndOwnerId(name,UUID.fromString(OWNER)));
	}

	@GetMapping("/api/me/advertisements")
	AdvertisementPagedResponse<AdvertisementResponse> findAllByOwner(@PageableDefault(size = 20) Pageable pageable) {
		return this.advertisementService.findAllByOwnerId(UUID.fromString(OWNER), pageable);
	}

	@GetMapping(value = "/api/me/advertisements", params = "status")
	AdvertisementPagedResponse<AdvertisementResponse> findAllByStatusAndOwner(@RequestParam("status") String status, @PageableDefault(size = 20) Pageable pageable) {
		return this.advertisementService.findAllByAdvertisementStatusAndOwnerId(
				AdvertisementStatus.valueOf(status.trim().toUpperCase()),
				UUID.fromString(OWNER),pageable);
	}

	@PutMapping("/api/me/advertisements/{id}/activate")
	ResponseEntity<AdvertisementResponse> activateByIdAndOwner(@PathVariable("id") String id) {
		return ResponseEntity.ok(this.advertisementService.activateByOwnerId(UUID.fromString(id), UUID.fromString(OWNER)));
	}

	@PutMapping("/api/me/advertisements/{id}/deactivate")
	ResponseEntity<AdvertisementResponse> deactivateByIdAndOwner(@PathVariable("id") String id) {
		return ResponseEntity.ok(this.advertisementService.deactivateByOwnerId(UUID.fromString(id), UUID.fromString(OWNER)));
	}

}

@Service
@Transactional
class AdvertisementService {

	private static final Logger logger = LoggerFactory.getLogger(AdvertisementService.class);
	private final AuditLogger auditLogger;

	private final advertisementRepository advertisementRepository;
	private final CategoryApi categoryApi;
	private final AdvertisementMapper mapper;
	private final ApplicationEventPublisher publisher;
	private final MessageSource messageSource;

	AdvertisementService(
			AuditLogger auditLogger,
			advertisementRepository advertisementRepository,
			CategoryApi categoryApi,
			AdvertisementMapper mapper,
			ApplicationEventPublisher publisher,
			MessageSource messageSource) {
		this.auditLogger = auditLogger;
		this.advertisementRepository = advertisementRepository;
		this.categoryApi = categoryApi;
		this.mapper = mapper;
		this.publisher = publisher;
		this.messageSource = messageSource;
	}

	AdvertisementResponse addByOwner(AddAdvertisementRequest addAdvertisementRequest) {
		logger.info("Creating new advertisement with the title: {}",addAdvertisementRequest.title());

		if(this.advertisementRepository.existsByTitleAndOwner(addAdvertisementRequest.title(),addAdvertisementRequest.ownerId())) {
			throw new AdvertisementAlreadyExistsException(
					messageSource.getMessage("error.advertisement.advertisement.already.exists",
							new Object[]{addAdvertisementRequest.title()},
							LocaleContextHolder.getLocale()),
					AdvertisementErrorCode.ADVERTISEMENT_ALREADY_EXISTS);
		}

		if(!this.categoryApi.existsById(addAdvertisementRequest.categoryId())){
			throw new CategoryNotFoundException(
					messageSource.getMessage("error.category.category.with.id.not.found",
					new Object[]{addAdvertisementRequest.categoryId()},
					LocaleContextHolder.getLocale()),
					CategoryErrorCode.CATEGORY_NOT_FOUND);
		}

		Advertisement mappedAdvertisement = this.mapper.mapAddRequestToAdvertisement(addAdvertisementRequest);
		Advertisement storedAdvertisement = this.advertisementRepository.save(mappedAdvertisement);

		this.auditLogger.log("ADVERTISEMENT_CREATED", "ADVERTISEMENT", "Advertisement ID: " + storedAdvertisement.id());
		this.publisher.publishEvent(new AddAdvertisementEvent(storedAdvertisement.id()));

		return this.mapper.mapAdvertisementToResponse(storedAdvertisement);
	}

	AdvertisementResponse updateByOwner(UpdateAdvertisementRequest updateAdvertisementRequest, UUID ownerId) {
		logger.info("Updating exisiting advertisement with the title: {}",updateAdvertisementRequest.title());
		Advertisement exisitngAdvertisement = this.advertisementRepository.findByIdAndOwnerId(UUID.fromString(updateAdvertisementRequest.id()), ownerId)
				.orElseThrow(() -> new AdvertisementNotFoundException(
						messageSource.getMessage("error.advertisement.advertisement.with.id.not.found",
								new Object[]{updateAdvertisementRequest.id()},
								LocaleContextHolder.getLocale()),
						AdvertisementErrorCode.ADVERTISEMENT_NOT_FOUND));

		Advertisement mappedAdvertisement = this.mapper.mapUpdateRequestToAdvertisement(updateAdvertisementRequest, exisitngAdvertisement);
		Advertisement storedAdvertisement = this.advertisementRepository.save(mappedAdvertisement);
		this.auditLogger.log("ADVERTISEMENT_UPDATED", "ADVERTISEMENT", "Advertisement TITLE: " + updateAdvertisementRequest.title());

		this.publisher.publishEvent(new UpdateAdvertisementEvent(storedAdvertisement.id()));
		return this.mapper.mapAdvertisementToResponse(storedAdvertisement);
	}

	public void delete(UUID id) {
		Advertisement advertisement = this.advertisementRepository.findById(id)
				.orElseThrow(() -> new AdvertisementNotFoundException(
						messageSource.getMessage("error.advertisement.advertisement.with.id.not.found",
								new Object[]{id},
								LocaleContextHolder.getLocale()),
						AdvertisementErrorCode.ADVERTISEMENT_NOT_FOUND));
		logger.info("Deleting exisiting advertisement with id: {} and name: {}",advertisement.id(),advertisement.title());
		this.advertisementRepository.delete(advertisement);
		this.auditLogger.log("ADVERTISEMENT_DELETED", "ADVERTISEMENT", "Advertisement TITLE: " + advertisement.title());
	}

	AdvertisementResponse findById(UUID id) {
		logger.info("Looking up advertisement by ID: {}",id);
		Advertisement advertisement = this.advertisementRepository.findById(id)
				.orElseThrow(() -> new AdvertisementNotFoundException(
						messageSource.getMessage("error.advertisement.advertisement.with.id.not.found",
								new Object[]{id},
								LocaleContextHolder.getLocale()),
						AdvertisementErrorCode.ADVERTISEMENT_NOT_FOUND));
		return this.mapper.mapAdvertisementToResponse(advertisement);
	}

	AdvertisementResponse findByIdAndOwnerId(UUID id, UUID ownerId) {
		logger.info("Looking up advertisement by ID: {} and OWNER: {}",id,ownerId);
		Advertisement advertisement = this.advertisementRepository.findByIdAndOwnerId(id, ownerId)
				.orElseThrow(() -> new AdvertisementNotFoundException(
						messageSource.getMessage("error.advertisement.advertisement.with.id.not.found",
								new Object[]{id},
								LocaleContextHolder.getLocale()),
						AdvertisementErrorCode.ADVERTISEMENT_NOT_FOUND));
		return this.mapper.mapAdvertisementToResponse(advertisement);
	}

	AdvertisementResponse findByTitleAndOwnerId(String title, UUID ownerId) {
		logger.info("Looking up advertisement by TITLe: {} and OWNER: {}",title,ownerId);
		Advertisement advertisement = this.advertisementRepository.findByTitleAndOwnerId(title, ownerId)
				.orElseThrow(() -> new AdvertisementNotFoundException(
						messageSource.getMessage("error.advertisement.advertisement.with.title.not.found",
								new Object[]{title},
								LocaleContextHolder.getLocale()),
						AdvertisementErrorCode.ADVERTISEMENT_NOT_FOUND));
		return this.mapper.mapAdvertisementToResponse(advertisement);
	}

	AdvertisementPagedResponse<AdvertisementResponse> findAllByOwnerId(UUID ownerId,Pageable pageable) {
		logger.info("Retriving all advertisements by their OWNER: {} ",ownerId);
		Page<Advertisement> advertisementss = this.advertisementRepository.findAllByOwnerId(ownerId, pageable);
		return this.mapper.mapAdvertisementToPagedResponse(advertisementss);
	}

	AdvertisementPagedResponse<AdvertisementResponse> findAllByAdvertisementStatusAndOwnerId(AdvertisementStatus status, UUID ownerId, Pageable pageable) {
		Page<Advertisement> advertisementss = this.advertisementRepository.findAllByAdvertisementStatusAndOwnerId(status, ownerId, pageable);
		return this.mapper.mapAdvertisementToPagedResponse(advertisementss);
	}

	AdvertisementResponse activateByOwnerId(UUID advertisementId, UUID ownerId) {
		Advertisement exisitngAdvertisement = this.advertisementRepository.findByIdAndOwnerId(advertisementId, ownerId)
				.orElseThrow(() -> new AdvertisementNotFoundException(
						messageSource.getMessage("error.advertisement.advertisement.with.id.not.found",
								new Object[]{advertisementId},
								LocaleContextHolder.getLocale()),
						AdvertisementErrorCode.ADVERTISEMENT_NOT_FOUND));
		logger.info("Activate an advertisement by ID: {} TITLE: {} and OWNER: {}",advertisementId,exisitngAdvertisement.title(),ownerId);

		if (exisitngAdvertisement.advertisementStatus() == AdvertisementStatus.INACTIVE) {
			Advertisement activatedAdvertisement = this.mapper.toActivate(exisitngAdvertisement);
			Advertisement storedAdvertisement = this.advertisementRepository.save(activatedAdvertisement);
			this.auditLogger.log("ADVERTISEMENT_ACTIVATED", "ADVERTISEMENT", "advertisement TITLE: "+storedAdvertisement.title());

			this.publisher.publishEvent(new UpdateAdvertisementEvent(storedAdvertisement.id()));
			return this.mapper.mapAdvertisementToResponse(storedAdvertisement);
		}

		throw new AdvertisementAlreadyActivatedException(
				messageSource.getMessage("error.advertisement.advertisement.already.activated",
						new Object[]{advertisementId},
						LocaleContextHolder.getLocale()),
				AdvertisementErrorCode.ADVERTISEMENT_ALREADY_ACTIVATED);
	}

	AdvertisementResponse deactivateByOwnerId(UUID advertisementId, UUID ownerId) {
		Advertisement exisitngAdvertisement = this.advertisementRepository.findByIdAndOwnerId(advertisementId, ownerId)
				.orElseThrow(() -> new AdvertisementNotFoundException(
						messageSource.getMessage("error.advertisement.advertisement.with.id.not.found",
								new Object[]{advertisementId},
								LocaleContextHolder.getLocale()),
						AdvertisementErrorCode.ADVERTISEMENT_NOT_FOUND));
		logger.info("Daactivate an advertisement by ID: {} TITLE: {} and OWNER: {}",advertisementId,exisitngAdvertisement.title(),ownerId);

		if (exisitngAdvertisement.advertisementStatus() == AdvertisementStatus.ACTIVE) {
			Advertisement deactivatedAdvertisement = this.mapper.toDeactivate(exisitngAdvertisement);
			Advertisement storedAdvertisement = this.advertisementRepository.save(deactivatedAdvertisement);
			this.auditLogger.log("ADVERTISEMENT_DEACTIVATED", "ADVERTISEMENT", "advertisement TITLE: "+storedAdvertisement.title());

			this.publisher.publishEvent(new UpdateAdvertisementEvent(storedAdvertisement.id()));
			return this.mapper.mapAdvertisementToResponse(storedAdvertisement);
		}

		throw new AdvertisementAlreadyDeactivatedException(
				messageSource.getMessage("error.advertisement.advertisement.already.deactivated",
						new Object[]{advertisementId},
						LocaleContextHolder.getLocale()),
				AdvertisementErrorCode.ADVERTISEMENT_ALREADY_DEACTIVATED);
	}

	boolean existsById(UUID id){
		return this.advertisementRepository.existsById(id);
	}

}

@Repository
interface advertisementRepository extends ListCrudRepository<Advertisement, UUID> {

	Optional<Advertisement> findById(UUID id);

	Optional<Advertisement> findByIdAndOwnerId(UUID id, UUID credential);

	Optional<Advertisement> findByTitleAndOwnerId(String title, UUID credential);

	Page<Advertisement> findAllByOwnerId(UUID credential, Pageable pageable);

	Page<Advertisement> findAllByAdvertisementStatusAndOwnerId(
			@Param("advertisementStatus") AdvertisementStatus advertisementStatus,
			@Param("credential") UUID credential,
			Pageable pageable);

	@Query("""
             SELECT CASE WHEN COUNT(1) > 0 THEN TRUE ELSE FALSE END
			 FROM advertisements WHERE owner_id= :ownerId AND title= :title
			 """)
	boolean existsByTitleAndOwner(@Param("title") String title,@Param("ownerId") UUID ownerId);

	@Query("SELECT CASE WHEN COUNT(1) > 0 THEN TRUE ELSE FALSE END FROM advertisements WHERE id= :id")
	boolean existsById(@Param("id") UUID id);
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
		LocalDateTime insertedAt,
		LocalDateTime updatedAt,
		UUID locationId,
		UUID categoryId,
		UUID ownerId) {}

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
		@NotNull UUID ownerId) {}

record UpdateAdvertisementRequest(
		@NotNull String id,
		@NotNull String title,
		@NotBlank String description,
		@NotNull String price,
		@NotNull Map<String,Object> attributes,
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

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE,imports = {UuidCreator.class, LocalDateTime.class})
interface AdvertisementMapper {

	@Mapping(target = "id", expression = "java(UuidCreator.getTimeOrderedEpoch())")
	@Mapping(target = "version", ignore = true)
	@Mapping(target = "advertisementStatus", constant = "INACTIVE")
	@Mapping(target = "insertedAt", expression = "java(LocalDateTime.now())")
	@Mapping(target = "updatedAt", ignore = true)
	Advertisement mapAddRequestToAdvertisement(AddAdvertisementRequest addAdvertisementRequest);

	@Mapping(target = "id", source = "advertisement.id")
	@Mapping(target = "version", source = "advertisement.version")
	@Mapping(target = "title", expression = "java(request.title() != null ? request.title() : advertisement.title())")
	@Mapping(target = "description", expression = "java(request.description() != null ? request.description() : advertisement.description())")
	@Mapping(target = "price", expression = "java(request.price() != null ? new BigDecimal(request.price()) : advertisement.price())")
	@Mapping(target = "advertisementType", source = "advertisement.advertisementType")
	@Mapping(target = "advertisementStatus", constant = "INACTIVE")
	@Mapping(target = "attributes", expression = "java(request.attributes() != null ? request.attributes() : advertisement.attributes())")
	@Mapping(target = "insertedAt", source = "advertisement.insertedAt")
	@Mapping(target = "updatedAt", expression = "java(LocalDateTime.now())")
	@Mapping(target = "locationId", source = "advertisement.locationId")
	@Mapping(target = "categoryId", source = "advertisement.categoryId")
	@Mapping(target = "ownerId", source = "advertisement.ownerId")
	Advertisement mapUpdateRequestToAdvertisement(UpdateAdvertisementRequest request, Advertisement advertisement);

	@Named("toDeactivate")
	@Mapping(target = "advertisementStatus", constant = "INACTIVE")
	@Mapping(target = "updatedAt", expression = "java(LocalDateTime.now())")
	Advertisement toDeactivate(Advertisement advertisement);

	@Named("toActivate")
	@Mapping(target = "advertisementStatus", constant = "ACTIVE")
	@Mapping(target = "updatedAt", expression = "java(LocalDateTime.now())")
	Advertisement toActivate(Advertisement advertisement);

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

