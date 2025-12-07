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
package com.mhs.onlinemarketingplatform.authentication;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.github.f4b6a3.uuid.UuidCreator;
import com.mhs.onlinemarketingplatform.authentication.api.PermissionServiceInternal;
import com.mhs.onlinemarketingplatform.authentication.error.AuthenticationErrorCode;
import com.mhs.onlinemarketingplatform.authentication.error.permission.PermissionAlreadyExistsException;
import com.mhs.onlinemarketingplatform.authentication.error.permission.PermissionNotFoundException;
import com.mhs.onlinemarketingplatform.authentication.model.Permission;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.web.PageableDefault;
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
import java.util.Set;
import java.util.UUID;
/**
 * @author Milad Haghighat Shahedi
 */
@Validated
@Controller
@ResponseBody
class PermissionController {

	private final PermissionService permissionService;

	public PermissionController(PermissionService permissionService) {
		this.permissionService = permissionService;
	}

	@PostMapping("/api/admin/permissions/add")
	ResponseEntity<PermissionApiResponse<PermissionResponse>> add(@RequestBody @Valid AddPermissionRequest request) {
		PermissionResponse response = this.permissionService.add(request);
		return ResponseEntity.ok(new PermissionApiResponse<>(true,"Permission saved successfully!",response));
	}

	@PutMapping("/api/admin/permissions/update")
	ResponseEntity<PermissionApiResponse<PermissionResponse>> update(@Valid @RequestBody UpdatePermissionRequest request) {
		PermissionResponse response = this.permissionService.update(request);
		return ResponseEntity.ok(new PermissionApiResponse<>(true,"Permission updated successfully!",response));
	}

	@DeleteMapping("/api/admin/permissions/{id}")
	ResponseEntity<?> delete(@PathVariable("id") @NotNull UUID id) {
		this.permissionService.delete(id);
		return ResponseEntity.ok(new PermissionApiResponse<>(true,"Permission deleted successfully!",null));
	}

	@GetMapping("/api/admin/permissions/paged")
	PermissionPageResponse<PermissionResponse> findAllPaged(@PageableDefault(size = 6) Pageable pageable) {
		return this.permissionService.findAllPaged(pageable);
	}

	@GetMapping("/api/admin/permissions/{id}")
	ResponseEntity<PermissionApiResponse<PermissionResponse>> fetchById(@PathVariable("id") @NotNull UUID id) {
		PermissionResponse response = this.permissionService.fetchById(id);
		return ResponseEntity.ok(new PermissionApiResponse<>(true,"Permission found successfully!",response));
	}

	@GetMapping(value = "/api/admin/permissions",params = "name")
	ResponseEntity<PermissionApiResponse<PermissionResponse>> fetchByName(@RequestParam("name") @NotBlank String name) {
		PermissionResponse response = this.permissionService.findByName(name);
		return ResponseEntity.ok(new PermissionApiResponse<>(true,"Permission found successfully!",response));
	}

	@GetMapping("/api/admin/permissions")
	ResponseEntity<PermissionApiResponse<Set<PermissionResponse>>> fetchAllMapToSet() {
		Set<PermissionResponse> response = this.permissionService.fetchAllMapToSet();
		return ResponseEntity.ok(new PermissionApiResponse<>(true,"Permissions found successfully!",response));
	}

}

@Service
class PermissionService implements PermissionServiceInternal {

	private final PermissionRepository permissionRepository;
	private final PermissionMapper mapper;
	private final MessageSource messageSource;

	PermissionService(
			PermissionRepository permissionRepository,
			PermissionMapper mapper,
			MessageSource messageSource) {
		this.permissionRepository = permissionRepository;
		this.mapper = mapper;
		this.messageSource = messageSource;
	}

	@Transactional
	public PermissionResponse add(AddPermissionRequest request) {
		if(existsByName(request.name())) {
			throw new PermissionAlreadyExistsException(
					messageSource.getMessage("error.authentication.permission.already.exists.with.name",
							new Object[]{request.name()},
							LocaleContextHolder.getLocale()),
					AuthenticationErrorCode.PERMISSION_ALREADY_EXISTS);
		}
		Permission permission = this.mapper.mapAddRequestToPermission(request);
		Permission savedPermission = this.permissionRepository.save(permission);
		return this.mapper.mapPermissionToResponse(savedPermission);
	}

	@Transactional
	public PermissionResponse update(UpdatePermissionRequest request) {
		Permission existingPermission = this.permissionRepository.findById(request.id()).orElseThrow(() ->
				new PermissionNotFoundException(
						messageSource.getMessage("error.authentication.permission.not.found.with.id",
								new Object[]{request.id()},
								LocaleContextHolder.getLocale()),
						AuthenticationErrorCode.PERMISSION_NOT_FOUND));

		Permission mappedToPermission = this.mapper.mapUpdateRequestToPermission(request, existingPermission);
		Permission updatedPermission = this.permissionRepository.save(mappedToPermission);
		return this.mapper.mapPermissionToResponse(updatedPermission);
	}

	@Transactional
	public void delete(UUID id) {
		Permission foundPermission = this.permissionRepository.findById(id).orElseThrow(() ->
				new PermissionNotFoundException(
						messageSource.getMessage("error.authentication.permission.not.found.with.id",
								new Object[]{id},
								LocaleContextHolder.getLocale()),
						AuthenticationErrorCode.PERMISSION_NOT_FOUND));
		this.permissionRepository.delete(foundPermission);
	}

	Set<Permission> findAll() {
		return this.permissionRepository.findAll();
	}

	@Override
	public Set<Permission> findAllById(Set<UUID> ids) {
		return this.permissionRepository.findAllById(ids);
	}

	Set<PermissionResponse> fetchAllMapToSet() {
		Set<Permission> permissions = this.permissionRepository.findAll();
		return this.mapper.mapPermissionsToResponse(permissions);
	}

	PermissionPageResponse<PermissionResponse> findAllPaged(Pageable pageable) {
		Page<Permission> permissions = this.permissionRepository.findAllByOrderByCreatedAt(pageable);
		return this.mapper.mapPermissionToPageResponse(permissions);
	}

	public Permission findById(UUID id) {
		return this.permissionRepository.findById(id).orElseThrow(() ->
				new PermissionNotFoundException(messageSource.getMessage("error.authentication.permission.not.found.with.id",
						new Object[] {id},
						LocaleContextHolder.getLocale()),
						AuthenticationErrorCode.PERMISSION_NOT_FOUND));
	}

	PermissionResponse fetchById(UUID id) {
		Permission foundPermission = this.permissionRepository.findById(id).orElseThrow(() ->
				 new PermissionNotFoundException(messageSource.getMessage("error.authentication.permission.not.found.with.id",
						 new Object[] {id},
						 LocaleContextHolder.getLocale()),
						 AuthenticationErrorCode.PERMISSION_NOT_FOUND));
		return this.mapper.mapPermissionToResponse(foundPermission);
	}

	PermissionResponse findByName(String name) {
		Permission foundPermission = this.permissionRepository.findByName(name).orElseThrow(() ->
				new PermissionNotFoundException(messageSource.getMessage("error.authentication.permission.not.found.with.name",
						new Object[] {name},
						LocaleContextHolder.getLocale()),
						AuthenticationErrorCode.PERMISSION_NOT_FOUND));
		return this.mapper.mapPermissionToResponse(foundPermission);
	}

	boolean existsById(UUID id) {
		return this.permissionRepository.existsById(id);
	}

	boolean existsByName(String name) {
		return this.permissionRepository.existsByName(name);
	}

}

@Repository
interface PermissionRepository extends CrudRepository<Permission, UUID> {

	@Query("SELECT * FROM auth_permissions ORDER BY created_at")
	Set<Permission> findAll();

	@Query("SELECT * FROM auth_permissions WHERE id IN (:ids)")
	Set<Permission> findAllById(Set<UUID> ids);

	Page<Permission> findAllByOrderByCreatedAt(Pageable pageable);

	Optional<Permission> findById(UUID id);

	Optional<Permission> findByName(String name);

	boolean existsById(@Param("id") UUID id);

	boolean existsByName(@Param("name") String name);

}

record AddPermissionRequest(
		@NotBlank String name) {}

record UpdatePermissionRequest(
		@NotNull UUID id,
		@NotBlank String name
) {}

@JsonInclude(JsonInclude.Include.NON_NULL)
record PermissionResponse(
		UUID id,
		String name,
		LocalDateTime createdAt,
		LocalDateTime lastUpdatedAt) {}

record PermissionApiResponse<T>(
		boolean response,
		String message,
		T data) {}

record PermissionPageResponse<T>(
		List<T> content,
		int page,
		int size,
		long totalElements,
		int totalPage) {}

@Mapper(componentModel = "spring",imports = {UuidCreator.class, LocalDateTime.class})
interface PermissionMapper {

	@Mapping(target = "id", expression = "java(UuidCreator.getTimeOrderedEpoch())")
	@Mapping(target = "version",ignore = true)
	@Mapping(target = "createdAt",expression = "java(LocalDateTime.now())")
	@Mapping(target = "lastUpdatedAt",expression = "java(LocalDateTime.now())")
	Permission mapAddRequestToPermission(AddPermissionRequest request);

	@Mapping(target = "id", source = "permission.id")
	@Mapping(target = "version", source = "permission.version")
	@Mapping(target = "name", source = "request.name")
	@Mapping(target = "createdAt",source = "permission.createdAt")
	@Mapping(target = "lastUpdatedAt",expression = "java(LocalDateTime.now())")
	Permission mapUpdateRequestToPermission(UpdatePermissionRequest request, Permission permission);

	@Mapping(target = "id", source = "permission.id")
	@Mapping(target = "name", source = "permission.name")
	@Mapping(target = "createdAt", source = "permission.createdAt")
	@Mapping(target = "lastUpdatedAt", source = "permission.lastUpdatedAt")
	PermissionResponse mapPermissionToResponse(Permission permission);

	Set<PermissionResponse> mapPermissionsToResponse(Set<Permission> permissions);

	default PermissionPageResponse<PermissionResponse> mapPermissionToPageResponse(Page<Permission> page) {
		return new PermissionPageResponse<>(
				page.getContent().stream().map(this::mapPermissionToResponse).toList(),
				page.getNumber(),
				page.getSize(),
				page.getTotalElements(),
				page.getTotalPages());
	}

}


