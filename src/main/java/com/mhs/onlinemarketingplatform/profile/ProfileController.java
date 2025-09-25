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
package com.mhs.onlinemarketingplatform.profile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;
import org.springframework.amqp.core.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.repository.CrudRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * @author Milad Haghighat Shahedi
 */
@Controller
@ResponseBody
@Validated
class ProfileController {

    private final ProfileService profileService;
    private final static String OWNER = "26712732-813a-4b3b-8ecf-54e47e428160";

	ProfileController(ProfileService profileService) {
		this.profileService = profileService;
	}

	@PostMapping("/api/profiles")
	ResponseEntity<ProfileResponse> addByOwner(@RequestBody AddProfileRequest addProfileRequest) {
		return ResponseEntity.ok(this.profileService.addByOwner(addProfileRequest,UUID.fromString(OWNER)));
    }

    @GetMapping("/api/profiles")
    ResponseEntity<ProfileResponse> findByOwner() {
	    return ResponseEntity.ok(this.profileService.findByOwner(UUID.fromString(OWNER)));
    }

    @GetMapping("/api/profiles/{id}")
    ResponseEntity<ProfileResponse> findByIdAndOwner(@PathVariable("id") String id) {
	    return ResponseEntity.ok(this.profileService.findByIdAndOwner(UUID.fromString(id),UUID.fromString(OWNER)));
    }

    @PutMapping("/api/profiles/{id}")
    ResponseEntity<ProfileResponse> updateByIdAndOwner(@RequestBody UpdateProfileRequest updateProfileRequest, @PathVariable("id") String id) {
	    return ResponseEntity.ok(this.profileService.updateByIdAndOwner(updateProfileRequest,UUID.fromString(id),UUID.fromString(OWNER)));
    }

    @GetMapping(value = "/api/profiles", params = "name")
    ResponseEntity<ProfileResponse> findByNameAndOwner(@RequestParam("name") String name) {
	    return ResponseEntity.ok(this.profileService.findByNameAndOwner(name,UUID.fromString(OWNER)));
    }

    @PutMapping("/api/profiles/{id}/activate")
    ResponseEntity<ProfileResponse> activateByOwner(@PathVariable("id") String id) {
        return ResponseEntity.ok(this.profileService.activateByOwner(UUID.fromString(id), UUID.fromString(OWNER)));
    }

    @PutMapping("/api/profiles/{id}/deactivate")
    ResponseEntity<ProfileResponse> deactivateByOwner(@PathVariable("id") String id) {
	    return ResponseEntity.ok(this.profileService.deactivateByOwner(UUID.fromString(id), UUID.fromString(OWNER)));
    }

}

@Service
@Transactional
class ProfileService {

	private final ProfileRepository profileRepository;
	private final ProfileMapper mapper;
	private final ApplicationEventPublisher publisher;

	ProfileService(ProfileRepository profileRepository, ProfileMapper mapper, ApplicationEventPublisher publisher) {
		this.profileRepository = profileRepository;
		this.mapper = mapper;
		this.publisher = publisher;
	}

	ProfileResponse addByOwner(AddProfileRequest addProfileRequest, UUID owner) {
		if(this.credentialExists(owner)){
			throw new ProfileAlreadyExistsException("Profile with this credential already exists");
		}

		if (this.profileNameExists(addProfileRequest.name())) {
			throw new ProfileAlreadyExistsException("Profile with this name " + addProfileRequest.name() + " already exists");
		}

		Profile mappedProduct = this.mapper.mapAddRequestToProfile(addProfileRequest);
		Profile storedProfile = this.profileRepository.save(mappedProduct);
		this.publisher.publishEvent(new AddProfileEvent(storedProfile.id()));
		return this.mapper.mapProfileToResponse(storedProfile);
	}

	ProfileResponse activateByOwner(UUID id, UUID owner) {
		Profile exisitngProfile = this.profileRepository.findByIdAndCredential(id, owner)
				.orElseThrow(() -> new ProfileNotFoundException("Profile with id " + id + " not found"));

		if (!exisitngProfile.profileStatus().status.trim().equals("ACTIVE")) {
			Profile updatingProfile = Profile.withProfileStatusActivated(exisitngProfile, owner);
			Profile storedProfile = this.profileRepository.save(updatingProfile);
			this.publisher.publishEvent(new UpdateProfileEvent(storedProfile.id()));
			return this.mapper.mapProfileToResponse(storedProfile);
		}

		throw new ProfileAlreadyExistsException("Profile with name " + exisitngProfile.name() + " is already enabled");
	}

	ProfileResponse deactivateByOwner(UUID id, UUID owner) {
		Profile exisitngProfile = this.profileRepository.findByIdAndCredential(id, owner)
				.orElseThrow(() -> new ProfileNotFoundException("Profile with id " + id + " not found"));

		if (!exisitngProfile.profileStatus().status.trim().equals("INACTIVE")) {
			Profile updatingProfile = Profile.withProfileStatusDeactivated(exisitngProfile, owner);
			Profile storedProfile = this.profileRepository.save(updatingProfile);
			this.publisher.publishEvent(new UpdateProfileEvent(storedProfile.id()));
			return this.mapper.mapProfileToResponse(storedProfile);
		}

		throw new ProfileAlreadyDisabledException("Profile with name " + exisitngProfile.name() + " is already disabled");
	}

	ProfileResponse updateByIdAndOwner(UpdateProfileRequest updateProfileRequest, UUID id, UUID owner) {
		Profile exisitingProfile = this.profileRepository.findByIdAndCredential(id, owner)
				.orElseThrow(() -> new ProfileNotFoundException("Profile not found with id: " + id));

		Profile mappedProfile = this.mapper.mapUpdateRequestToProfile(updateProfileRequest, exisitingProfile);
		Profile storedProfile = this.profileRepository.save(mappedProfile);
		this.publisher.publishEvent(new UpdateProfileEvent(storedProfile.id()));
		return this.mapper.mapProfileToResponse(storedProfile);
	}

	ProfileResponse findByNameAndOwner(String name,UUID owner) {
		Profile profile = this.profileRepository.findByNameAndCredential(name,owner)
				.orElseThrow(() -> new ProfileNotFoundException("Profile with name " + name + " not found"));
		return this.mapper.mapProfileToResponse(profile);
	}

	ProfileResponse findByIdAndOwner(UUID id,UUID owner) {
		Profile profile = this.profileRepository.findByIdAndCredential(id,owner)
				.orElseThrow(() -> new ProfileNotFoundException("Profile with id " + id + " not found"));
		return this.mapper.mapProfileToResponse(profile);
	}

	ProfileResponse findByOwner(UUID owner) {
		Profile profile = this.profileRepository.findByCredential(owner)
				.orElseThrow(() -> new ProfileNotFoundException("Profile with  " + owner + " not found"));
		return this.mapper.mapProfileToResponse(profile);
	}

	boolean credentialExists(UUID credential){
		return this.profileRepository.existsByCredential(credential);
	}

	boolean profileNameExists(String name){
		return this.profileRepository.existsByName(name);
	}

}

@Repository
interface ProfileRepository extends CrudRepository<Profile, Integer> {

	Optional<Profile> findByIdAndCredential(UUID profileId, UUID credential);

	Optional<Profile> findByCredential(UUID credential);

	Optional<Profile> findByNameAndCredential(String name,UUID credential);

	Optional<Profile> findById(UUID id);

	boolean existsByCredential(UUID credential);

	boolean existsByName(String name);

}

@Table(name = "profiles")
record Profile(@Id UUID id,
               @Version Integer version,
               String name,
               String about,
               ProfileType profileType,
               ProfileStatus profileStatus,
               LocalDateTime activationDate,
               UUID credential) {

	static Profile createNewProfile(AddProfileRequest addProfileRequest, UUID credential) {
		return new Profile(
				UUID.randomUUID(),
				null,
				addProfileRequest.name(),
				addProfileRequest.about(),
				ProfileType.valueOf(addProfileRequest.profileType().trim().toUpperCase()),
				ProfileStatus.INACTIVE,
				null,
				credential);
	}

	static Profile withProfileStatusActivated(Profile exisitngProfile, UUID credential) {
		return new Profile(
				exisitngProfile.id,
				exisitngProfile.version(),
				exisitngProfile.name,
				exisitngProfile.about,
				exisitngProfile.profileType,
				ProfileStatus.ACTIVE,
				LocalDateTime.now(),
				credential);
	}

	static Profile withProfileStatusDeactivated(Profile exisitngProfile, UUID credential) {
		return new Profile(exisitngProfile.id,
				exisitngProfile.version(),
				exisitngProfile.name,
				exisitngProfile.about,
				exisitngProfile.profileType,
				ProfileStatus.INACTIVE,
				LocalDateTime.now(),
				credential);
	}

}

record AddProfileRequest(
		@NotNull String name,
		@NotBlank String about,
		@NotNull String profileType) {}

record UpdateProfileRequest(
		@NotNull String name,
		@NotBlank String about,
		@NotNull String profileType) {}

record ProfileResponse(String id,
                       String name,
                       String about,
                       String profileType,
                       String profileStatus,
                       LocalDateTime activationDate,
                       String credential) {}

record ProfilePagedResponse<T>(
		List<T> content,
		int page,
		int size,
		long totalElements,
		int totalPages) {}

enum ProfileType {

	INDIVIDUAL("INDIVIDUAL"),
	COMPANY("COMPANY"),
	STORE("STORE");

	final String type;

	ProfileType(String type) {
		this.type = type;
	}

}

enum ProfileStatus {

	ACTIVE("ACTIVE"),
	INACTIVE("INACTIVE");

	final String status;

	ProfileStatus(String status) {
		this.status = status;
	}
}

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
interface ProfileMapper {

	@org.mapstruct.Mapping(target = "id", expression = "java(java.util.UUID.randomUUID())")
	@org.mapstruct.Mapping(target = "version", ignore = true)
	@org.mapstruct.Mapping(target = "profileStatus", constant = "ACTIVE")
	@org.mapstruct.Mapping(target = "activationDate", expression = "java(java.time.LocalDateTime.now())")
	Profile mapAddRequestToProfile(AddProfileRequest addProfileRequest);

	@BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
	default Profile mapUpdateRequestToProfile(UpdateProfileRequest updateProfileRequest, Profile profile) {
		return new Profile(
				profile.id(),
				profile.version(),
				updateProfileRequest.name(),
				updateProfileRequest.about(),
				ProfileType.valueOf(updateProfileRequest.profileType()),
				ProfileStatus.INACTIVE,
				profile.activationDate(),
				profile.credential());
	}

	@org.mapstruct.Mapping(target = "profileStatus", source = "profileStatus")
	@Mapping(target = "profileType", source = "profileType")
	ProfileResponse mapProfileToResponse(Profile profile);

	default String status(ProfileStatus status) {
		return status != null ? status.name() : null;
	}

	default String type(ProfileType type) {
		return type != null ? type.name() : null;
	}

}

class ProfileNotFoundException extends RuntimeException {
	ProfileNotFoundException(String message) {
		super(message);
	}
}

class ProfileAlreadyExistsException extends RuntimeException {
	ProfileAlreadyExistsException(String message) {
		super(message);
	}
}

class ProfileAlreadyEnabledException extends RuntimeException {
	ProfileAlreadyEnabledException(String message) {
		super(message);
	}
}

class ProfileAlreadyDisabledException extends RuntimeException {
	ProfileAlreadyDisabledException(String message) {
		super(message);
	}
}

// controller // service // repository // model // enum // dto // mapper // exception

@Configuration
class RabbitMqProfilesIntegrationConfig {

	public static final String PROFILE_Q = "profiles";

	@Bean
	Binding profileBinding(Queue profileQueue, Exchange profileExchange) {
		return BindingBuilder.bind(profileQueue).to(profileExchange).with(PROFILE_Q).noargs();
	}

	@Bean
	Exchange profileExchange() {
		return ExchangeBuilder.directExchange(PROFILE_Q).build();
	}

	@Bean
	Queue profileQueue() {
		return QueueBuilder.durable(PROFILE_Q).build();
	}

}
