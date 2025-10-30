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
import com.mhs.onlinemarketingplatform.region.api.CityApi;
import com.mhs.onlinemarketingplatform.region.error.city.CityAlreadyExistsException;
import com.mhs.onlinemarketingplatform.region.error.city.CityErrorCode;
import com.mhs.onlinemarketingplatform.region.error.city.CityNotFoundException;
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

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * @author Milad Haghighat Shahedi
 */
@Controller
@ResponseBody
class CityController {

	private final CityService cityService;

	public CityController(CityService cityService) {
		this.cityService = cityService;
	}

	@GetMapping("/api/cities")
	ResponseEntity<CityResponse> add(@RequestBody AddCityRequest addCityRequest) {
		return ResponseEntity.ok(this.cityService.add(addCityRequest));
	}

	@PutMapping("/api/cities")
	ResponseEntity<CityResponse> add(@RequestBody UpdateCityRequest updateCityRequest) {
		return ResponseEntity.ok(this.cityService.update(updateCityRequest));
	}

	@DeleteMapping("/api/cities/{id}")
	ResponseEntity<?> delete(@PathVariable("id") UUID id) {
		this.cityService.delete(id);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/api/cities/{id}")
    ResponseEntity<CityResponse> findById(@PathVariable("id") UUID id) {
		return ResponseEntity.ok(this.cityService.findById(id));
    }

	@GetMapping(value = "/api/cities",params = "name")
	ResponseEntity<CityResponse> findById(@RequestParam("name") String name) {
		return ResponseEntity.ok(this.cityService.findByName(name));
	}

	@GetMapping(value = "/api/cities/{provinceId}",params = "name")
	ResponseEntity<CityResponse> findByNameAndProvinceId(@RequestParam("name") String name,@PathVariable("provinceId") UUID provinceId) {
		return ResponseEntity.ok(this.cityService.findByNameAndProvinceId(name,provinceId));
	}

	@GetMapping("/api/cities/province/{provinceId}")
	ResponseEntity<Set<CityResponse>> findAllByProvinceId(@PathVariable("provinceId") UUID provinceId) {
		return ResponseEntity.ok(this.cityService.findAllByProvinceId(provinceId));
	}

}

@Service
@Transactional
class CityService implements CityApi {

	private static final Logger logger = LoggerFactory.getLogger(CityService.class);
	private final AuditLogger auditLogger;

	private final CityRepository cityRepository;
	private final ProvinceRepository provinceRepository;
	private final CityMapper cityMapper;
	private final MessageSource messageSource;

	public CityService(
			AuditLogger auditLogger,
			CityRepository cityRepository,
			ProvinceRepository provinceRepository,
			CityMapper cityMapper,
			MessageSource messageSource) {
		this.auditLogger = auditLogger;
		this.cityRepository = cityRepository;
		this.provinceRepository = provinceRepository;
		this.cityMapper = cityMapper;
		this.messageSource = messageSource;
	}

	CityResponse add(AddCityRequest addCityRequest) {
		logger.info("Creating new city with name: {} and provinceId: {}",addCityRequest.name(),addCityRequest.provinceId());

		this.provinceRepository.findById(addCityRequest.provinceId()).orElseThrow(() ->
				new ProvinceNotFoundException(
						messageSource.getMessage("error.province.province.with.id.not.found",
								new Object[]{addCityRequest.provinceId()},
								LocaleContextHolder.getLocale()), ProvinceErrorCode.PROVINCE_NOT_FOUND));

		if(this.cityRepository.existsByNameAndProvinceId(addCityRequest.name(), addCityRequest.provinceId())){
			throw new CityAlreadyExistsException(
					messageSource.getMessage("error.city.city.already.exists",
							new Object[] {addCityRequest.name()},
							LocaleContextHolder.getLocale()),
			CityErrorCode.CITY_ALREADY_EXISTS);
		}

		City mappedCity = this.cityMapper.mapAddCityRequestToCity(addCityRequest);
		City storedCity = this.cityRepository.save(mappedCity);
		this.auditLogger.log("CITY_CREATED", "CITY", "City ID: " + storedCity.id());
		return this.cityMapper.mapCityToCityResponse(storedCity);
	}

	CityResponse update(UpdateCityRequest updateCityRequest) {
		logger.info("Updating existing city with name: {} and provinceId: {}",updateCityRequest.name(),updateCityRequest.provinceId());

		City existingCity = this.cityRepository.findById(updateCityRequest.id()).orElseThrow(() ->
			 new CityNotFoundException(
					messageSource.getMessage("error.city.city.with.id.not.found",
							new Object[]{updateCityRequest.id()},
							LocaleContextHolder.getLocale()),
					CityErrorCode.CITY_NOT_FOUND));

		Province exisitngProvince = this.provinceRepository.findById(updateCityRequest.provinceId()).orElseThrow(() ->
				new ProvinceNotFoundException(
						messageSource.getMessage("error.province.province.with.id.not.found",
								new Object[]{updateCityRequest.provinceId()},
								LocaleContextHolder.getLocale()),
						ProvinceErrorCode.PROVINCE_NOT_FOUND));

		City mappedCity = this.cityMapper.mapUpdateCityRequestToCity(updateCityRequest, existingCity);
		City storedCity = this.cityRepository.save(mappedCity);
		this.auditLogger.log("CITY_UPDATED", "CITY", "City ID: " + storedCity.id());
		return this.cityMapper.mapCityToCityResponse(storedCity);
	}

	void delete(UUID id) {
		City existingCity = this.cityRepository.findById(id).orElseThrow(() ->
				new CityNotFoundException(
						messageSource.getMessage("error.city.city.with.id.not.found",
								new Object[]{id},
								LocaleContextHolder.getLocale()),
						CityErrorCode.CITY_NOT_FOUND));
		logger.info("Deleting exisiting city with id: {} ",existingCity.id());

		this.cityRepository.delete(existingCity);
		this.auditLogger.log("CITY_DELETED", "CITY", "City name: " + existingCity.name());
	}

	CityResponse findById(UUID id) {
		logger.info("Looking up city by ID: {}",id);

		City existingCity = this.cityRepository.findById(id).orElseThrow(() ->
				new CityNotFoundException(
						messageSource.getMessage("error.city.city.with.id.not.found",
								new Object[]{id},
								LocaleContextHolder.getLocale()),
						CityErrorCode.CITY_NOT_FOUND));

		return this.cityMapper.mapCityToCityResponse(existingCity);
	}

	CityResponse findByName(String name) {
		logger.info("Looking up city by NAME: {}",name);

		City existingCity = this.cityRepository.findByName(name).orElseThrow(() ->
				new CityNotFoundException(
						messageSource.getMessage("error.city.city.with.name.not.found",
								new Object[]{name},
								LocaleContextHolder.getLocale()),
						CityErrorCode.CITY_NOT_FOUND));

		return this.cityMapper.mapCityToCityResponse(existingCity);
	}

	CityResponse findByNameAndProvinceId(String name,UUID provinceId) {
		logger.info("Looking up city by NAME: {} and ProvinceId: {}",name,provinceId);

		City retrievedCity = this.cityRepository.findByNameAndProvinceId(name, provinceId).orElseThrow(() ->
				new CityNotFoundException(
						messageSource.getMessage("error.city.city.with.name.and.province.id.not.found",
								new Object[]{name, provinceId},
								LocaleContextHolder.getLocale()),
						CityErrorCode.CITY_NOT_FOUND));

		return this.cityMapper.mapCityToCityResponse(retrievedCity);
	}

	Set<CityResponse> findAllByProvinceId(UUID provinceId) {
		logger.info("Retriving all cities by province id: {}",provinceId);

		Set<City> retrievedCities = this.cityRepository.findAllByProvinceId(provinceId);
		return this.cityMapper.mapToASetOfCityResponses(retrievedCities);
	}

	public boolean existsById(UUID id) {
		return this.cityRepository.existsById(id);
	}

	public Optional<String> findNameById(UUID id) {
		return this.cityRepository.findNameById(id);
	}

}

@Repository
interface CityRepository extends CrudRepository<City,UUID> {

	@Query("SELECT * FROM cities WHERE name= :name")
	Optional<City> findByName(@Param("name") String name);

	Optional<City> findByNameAndProvinceId(String name, UUID provinceId);

	@Query("SELECT * FROM cities WHERE province_id = :provinceId ORDER BY name")
	Set<City> findAllByProvinceId(@Param("provinceId") UUID provinceId);

	@Query("SELECT CASE WHEN COUNT(1) > 0  THEN TRUE ELSE FALSE END FROM cities WHERE name= :name AND province_id= :provinceId")
	boolean existsByNameAndProvinceId(@Param("name") String name,@Param("provinceId") UUID provinceId);

	@Query("SELECT CASE WHEN COUNT(1) > 1 THEN TRUE ELSE FALSE END FROM cities WHERE id = :iD")
	boolean existsById(@Param("id") UUID id);

	@Query("SELECT name FROM cities WHERE id= :id")
	Optional<String> findNameById(@Param("id") UUID id);
}

@Table("cities")
record City(
		@Id UUID id,
		@Version
		int version,
		String name,
		UUID provinceId) {}

record AddCityRequest(
	String name,
	UUID provinceId
) {}

record UpdateCityRequest(
		UUID id,
		String name,
		UUID provinceId
) {}

record CityResponse(
		UUID id,
		String name,
		UUID provinceId
) {}

@Mapper(componentModel = "spring" , nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,imports = {UuidCreator.class})
interface CityMapper {

	@Mapping(target = "id",expression = "java(UuidCreator.getTimeOrderedEpoch())")
	@Mapping(target = "version", ignore = true)
	City mapAddCityRequestToCity(AddCityRequest request);

	@Mapping(target = "id", source = "city.id")
	@Mapping(target = "version", source = "city.version")
	@Mapping(target = "name", expression = "java(request.name() != null ? request.name() : city.name())")
	@Mapping(target = "provinceId", expression = "java(request.provinceId() != null ? request.provinceId() : city.provinceId())")
	City mapUpdateCityRequestToCity(UpdateCityRequest request,City city);

	CityResponse mapCityToCityResponse(City city);

	Set<CityResponse> mapToASetOfCityResponses(Set<City> cities);

}
