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
    private final static String CREDENTIAL = "26712732-813a-4b3b-8ecf-54e47e428160";

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @PostMapping("/api/profiles")
    public ResponseEntity<ProfileResponse> create(@RequestBody AddProfileRequest addProfileRequest) {
        return ResponseEntity.ok(this.profileService.add(addProfileRequest, UUID.fromString(CREDENTIAL)));
    }

    @GetMapping("/api/profiles")
    public ResponseEntity<ProfileResponse> findByCredential() {
        return ResponseEntity.ok(this.profileService.findByCredential(UUID.fromString(CREDENTIAL)));
    }

    @GetMapping("/api/profiles/{id}")
    public ResponseEntity<ProfileResponse> findById(@PathVariable("id") String id) {
        return ResponseEntity.ok(this.profileService.findById(UUID.fromString(id)));
    }

    @PutMapping("/api/profiles/{id}")
    public ResponseEntity<ProfileResponse> update(@RequestBody UpdateProfileRequest updateProfileRequest, @PathVariable("id") String id) {
        return ResponseEntity.ok(this.profileService.update(updateProfileRequest, UUID.fromString(id), UUID.fromString(CREDENTIAL)));
    }

    @GetMapping(value = "/api/profiles", params = "name")
    public ResponseEntity<ProfileResponse> findByName(@RequestParam("name") String name) {
        return ResponseEntity.ok(this.profileService.findByName(name));
    }

    @PutMapping("/api/profiles/{id}/activate")
    public ResponseEntity<ProfileResponse> activate(@PathVariable("id") String id) {
        return ResponseEntity.ok(this.profileService.activate(UUID.fromString(id), UUID.fromString(CREDENTIAL)));
    }

    @PutMapping("/api/profiles/{id}/deactivate")
    public ResponseEntity<ProfileResponse> deactivate(@PathVariable("id") String id) {
        return ResponseEntity.ok(this.profileService.deactivate(UUID.fromString(id), UUID.fromString(CREDENTIAL)));
    }
}

@Service
@Transactional
class ProfileService {

    private final ProfileRepository profileRepository;
    private final ApplicationEventPublisher publisher;

    public ProfileService(ProfileRepository profileRepository, ApplicationEventPublisher publisher) {
        this.profileRepository = profileRepository;
        this.publisher = publisher;
    }

    ProfileResponse add(AddProfileRequest addProfileRequest, UUID credential) {
        if (profileRepository.existsByCredential(credential)) {
            throw new ProfileAlreadyExistsException("Profile with user " + credential + " already exists");
        }

        Profile profile = Profile.createNewProfile(addProfileRequest, credential);
        Profile storedProfile = profileRepository.save(profile);
        publisher.publishEvent(new AddProfileEvent(storedProfile.id()));
        return ProfileResponse.from(storedProfile, true);
    }

    ProfileResponse activate(UUID profileId, UUID credential) {
        Profile exisitngProfile = profileRepository.findByIdAndCredential(profileId, credential)
				.orElseThrow(() -> new ProfileNotFoundException("Profile with id " + profileId + " not found"));

        if (!exisitngProfile.profileStatus().status.trim().equals("ACTIVE")) {
            Profile updatingProfile = Profile.withProfileStatusActivated(exisitngProfile, credential);
            Profile storedProfile = profileRepository.save(updatingProfile);
            publisher.publishEvent(new UpdateProfileEvent(storedProfile.id()));
            return ProfileResponse.from(storedProfile, true);
        }

        throw new ProfileAlreadyExistsException("Profile with name " + exisitngProfile.name() + " is already enabled");
    }

    ProfileResponse deactivate(UUID profileId, UUID credential) {
        Profile exisitngProfile = profileRepository.findByIdAndCredential(profileId, credential)
				.orElseThrow(() -> new ProfileNotFoundException("Profile with id " + profileId + " not found"));

        if (!exisitngProfile.profileStatus().status.trim().equals("INACTIVE")) {
            Profile updatingProfile = Profile.withProfileStatusDeactivated(exisitngProfile, credential);
            Profile storedProfile = profileRepository.save(updatingProfile);
            publisher.publishEvent(new UpdateProfileEvent(storedProfile.id()));
            return ProfileResponse.from(storedProfile, true);
        }

        throw new ProfileAlreadyDisabledException("Profile with name " + exisitngProfile.name() + " is already disabled");
    }

    ProfileResponse update(UpdateProfileRequest updateProfileRequest, UUID profileId, UUID credential) {
        Profile exisitngProfile = profileRepository.findByIdAndCredential(profileId, credential)
				.orElseThrow(() -> new ProfileNotFoundException("Profile with id " + profileId + " not found"));

        Profile updatingProfile = new Profile(
				exisitngProfile.id(),
				exisitngProfile.version(),
				updateProfileRequest.name(),
				updateProfileRequest.about(),
				ProfileType.valueOf(updateProfileRequest.profileType().trim().toUpperCase()),
				exisitngProfile.profileStatus(),
				exisitngProfile.activationDate(),
				exisitngProfile.credential());

        Profile storedProfile = profileRepository.save(updatingProfile);
        publisher.publishEvent(new UpdateProfileEvent(storedProfile.id()));
        return ProfileResponse.from(storedProfile, true);
    }

    ProfileResponse findByName(String name) {
        Profile profile = profileRepository.findByName(name)
				.orElseThrow(() -> new ProfileNotFoundException("Profile with name " + name + " not found"));

        return ProfileResponse.from(profile, true);
    }

    ProfileResponse findById(UUID id) {
        Profile profile = profileRepository.findById(id)
				.orElseThrow(() -> new ProfileNotFoundException("Profile with id " + id + " not found"));

        return ProfileResponse.from(profile, true);
    }

    ProfileResponse findByCredential(UUID credential) {
        Profile profile = profileRepository.findByCredential(credential)
				.orElseThrow(() -> new ProfileNotFoundException("Profile with  " + credential + " not found"));

        return ProfileResponse.from(profile, true);
    }

}

@Repository
interface ProfileRepository extends CrudRepository<Profile, Integer> {

    Optional<Profile> findByIdAndCredential(UUID profileId, UUID credential);

    Optional<Profile> findByCredential(UUID credential);

    Optional<Profile> findByName(String name);

    Optional<Profile> findById(UUID id);

    boolean existsByCredential(UUID credential);

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

    public static Profile createNewProfile(AddProfileRequest addProfileRequest, UUID credential) {
        return new Profile(
				UUID.randomUUID(),
				null, addProfileRequest.name(),
				addProfileRequest.about(),
				ProfileType.valueOf(addProfileRequest.profileType().trim().toUpperCase()),
				ProfileStatus.INACTIVE,
				null,
				credential);
    }

    public static Profile withProfileStatusActivated(Profile exisitngProfile, UUID credential) {
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

    public static Profile withProfileStatusDeactivated(Profile exisitngProfile, UUID credential) {
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

enum ProfileStatus {
    ACTIVE("ACTIVE"), INACTIVE("INACTIVE");

    final String status;

    ProfileStatus(String status) {
        this.status = status;
    }
}


enum ProfileType {

    INDIVIDUAL("INDIVIDUAL"), COMPANY("COMPANY"), STORE("STORE");

    final String type;

    ProfileType(String type) {
        this.type = type;
    }

}

record AddProfileRequest(@NotNull String name,
						 @NotBlank String about,
						 @NotNull String profileType) {
}

record UpdateProfileRequest(@NotNull String name,
							@NotBlank String about,
							@NotNull String profileType) {
}

record ProfileResponse(String id,
					   String name,
					   String about,
					   String profileType,
					   String profileStatus,
                       LocalDateTime activationDate,
					   String credential) {

    public static ProfileResponse from(Profile profile, boolean includeSensitive) {
        return new ProfileResponse(
				includeSensitive ? profile.id().toString() : null,
				profile.name(), profile.about(),
				profile.profileType().toString(),
				includeSensitive ? profile.profileStatus().toString() : null,
				includeSensitive ? profile.activationDate() : null,
				includeSensitive ? profile.credential().toString() : null);
    }

}

class ProfileAlreadyExistsException extends RuntimeException {
    public ProfileAlreadyExistsException(String message) {
        super(message);
    }
}

class ProfileNotFoundException extends RuntimeException {
    ProfileNotFoundException(String message) {
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

// controller // service // repository // model // enum // dto // exception

@Configuration
class RabbitMqProfilesIntegrationConfig {

    static final String PROFILE_Q = "profiles";

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


