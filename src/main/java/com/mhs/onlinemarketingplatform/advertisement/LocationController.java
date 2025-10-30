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
import com.mhs.onlinemarketingplatform.advertisement.error.location.LocationCityNotFoundException;
import com.mhs.onlinemarketingplatform.advertisement.error.location.LocationProvinceNotFoundException;
import com.mhs.onlinemarketingplatform.common.AuditLogger;

import com.mhs.onlinemarketingplatform.advertisement.error.location.LocationErrorCode;
import com.mhs.onlinemarketingplatform.advertisement.error.location.LocationNotFoundException;
import com.mhs.onlinemarketingplatform.region.api.CityApi;
import com.mhs.onlinemarketingplatform.region.api.ProvinceApi;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.repository.CrudRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

/**
 * @author Milad Haghighat Shahedi
 */
@Controller
@ResponseBody
class LocationController {

	private final LocationService locationService;

	public LocationController(LocationService locationService) {
		this.locationService = locationService;
	}

	@PostMapping("/api/location")
	ResponseEntity<LocationResponse> add(@RequestBody AddLocationRequest addLocationRequest) {
		return ResponseEntity.ok(this.locationService.add(addLocationRequest));
	}

	@PutMapping("/api/location")
	ResponseEntity<LocationResponse> update(@RequestBody UpdateLocationRequest updateLocationRequest) {
		return ResponseEntity.ok(this.locationService.update(updateLocationRequest));
	}

	@DeleteMapping("/api/location/{id}")
	ResponseEntity<?> delete(@PathVariable("id") UUID id) {
		this.locationService.delete(id);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/api/location/{id}")
	ResponseEntity<LocationResponse> findById(@PathVariable("id") UUID id) {
		return ResponseEntity.ok(this.locationService.getLocationById(id));
	}

}

@Service
@Transactional
class LocationService {

	private static final Logger logger = LoggerFactory.getLogger(LocationService.class);
	private final AuditLogger auditLogger;

		private final LocationRepository locationRepository;
		private final ProvinceApi provinceApi;
		private final CityApi cityApi;
		private final LocationMapper locationMapper;
		private final MessageSource messageSource;

	public LocationService(
			AuditLogger auditLogger,
			LocationRepository locationRepository,
			ProvinceApi provinceApi,
			CityApi cityApi,
			LocationMapper locationMapper,
			MessageSource messageSource) {
		this.auditLogger = auditLogger;
		this.locationRepository = locationRepository;
		this.provinceApi = provinceApi;
		this.cityApi = cityApi;
		this.locationMapper = locationMapper;
		this.messageSource = messageSource;
	}

	LocationResponse add(AddLocationRequest addLocationRequest) {
	    logger.info("Creating new location with latitude: {} and longitude: {}",addLocationRequest.latitude(),addLocationRequest.longitude());

	    if(!this.provinceApi.existsById(addLocationRequest.provinceId())) {
			throw new LocationProvinceNotFoundException(
				    messageSource.getMessage("error.location.province.with.id.not.found",
						    new Object[]{addLocationRequest.provinceId()},
						    LocaleContextHolder.getLocale()),
				    LocationErrorCode.PROVINCE_NOT_FOUND);}

		if(!this.cityApi.existsById(addLocationRequest.cityId())) {
			throw new LocationCityNotFoundException(
					messageSource.getMessage("error.location.city.with.id.not.found",
							new Object[]{addLocationRequest.provinceId()},
							LocaleContextHolder.getLocale()),
					LocationErrorCode.CITY_NOT_FOUND);
		}

		Location mappedLocation = this.locationMapper.mapAddLocationRequestToLocation(addLocationRequest);
		Location storedLocation = this.locationRepository.save(mappedLocation);
		this.auditLogger.log("LOCATION_CREATED", "LOCATION", "Location ID: " + storedLocation.id());
        return this.locationMapper.mappLocationToLocationResponse(storedLocation);
	}

	LocationResponse update(UpdateLocationRequest updateLocationRequest) {
		logger.info("Updating existing location with latitude: {} and longitude: {}",updateLocationRequest.latitude(),updateLocationRequest.longitude());

		Location exisitingLocation = this.locationRepository.findById(updateLocationRequest.id()).orElseThrow(
				() -> new LocationNotFoundException(
						messageSource.getMessage("error.location.location.with.id.not.found",
								new Object[]{updateLocationRequest.id()},
								LocaleContextHolder.getLocale()),
						LocationErrorCode.LOCATION_NOT_FOUND));

		if(!this.provinceApi.existsById(updateLocationRequest.provinceId())) {
			throw new LocationProvinceNotFoundException(
					messageSource.getMessage("error.location.province.with.id.not.found",
							new Object[]{updateLocationRequest.provinceId()},
							LocaleContextHolder.getLocale()),
					LocationErrorCode.PROVINCE_NOT_FOUND);}

		if(!this.cityApi.existsById(updateLocationRequest.cityId())) {
			throw new LocationCityNotFoundException(
					messageSource.getMessage("error.location.city.with.id.not.found",
							new Object[]{updateLocationRequest.cityId()},
							LocaleContextHolder.getLocale()),
					LocationErrorCode.CITY_NOT_FOUND);
		}

		Location mappedLocation = this.locationMapper.mapUpdateLocationRequestToLocation(updateLocationRequest, exisitingLocation);
		Location storedLocation = this.locationRepository.save(mappedLocation);
		this.auditLogger.log("LOCATION_UPDATED", "LOCATION", "Location ID: " + storedLocation.id());
		return this.locationMapper.mappLocationToLocationResponse(storedLocation);
	}

	void delete(UUID id) {
		Location location = this.locationRepository.findById(id).orElseThrow(
			() -> new LocationNotFoundException(
					messageSource.getMessage("error.location.location.with.id.not.found",
							new Object[]{id},
							LocaleContextHolder.getLocale()),
					LocationErrorCode.LOCATION_NOT_FOUND));
		logger.info("Deleting exisiting location with id: {} ",location.id());

		this.locationRepository.delete(location);
		this.auditLogger.log("LOCATION_DELETED", "LOCATION", "Location id: " + location.id());
	}

	LocationResponse getLocationById(UUID id) {
		logger.info("Looking up location by ID: {}",id);

		Location location = this.locationRepository.findById(id).orElseThrow(
				() -> new LocationNotFoundException(
						messageSource.getMessage("error.location.location.with.id.not.found",
								new Object[]{id},
								LocaleContextHolder.getLocale()),
						LocationErrorCode.LOCATION_NOT_FOUND));

		String provinceName = this.provinceApi.findNameById(location.provinceId()).orElseThrow(
				()-> new LocationProvinceNotFoundException(
						messageSource.getMessage("error.location.province.with.id.not.found",
								new Object[]{location.provinceId()},
								LocaleContextHolder.getLocale()),
						LocationErrorCode.PROVINCE_NOT_FOUND));


		String cityName = this.cityApi.findNameById(location.cityId()).orElseThrow(
				() -> new LocationCityNotFoundException(
						messageSource.getMessage("error.location.city.with.id.not.found",
								new Object[]{location.cityId()},
								LocaleContextHolder.getLocale()),
						LocationErrorCode.CITY_NOT_FOUND));

		return this.locationMapper.mappToLocationResponse(location.id(), provinceName, cityName);
	}

}

@Repository
interface LocationRepository extends CrudRepository<Location,UUID> {
	Optional<Location> findByCityId(UUID cityId);
}

@Table("locations")
record Location(
		@Id
		UUID id,
		@Version
		int version,
		Double latitude,
		Double longitude,
		UUID provinceId,
		UUID cityId) {}

record AddLocationRequest(
		Double latitude,
		Double longitude,
		UUID provinceId,
		UUID cityId
){}

record UpdateLocationRequest(
		UUID id,
		Double latitude,
		Double longitude,
		UUID provinceId,
		UUID cityId
){}

record LocationResponse(
	UUID id,
	String provinceName,
	String cityName
){}

@Mapper( componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE,imports = {UuidCreator.class})
interface LocationMapper {

	@Mapping(target="id", expression = ("java(UuidCreator.getTimeOrderedEpoch())"))
	@Mapping(target = "version", ignore = true)
	Location mapAddLocationRequestToLocation(AddLocationRequest addLocationRequest);

	@Mapping(target = "id",source = "location.id")
	@Mapping(target = "version",source = "location.version")
	@Mapping(target = "latitude",expression = "java(request.latitude() != null ? request.latitude() : location.latitude())")
	@Mapping(target = "longitude",expression = "java(request.longitude() != null ? request.longitude() : location.longitude())")
	@Mapping(target = "provinceId",expression = "java(request.provinceId() != null ? request.provinceId() : location.provinceId())")
	@Mapping(target = "cityId",expression = "java(request.cityId() != null ? request.cityId() : location.cityId())")
	Location mapUpdateLocationRequestToLocation(UpdateLocationRequest request,Location location);

	LocationResponse mappLocationToLocationResponse(Location location);

	@Mapping(target = "id",source = "locationId")
	@Mapping(target = "provinceName",source = "provinceName")
	@Mapping(target = "cityName",source = "cityName")
	LocationResponse mappToLocationResponse(UUID locationId,String provinceName,String cityName);

}
