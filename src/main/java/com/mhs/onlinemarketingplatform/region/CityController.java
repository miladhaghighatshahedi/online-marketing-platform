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
import jakarta.validation.constraints.NotNull;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;
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
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.bind.annotation.*;

import java.util.*;

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

	@PostMapping("/api/cities")
	ResponseEntity<CityApiResponse<CityResponse>> add(@RequestBody AddCityRequest addCityRequest) {
		CityResponse addedCityResponse = this.cityService.add(addCityRequest);
		return ResponseEntity.ok(new CityApiResponse<>(true,"City saved successfully!",addedCityResponse));
	}

	@PostMapping("/api/cities/bulk")
	ResponseEntity<String> addBulk(@RequestBody AddBulkCityRequest addBulkCityRequest) {
		this.cityService.addBulk(addBulkCityRequest);
		return ResponseEntity.accepted().body("Add bulk cities accepted: processing add cities...");
	}

	@PutMapping("/api/cities")
	ResponseEntity<CityApiResponse<CityResponse>> update(@RequestBody UpdateCityRequest updateCityRequest) {
		CityResponse updatedCityResponse = this.cityService.update(updateCityRequest);
		return ResponseEntity.ok(new CityApiResponse<>(true,"City updated successfully!",updatedCityResponse));
	}

	@DeleteMapping("/api/cities/{id}")
	ResponseEntity<?> delete(@PathVariable("id") UUID id) {
		this.cityService.delete(id);
		return ResponseEntity.ok(new ProvinceApiResponse<>(true,"City deleted successfully!",null));
	}

	@GetMapping("/api/cities/{id}")
    ResponseEntity<CityApiResponse<CityResponse>> findById(@PathVariable("id") UUID id) {
		CityResponse fetchedCity = this.cityService.findById(id);
		return ResponseEntity.ok(new CityApiResponse<>(true,"City fetched successfully!",fetchedCity));
    }

	@GetMapping(value = "/api/cities",params = "name")
	ResponseEntity<CityApiResponse<CityResponse>> findById(@RequestParam("name") String name) {
		CityResponse fetchedCity = this.cityService.findByName(name);
		return ResponseEntity.ok(new CityApiResponse<>(true,"City fetched successfully!",fetchedCity));
	}

	@GetMapping(value = "/api/cities/{provinceId}",params = "name")
	ResponseEntity<CityApiResponse<CityResponse>> findByNameAndProvinceId(@RequestParam("name") String name,@PathVariable("provinceId") UUID provinceId) {
		CityResponse fetchedCity = this.cityService.findByNameAndProvinceId(name, provinceId);
		return ResponseEntity.ok(new CityApiResponse<>(true,"City fetched successfully!",fetchedCity));
	}

	@GetMapping("/api/cities/{provinceId}/province")
	ResponseEntity<CityApiResponse<List<CityResponse>>> findAllByProvinceId(@PathVariable("provinceId") UUID provinceId) {
		List<CityResponse> fetchedCities = this.cityService.findAllByProvinceId(provinceId);
		return ResponseEntity.ok(new CityApiResponse<>(true,"Cities fetched successfully!",fetchedCities));
	}

	@GetMapping("/api/cities/count")
	long countRootCategories() {
		return this.cityService.countCities();
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
	private final ApplicationEventPublisher applicationEventPublisher;

	public CityService(
			AuditLogger auditLogger,
			CityRepository cityRepository,
			ProvinceRepository provinceRepository,
			CityMapper cityMapper,
			MessageSource messageSource,
			ApplicationEventPublisher applicationEventPublisher) {
		this.auditLogger = auditLogger;
		this.cityRepository = cityRepository;
		this.provinceRepository = provinceRepository;
		this.cityMapper = cityMapper;
		this.messageSource = messageSource;
		this.applicationEventPublisher = applicationEventPublisher;
	}

	@Caching(evict = {
			@CacheEvict(value = "cities", allEntries = true),
			@CacheEvict(value = "city", allEntries = true),
			@CacheEvict(value = "cityByNameAndProvince", allEntries = true),
			@CacheEvict(value = "cityCount", allEntries = true)
	})
	public CityResponse add(AddCityRequest addCityRequest) {
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

    // CacheEvict moved on async method
	public void addBulk(AddBulkCityRequest addBulkCityRequest) {
		logger.info("Creating a bulk list of cities with provinceId: {}",addBulkCityRequest.provinceId());

		if(!this.provinceRepository.existsById(addBulkCityRequest.provinceId()))
		{
			throw new ProvinceNotFoundException(
						messageSource.getMessage("error.province.province.with.id.not.found",
								new Object[]{addBulkCityRequest.provinceId()},
								LocaleContextHolder.getLocale()), ProvinceErrorCode.PROVINCE_NOT_FOUND);}


		List<String> normalizedListOfNames = addBulkCityRequest.names().stream()
				.filter(Objects::nonNull)
				.map(String::trim)
				.filter(name -> !name.isBlank())
				.distinct()
				.toList();

		if (normalizedListOfNames.isEmpty()) { return;}

		List<City> existingCities = this.cityRepository.findAllByProvinceId(addBulkCityRequest.provinceId());

		List<String> existingNamesNormalized = existingCities.stream().map(City::name).toList();

		List<City> bulkCities = normalizedListOfNames.stream()
				.filter(name -> !existingNamesNormalized.contains(name))
				.map(nameNormalized -> cityMapper.mapStringToCity(nameNormalized,addBulkCityRequest.provinceId())).toList();

		if (bulkCities.isEmpty()) {
			return;
		}

		applicationEventPublisher.publishEvent(new AddBulkCityEvent(bulkCities,addBulkCityRequest.provinceId()));

	}

	@Caching(evict = {
			@CacheEvict(value = "cities", allEntries = true),
			@CacheEvict(value = "city", allEntries = true),
			@CacheEvict(value = "cityByNameAndProvince", allEntries = true),
			@CacheEvict(value = "cityCount", allEntries = true)
	})
	public CityResponse update(UpdateCityRequest updateCityRequest) {
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

	@Caching(evict = {
			@CacheEvict(value = "cities", allEntries = true),
			@CacheEvict(value = "city", allEntries = true),
			@CacheEvict(value = "cityByNameAndProvince", allEntries = true),
			@CacheEvict(value = "cityCount", allEntries = true)
	})
	public void delete(UUID id) {
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

	@Cacheable(key = "#id" , value = "city")
	public CityResponse findById(UUID id) {
		logger.info("Looking up city by ID: {}",id);

		City existingCity = this.cityRepository.findById(id).orElseThrow(() ->
				new CityNotFoundException(
						messageSource.getMessage("error.city.city.with.id.not.found",
								new Object[]{id},
								LocaleContextHolder.getLocale()),
						CityErrorCode.CITY_NOT_FOUND));

		return this.cityMapper.mapCityToCityResponse(existingCity);
	}

	@Cacheable(key = "#name" , value = "city")
	public CityResponse findByName(String name) {
		logger.info("Looking up city by NAME: {}",name);

		City existingCity = this.cityRepository.findByName(name).orElseThrow(() ->
				new CityNotFoundException(
						messageSource.getMessage("error.city.city.with.name.not.found",
								new Object[]{name},
								LocaleContextHolder.getLocale()),
						CityErrorCode.CITY_NOT_FOUND));

		return this.cityMapper.mapCityToCityResponse(existingCity);
	}

	@Cacheable(key = "#name + ':' + #provinceId",value = "cityByNameAndProvince")
	public CityResponse findByNameAndProvinceId(String name,UUID provinceId) {
		logger.info("Looking up city by NAME: {} and ProvinceId: {}",name,provinceId);

		City retrievedCity = this.cityRepository.findByNameAndProvinceId(name, provinceId).orElseThrow(() ->
				new CityNotFoundException(
						messageSource.getMessage("error.city.city.with.name.and.province.id.not.found",
								new Object[]{name, provinceId},
								LocaleContextHolder.getLocale()),
						CityErrorCode.CITY_NOT_FOUND));

		return this.cityMapper.mapCityToCityResponse(retrievedCity);
	}

	@Cacheable(value = "cities")
	public List<CityResponse> findAllByProvinceId(UUID provinceId) {
		logger.info("Retriving all cities by province id: {}",provinceId);
		List<City> exisitingCities = this.cityRepository.findAllByProvinceId(provinceId);
		return this.cityMapper.mapListToListOfResponse(exisitingCities);
	}

	public boolean existsById(UUID id) {
		return this.cityRepository.existsById(id);
	}

	public Optional<String> findNameById(UUID id) {
		return this.cityRepository.findNameById(id);
	}

	@Cacheable(value = "cityCount")
	public long countCities() {
		return this.cityRepository.countCities();
	}

}

@Repository
interface CityRepository extends CrudRepository<City,UUID> {

	@Query("SELECT * FROM cities WHERE name= :name")
	Optional<City> findByName(@Param("name") String name);

	Optional<City> findByNameAndProvinceId(String name, UUID provinceId);

	@Query("SELECT * FROM cities WHERE province_id= :provinceId ORDER BY name")
	List<City> findAllByProvinceId(@Param("provinceId") UUID provinceId);

	@Query("SELECT CASE WHEN COUNT(1) > 0  THEN TRUE ELSE FALSE END FROM cities WHERE name= :name AND province_id= :provinceId")
	boolean existsByNameAndProvinceId(@Param("name") String name,@Param("provinceId") UUID provinceId);

	@Query("SELECT CASE WHEN COUNT(1) > 0 THEN TRUE ELSE FALSE END FROM cities WHERE id = :id")
	boolean existsById(@Param("id") UUID id);

	@Query("SELECT name FROM cities WHERE id= :id")
	Optional<String> findNameById(@Param("id") UUID id);

	@Query("SELECT COUNT(*) FROM cities c")
	long countCities();
}

@Table("cities")
record City(
		@Id UUID id,
		@Version int version,
		@NotNull String name,
		@NotNull UUID provinceId) {}

record AddCityRequest(
		@NotNull String name,
		@NotNull UUID provinceId
) {}

record AddBulkCityRequest(
		@NotNull List<String> names,
		@NotNull UUID provinceId
){}

record UpdateCityRequest(
		@NotNull UUID id,
		@NotNull String name,
		@NotNull UUID provinceId
) {}

record CityResponse(
		UUID id,
		String name,
		UUID provinceId
) {}

record CityApiResponse<T>(
		boolean success,
		String message,
		T data
) {}

@Mapper(componentModel = "spring" , nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,imports = {UuidCreator.class})
interface CityMapper {

	@Mapping(target = "id",expression = "java(UuidCreator.getTimeOrderedEpoch())")
	@Mapping(target = "version", ignore = true)
	City mapAddCityRequestToCity(AddCityRequest request);

	@Mapping(target = "id",expression = "java(UuidCreator.getTimeOrderedEpoch())")
	@Mapping(target = "version", constant = "0")
	@Mapping(target = "provinceId", source = "provinceId")
	City mapStringToCity(String name,UUID provinceId);

	@Mapping(target = "id", source = "city.id")
	@Mapping(target = "version", source = "city.version")
	@Mapping(target = "name", expression = "java(request.name() != null ? request.name() : city.name())")
	@Mapping(target = "provinceId", expression = "java(request.provinceId() != null ? request.provinceId() : city.provinceId())")
	City mapUpdateCityRequestToCity(UpdateCityRequest request,City city);

	CityResponse mapCityToCityResponse(City city);

	List<CityResponse> mapListToListOfResponse(Iterable<City> cities);


}

record AddBulkCityEvent(List<City> cities,UUID provinceId) {}

@Component
class AddBulkCityEventHandler {

	private final CityRepository cityRepository;
	private final AuditLogger auditLogger;
	private final static Logger logger = LoggerFactory.getLogger(AddBulkCityEventHandler.class);

	public AddBulkCityEventHandler(
			CityRepository cityRepository,
			AuditLogger auditLogger) {
		this.cityRepository = cityRepository;
		this.auditLogger = auditLogger;
	}

	@Caching(evict = {
			@CacheEvict(value = "cities", allEntries = true),
			@CacheEvict(value = "city", allEntries = true),
			@CacheEvict(value = "cityByNameAndProvince", allEntries = true),
			@CacheEvict(value = "cityCount", allEntries = true)
	})
	@Async("cityTaskExecutor")
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void handleAddBulkCityEvent(AddBulkCityEvent addBulkCityEvent) {
		logger.info("Calling ASYNC method with thread {} to store {} cities to the province with ID {} ",Thread.currentThread().getName(),addBulkCityEvent.cities().size(),addBulkCityEvent.provinceId());
		List<City> cities = addBulkCityEvent.cities();
		if (cities == null || cities.isEmpty()) return;

		this.cityRepository.saveAll(addBulkCityEvent.cities());
		this.auditLogger.log("CITY_BULK_ADD", "CITY_BULK_ADD", "BULK CITIES add to the province with ID: "+addBulkCityEvent.provinceId());
	}

}
