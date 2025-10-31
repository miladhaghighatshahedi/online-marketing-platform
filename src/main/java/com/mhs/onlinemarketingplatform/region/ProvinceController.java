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
package com.mhs.onlinemarketingplatform.region;

import com.github.f4b6a3.uuid.UuidCreator;
import com.mhs.onlinemarketingplatform.common.AuditLogger;
import com.mhs.onlinemarketingplatform.region.api.ProvinceApi;
import com.mhs.onlinemarketingplatform.region.error.province.ProvinceAlreadyExistsException;
import com.mhs.onlinemarketingplatform.region.error.province.ProvinceErrorCode;
import com.mhs.onlinemarketingplatform.region.error.province.ProvinceNotFoundException;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
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

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * @author Milad Haghighat Shahedi
 */
@Controller
@ResponseBody
class ProvinceController {

	private final ProvinceService provinceService;

	public ProvinceController(ProvinceService provinceService) {
		this.provinceService = provinceService;
	}

	@PostMapping("/api/provinces")
	ResponseEntity<ProvinceResponse> add(@RequestBody AddProvinceRequest addProvinceRequest) {
		return ResponseEntity.ok(this.provinceService.add(addProvinceRequest));
	}

	@PutMapping("/api/provinces")
	ResponseEntity<ProvinceResponse> update(@RequestBody UpdateProvinceRequest updateProvinceRequest) {
		return ResponseEntity.ok(this.provinceService.update(updateProvinceRequest));
	}

	@DeleteMapping("/api/provinces/{id}")
	ResponseEntity<?> delete(@PathVariable("id") UUID id) {
		this.provinceService.delete(id);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/api/provinces/{id}")
	ResponseEntity<ProvinceResponse> findById(@PathVariable("id") UUID id) {
		return ResponseEntity.ok(this.provinceService.findById(id));
	}

	@GetMapping(value = "/api/provinces",params = "name")
	ResponseEntity<ProvinceResponse> findByName(@RequestParam("name") String name) {
		return ResponseEntity.ok(this.provinceService.findByName(name));
	}

	@GetMapping("/api/provinces")
	ResponseEntity<List<ProvinceResponse>> findAllOrdered() {
		return ResponseEntity.ok(this.provinceService.findAllOrdered());
	}

}

@Service
@Transactional
class ProvinceService implements ProvinceApi {
	private static final Logger logger = LoggerFactory.getLogger(ProvinceService.class);
	private final AuditLogger auditLogger;

	private final ProvinceRepository provinceRepository;
	private final ProvinceMapper provinceMapper;
	private final MessageSource messageSource;

	public ProvinceService(
			AuditLogger auditLogger,
			ProvinceRepository provinceRepository,
			ProvinceMapper provinceMapper,
			MessageSource messageSource) {
		this.auditLogger = auditLogger;
		this.provinceRepository = provinceRepository;
		this.provinceMapper = provinceMapper;
		this.messageSource = messageSource;
	}

	ProvinceResponse add(AddProvinceRequest addProvinceRequest) {
		logger.info("Creating new province with name: {}",addProvinceRequest.name());

		if(this.provinceRepository.existsByName(addProvinceRequest.name())) {
			throw new ProvinceAlreadyExistsException(
					messageSource.getMessage("error.province.province.already.exists",
					new Object[] {addProvinceRequest.name()},
					LocaleContextHolder.getLocale()),
			ProvinceErrorCode.PROVINCE_ALREADY_EXISTS);
		}

		Province mappedProvince = this.provinceMapper.mapAddProvinceRequestToProvince(addProvinceRequest);
		Province storedProvince = this.provinceRepository.save(mappedProvince);
		this.auditLogger.log("PROVINCE_CREATED", "PROVINCE", "City ID: " + storedProvince.id());
		return this.provinceMapper.mapProvinceToProvinceResponse(storedProvince);
	}

	ProvinceResponse update(UpdateProvinceRequest updateProvinceRequest) {
		logger.info("Updating existing province with name: {}",updateProvinceRequest.name());

		Province existingProvince = this.provinceRepository.findById(updateProvinceRequest.id()).orElseThrow(() ->
				new ProvinceNotFoundException(
						messageSource.getMessage("error.province.province.with.id.not.found",
								new Object[]{updateProvinceRequest.id()},
								LocaleContextHolder.getLocale()),
						ProvinceErrorCode.PROVINCE_NOT_FOUND));

		Province mappedProvince = this.provinceMapper.mapUpdateProvinceRequestToProvince(updateProvinceRequest, existingProvince);
		Province updatedProvince = this.provinceRepository.save(mappedProvince);
		this.auditLogger.log("PROVINCE_UPDATED", "PROVINCE", "City NAME: " + updatedProvince.name());
		return this.provinceMapper.mapProvinceToProvinceResponse(updatedProvince);
	}

	void delete(UUID id) {
		Province existingProvince = this.provinceRepository.findById(id).orElseThrow(() ->
				new ProvinceNotFoundException(
						messageSource.getMessage("error.province.province.with.id.not.found",
								new Object[]{id},
								LocaleContextHolder.getLocale()),
						ProvinceErrorCode.PROVINCE_NOT_FOUND));

		logger.info("Deleting exisiting province with id: {} ",existingProvince.id());
		this.provinceRepository.delete(existingProvince);
		this.auditLogger.log("PROVINCE_DELETED", "PROVINCE", "Province name: " + existingProvince.name());
	}

	ProvinceResponse findById(UUID id) {
		logger.info("Looking up province by ID: {}",id);
		Province existingProvince = this.provinceRepository.findById(id).orElseThrow(() ->
				new ProvinceNotFoundException(
						messageSource.getMessage("error.province.province.with.id.not.found",
								new Object[]{id},
								LocaleContextHolder.getLocale()),
						ProvinceErrorCode.PROVINCE_NOT_FOUND));
		return this.provinceMapper.mapProvinceToProvinceResponse(existingProvince);
	}

    ProvinceResponse findByName(String name) {
	    logger.info("Looking up province by Name: {}",name);
	    Province province = this.provinceRepository.findByName(name).orElseThrow(() ->
			    new ProvinceNotFoundException(
						messageSource.getMessage("error.province.province.with.name.not.found",
								new Object[]{name},
								LocaleContextHolder.getLocale()),
					    ProvinceErrorCode.PROVINCE_NOT_FOUND));
		return this.provinceMapper.mapProvinceToProvinceResponse(province);
    }

	List<ProvinceResponse> findAllOrdered() {
	    logger.info("Retriving all provinces ordered");
	    return this.provinceRepository.findAllOrdered();
    }

	public boolean existsById(UUID id) {
		return this.provinceRepository.existsById(id);
	}

	public Optional<String> findNameById(UUID id) {
		return this.provinceRepository.findNameById(id);
	}

}

@Repository
interface ProvinceRepository extends CrudRepository<Province,UUID> {

	@Query("SELECT * FROM provinces WHERE name = :name")
	Optional<Province> findByName(@Param("name")  String name);

	@Query("SELECT CASE WHEN COUNT(1) > 0 THEN TRUE ELSE FALSE END FROM provinces WHERE name= :name")
	boolean existsByName(@Param("name") String name);

	@Query("SELECT CASE WHEN COUNT(1) > 0 THEN TRUE ELSE FALSE END FROM provinces WHERE id = :id")
	boolean existsById(@Param("id") UUID id);

	@Query("SELECT name FROM provinces WHERE id= :id")
	Optional<String> findNameById(@Param("id") UUID id);

	@Query("SELECT id,name FROM provinces order by name")
	List<ProvinceResponse> findAllOrdered();

}

@Table("provinces")
record Province(
		@Id UUID id,
		@Version
		int version,
		String name) {}

record AddProvinceRequest(
		String name
) {}

record UpdateProvinceRequest(
		UUID id,
		String name
) {}

record ProvinceResponse(
		UUID id,
		String name
) {}

@Mapper(componentModel = "spring",nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE, imports = {UuidCreator.class})
interface ProvinceMapper {

	@Mapping(target = "id", expression = ("java(UuidCreator.getTimeOrderedEpoch())"))
	@Mapping(target = "version",ignore = true)
	Province mapAddProvinceRequestToProvince(AddProvinceRequest request);

	@Mapping(target = "id",source = "province.id")
	@Mapping(target = "version",source = "province.version")
	@Mapping(target = "name",expression = "java( request.name() != null ? request.name() : province.name())")
	Province mapUpdateProvinceRequestToProvince(UpdateProvinceRequest request,Province province);

	ProvinceResponse mapProvinceToProvinceResponse(Province province);

}

