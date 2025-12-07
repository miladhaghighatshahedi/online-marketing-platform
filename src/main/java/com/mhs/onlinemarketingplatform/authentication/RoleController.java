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
import com.mhs.onlinemarketingplatform.authentication.api.RoleServiceInternal;
import com.mhs.onlinemarketingplatform.authentication.error.AuthenticationErrorCode;
import com.mhs.onlinemarketingplatform.authentication.error.permission.PermissionNotFoundException;
import com.mhs.onlinemarketingplatform.authentication.error.role.RoleAlreadyExistsException;
import com.mhs.onlinemarketingplatform.authentication.error.role.RoleNotFoundException;
import com.mhs.onlinemarketingplatform.authentication.model.Permission;
import com.mhs.onlinemarketingplatform.authentication.model.Role;
import com.mhs.onlinemarketingplatform.authentication.model.RolePermissions;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
/**
 * @author Milad Haghighat Shahedi
 */
@Controller
@ResponseBody
class RollController {

	private final RoleService roleService;

	public RollController(RoleService roleService) {
		this.roleService = roleService;
	}

	@PostMapping("/api/admin/roles/add")
	ResponseEntity<RoleApiResponse<RoleResponse>> add(@RequestBody AddRoleRequest request) {
		RoleResponse response = this.roleService.add(request);
		return ResponseEntity.ok(new RoleApiResponse<>(true,"Role added successfully!",response));
	}

	@PutMapping("/api/admin/roles/update")
	ResponseEntity<RoleApiResponse<RoleResponse>> add(@RequestBody UpdateRoleRequest request) {
		RoleResponse response = this.roleService.update(request);
		return ResponseEntity.ok(new RoleApiResponse<>(true,"Role updated successfully!",response));
	}

	@PutMapping("/api/admin/roles/{id}/permissions")
	ResponseEntity<RoleApiResponse<RoleResponse>> addPermissionsToARole(@RequestBody AddPermissionsToARoleRequest request, @PathVariable("id") UUID id) {
		if (!id.equals(request.roleId())) {
			throw new IllegalArgumentException("Role ID mismatch between path and body.");
		}
		RoleResponse response = this.roleService.addPermissionsToARole(request);
		return ResponseEntity.ok(new RoleApiResponse<>(true,"Permissions added to the role successfully!",response));
	}

	@DeleteMapping("/api/admin/roles/{id}/permissions")
	ResponseEntity<RoleApiResponse<RoleResponse>> removePermissionsFromARole(@RequestBody RemovePermissionsFromARoleRequest request, @PathVariable UUID id) {
		if (!id.equals(request.roleId())) {
			throw new IllegalArgumentException("Role ID mismatch between path and body.");
		}
		RoleResponse response = this.roleService.removePermissionsFromARole(request);
		return ResponseEntity.ok(new RoleApiResponse<>(true,"Permissions removed from the role successfully!",response));
	}

	@DeleteMapping("/api/admin/roles/{id}")
	ResponseEntity<?> delete(@PathVariable("id") UUID id) {
		this.roleService.delete(id);
		return ResponseEntity.ok(new RoleApiResponse<>(true,"Role deleted successfully!",null));
	}

	@GetMapping("/api/admin/roles/{id}")
	ResponseEntity<RoleApiResponse<RoleResponse>> fetchById(@PathVariable("id") UUID id) {
		RoleResponse response = this.roleService.fetchById(id);
		return ResponseEntity.ok(new RoleApiResponse<>(true,"Role found successfully!",response));
	}

	@GetMapping(value = "/api/admin/roles",params = "name")
	ResponseEntity<RoleApiResponse<RoleResponse>> fetchByName(@RequestParam("name") String name) {
		RoleResponse response = this.roleService.fetchByName(name);
		return ResponseEntity.ok(new RoleApiResponse<>(true,"Role found successfully!",response));
	}

	@GetMapping("/api/admin/roles")
	ResponseEntity<RoleApiResponse<Set<RoleResponse>>> fetchAllMapToSet() {
		Set<RoleResponse> responses = this.roleService.fetchAllMapToSet();
		return ResponseEntity.ok(new RoleApiResponse<>(true,"Permissions found successfully!",responses));
	}

}

@Service
class RoleService implements RoleServiceInternal {

	private final RoleRepository roleRepository;
	private final RolePermissionsRepository rolePermissionsRepository;
	private final PermissionServiceInternal permissionServiceInternal;
	private final MessageSource messageSource;
	private final RoleMapper mapper;

	RoleService(
			RoleRepository roleRepository,
			RolePermissionsRepository rolePermissionsRepository,
			PermissionServiceInternal permissionServiceInternal,
			MessageSource messageSource,
			RoleMapper mapper) {
		this.roleRepository = roleRepository;
		this.rolePermissionsRepository = rolePermissionsRepository;
		this.permissionServiceInternal = permissionServiceInternal;
		this.messageSource = messageSource;
		this.mapper = mapper;
	}

	@Transactional
	RoleResponse add(AddRoleRequest request) {
		if(existsByName(request.name())) {
			throw new RoleAlreadyExistsException(
					messageSource.getMessage("error.authentication.role.already.exists.with.name",
							new Object[]{request.name()},
							LocaleContextHolder.getLocale()),
					AuthenticationErrorCode.ROLE_ALREADY_EXISTS);
		}

		Set<Permission> foundPermissions = this.permissionServiceInternal.findAllById(request.permissionIds());
        if(foundPermissions.size() != request.permissionIds().size()) {
			throw new PermissionNotFoundException(
			        messageSource.getMessage("error.authentication.permission.not.found.with.ids",
					        new Object[]{request.permissionIds().size()},
					        LocaleContextHolder.getLocale()),
			        AuthenticationErrorCode.PERMISSION_NOT_FOUND);
        }

		Role mappedRole = this.mapper.mapAddRequestToRole(request);
		Role savedRole = this.roleRepository.save(mappedRole);


		for(UUID permissionId : request.permissionIds()) {
			this.rolePermissionsRepository.insert(savedRole.id(),permissionId);
		}

		Set<RolePermissions> rolePermissions = this.rolePermissionsRepository.findByRoleId(savedRole.id());

		return this.mapper.mapRoleToResponse(savedRole,rolePermissions);
	}

	@Transactional
	RoleResponse update(UpdateRoleRequest request) {
		Role existingRole = this.roleRepository.findById(request.id()).orElseThrow(() ->
				new RoleNotFoundException(
						messageSource.getMessage("error.authentication.role.not.found.with.id",
								new Object[]{request.id()},
								LocaleContextHolder.getLocale()),
						AuthenticationErrorCode.ROLE_NOT_FOUND));

		Role mappedToRole = this.mapper.mapUpdateRequestToRole(request, existingRole);
		Role updatedRole = this.roleRepository.save(mappedToRole);

		Set<RolePermissions> rolePermissions = this.rolePermissionsRepository.findByRoleId(existingRole.id());

		return this.mapper.mapRoleToResponse(updatedRole,rolePermissions);
	}

	@Transactional
	RoleResponse addPermissionsToARole(AddPermissionsToARoleRequest request) {
		Role existingRole = this.roleRepository.findById(request.roleId()).orElseThrow(() ->
				new RoleNotFoundException(
						messageSource.getMessage("error.authentication.role.not.found.with.id",
								new Object[]{request.roleId()},
								LocaleContextHolder.getLocale()),
						AuthenticationErrorCode.ROLE_NOT_FOUND));

		Set<Permission> foundPermissionsByRequest = this.permissionServiceInternal.findAllById(request.permissionIds());
		if(foundPermissionsByRequest.size() != request.permissionIds().size()) {
			throw new PermissionNotFoundException(
					messageSource.getMessage("error.authentication.permission.not.found.with.ids",
							new Object[]{request.permissionIds().size()},
							LocaleContextHolder.getLocale()),
					AuthenticationErrorCode.PERMISSION_NOT_FOUND);
		}

		Set<RolePermissions> existingRolePermissions = this.rolePermissionsRepository.findByRoleId(existingRole.id());
		Set<UUID> existingPermissionIds = existingRolePermissions.stream().map(RolePermissions::permissionId).collect(Collectors.toSet());

		Set<UUID> newPermissionIds = new HashSet<>(request.permissionIds());
		newPermissionIds.removeAll(existingPermissionIds);

		for(UUID permissionId : newPermissionIds) {
	       this.rolePermissionsRepository.insert(existingRole.id(),permissionId);
       }

		Set<RolePermissions> updatedRolePermissions = this.rolePermissionsRepository.findByRoleId(request.roleId());

		return this.mapper.mapRoleToResponse(existingRole,updatedRolePermissions);
	}

	@Transactional
	RoleResponse removePermissionsFromARole(RemovePermissionsFromARoleRequest request) {
		Role existingRole = this.roleRepository.findById(request.roleId()).orElseThrow(() ->
				new RoleNotFoundException(
						messageSource.getMessage("error.authentication.role.not.found.with.id",
								new Object[]{request.roleId()},
								LocaleContextHolder.getLocale()),
						AuthenticationErrorCode.ROLE_NOT_FOUND));


		Set<Permission> foundPermissionsByRequest = this.permissionServiceInternal.findAllById(request.permissionIds());
		if(foundPermissionsByRequest.size() != request.permissionIds().size()) {
			throw new PermissionNotFoundException(
					messageSource.getMessage("error.authentication.permission.not.found.with.ids",
							new Object[]{request.permissionIds().size()},
							LocaleContextHolder.getLocale()),
					AuthenticationErrorCode.PERMISSION_NOT_FOUND);
		}

		for(UUID permissionId : request.permissionIds()) {
			this.rolePermissionsRepository.delete(existingRole.id(),permissionId);
		}

		Set<RolePermissions> updatedRolePermissions = this.rolePermissionsRepository.findByRoleId(existingRole.id());

		return this.mapper.mapRoleToResponse(existingRole,updatedRolePermissions);
	}

	@Transactional
	void delete(UUID id) {
		Role foundRole = this.roleRepository.findById(id).orElseThrow(() ->
				new RoleNotFoundException(
						messageSource.getMessage("error.authentication.role.not.found.with.id",
								new Object[]{id},
								LocaleContextHolder.getLocale()),
						AuthenticationErrorCode.ROLE_NOT_FOUND));


		this.rolePermissionsRepository.deleteByRoleId(foundRole.id());
		this.roleRepository.delete(foundRole);

	}

	public Set<Role> findAll() {
		return this.roleRepository.findAll();
	}

	public Set<Role> findAllById(Set<UUID> ids) {
		return this.roleRepository.findAllById(ids);
	}

	Set<RoleResponse> fetchAllMapToSet() {
		Set<Role> roles = this.roleRepository.findAll();
		Set<UUID> roleIds = roles.stream().map(Role::id).collect(Collectors.toSet());

		Set<Permission> permissions = new HashSet<>();

		for(UUID id: roleIds) {
			permissions = findPermissionsByRoleId(id);
		}

		return this.mapper.mapRolesToResponse(roles);
	}

	public Role findById(UUID id) {
		return this.roleRepository.findById(id).orElseThrow(() ->
				new RoleNotFoundException(
						messageSource.getMessage("error.authentication.role.not.found.with.id",
								new Object[]{id},
								LocaleContextHolder.getLocale()),
						AuthenticationErrorCode.ROLE_NOT_FOUND));
	}

	RoleResponse fetchById(UUID id) {
		Role existingRole = this.roleRepository.findById(id).orElseThrow(() ->
				new RoleNotFoundException(
						messageSource.getMessage("error.authentication.role.not.found.with.id",
								new Object[]{id},
								LocaleContextHolder.getLocale()),
						AuthenticationErrorCode.ROLE_NOT_FOUND));

		Set<RolePermissions> foundRolePermissions = this.rolePermissionsRepository.findByRoleId(existingRole.id());

		return this.mapper.mapRoleToResponse(existingRole,foundRolePermissions);
	}

	public Role findByName(String name) {
		return this.roleRepository.findByName(name).orElseThrow(() ->
				new RoleNotFoundException(
						messageSource.getMessage("error.authentication.role.not.found.with.name",
								new Object[]{name},
								LocaleContextHolder.getLocale()),
						AuthenticationErrorCode.ROLE_NOT_FOUND));
	}

	RoleResponse fetchByName(String name) {
		Role existingRole =  this.roleRepository.findByName(name).orElseThrow(() ->
				new RoleNotFoundException(
						messageSource.getMessage("error.authentication.role.not.found.with.name",
								new Object[]{name},
								LocaleContextHolder.getLocale()),
						AuthenticationErrorCode.ROLE_NOT_FOUND));

		Set<RolePermissions> foundRolePermissions = this.rolePermissionsRepository.findByRoleId(existingRole.id());

		return this.mapper.mapRoleToResponse(existingRole,foundRolePermissions);
	}

	boolean existsById(UUID id) {
		return this.roleRepository.existsById(id);
	}

	boolean existsByName(String name) {
		return this.roleRepository.existsByName(name);
	}

	public Set<Permission> findPermissionsByRoleId(UUID roleId) {
		Set<RolePermissions> rolePermissions = this.rolePermissionsRepository.findByRoleId(roleId);
		Set<UUID> permissionIds = rolePermissions.stream().map(RolePermissions::permissionId).collect(Collectors.toSet());
		return this.permissionServiceInternal.findAllById(permissionIds);
	}

}

@Repository
interface RoleRepository extends CrudRepository<Role, UUID> {

	@Query("SELECT * FROM auth_roles ORDER BY created_at")
	Set<Role> findAll();

	@Query("SELECT * FROM auth_roles WHERE id IN (:ids)")
	Set<Role> findAllById(Set<UUID> ids);

	Optional<Role> findById(UUID id);

	Optional<Role> findByName(String name);

	boolean existsById(@Param("id") UUID id);

	boolean existsByName(@Param("name") String name);

}

@Repository
interface RolePermissionsRepository extends CrudRepository<RolePermissions,UUID> {
	@Modifying
	@Query("INSERT INTO auth_role_permissions(role_id, permission_id) VALUES (:roleId,:permissionId)")
	void insert(@Param("roleId") UUID roleId, @Param("permissionId")UUID permissionId);

	@Modifying
	@Query("DELETE FROM auth_role_permissions WHERE  role_id= :roleId AND permission_id= :permissionId")
	void delete(@Param("roleId") UUID roleId, @Param("permissionId")UUID permissionId);

	@Modifying
	@Query("DELETE FROM auth_role_permissions WHERE role_id = :roleId")
	void deleteByRoleId(@Param("roleId") UUID roleId);

	Set<RolePermissions> findByRoleId(@Param("roleId") UUID roleId);
}

record AddRoleRequest(
		@NotBlank String name,
		@NotNull @Size(min = 1) Set<@NotNull UUID> permissionIds) {}

record UpdateRoleRequest(
		@NotNull UUID id,
		@NotBlank String name
) {}

record AddPermissionsToARoleRequest(
		@NotNull UUID roleId,
		@NotNull Set<@NotNull UUID> permissionIds
) {}

record RemovePermissionsFromARoleRequest(
		@NotNull UUID roleId,
		@NotNull Set<@NotNull UUID> permissionIds
) {}

@JsonInclude(JsonInclude.Include.NON_NULL)
record RoleResponse(
	UUID id,
	String name,
	LocalDateTime createdAt,
	LocalDateTime lastUpdatedAt,
	Set<PermissionResponse> permissions
) {}

record RoleApiResponse<T> (
	boolean response,
	String message,
	T data
) {}

record RolePageResponse<T>(
		List<T> content,
		int page,
		int size,
		long totalElements,
		int totalPage) {}

@Mapper(componentModel = "spring",imports = {UuidCreator.class, LocalDateTime.class},uses = PermissionMapperHelper.class)
interface RoleMapper {

	@Mapping(target = "id", expression = "java(UuidCreator.getTimeOrderedEpoch())")
	@Mapping(target = "version", ignore = true)
	@Mapping(target = "createdAt", expression = "java(LocalDateTime.now())")
	@Mapping(target = "lastUpdatedAt", expression = "java(LocalDateTime.now())")
	Role mapAddRequestToRole(AddRoleRequest request);

	@Mapping(target = "id", source = "role.id")
	@Mapping(target = "version", source = "role.version")
	@Mapping(target = "name", source = "request.name")
	@Mapping(target = "createdAt", source = "role.createdAt")
	@Mapping(target = "lastUpdatedAt", expression = "java(LocalDateTime.now())")
	Role mapUpdateRequestToRole(UpdateRoleRequest request,Role role);

    @Mapping(target = "id",source = "role.id")
    @Mapping(target = "name",source = "role.name")
    @Mapping(target = "createdAt", source = "role.createdAt")
    @Mapping(target = "lastUpdatedAt", source = "role.lastUpdatedAt")
    @Mapping(target = "permissions", source = "rolePermissions")
	RoleResponse mapRoleToResponse(Role role,Set<RolePermissions> rolePermissions);

	Set<RoleResponse> mapRolesToResponse(Set<Role> roles);

}

@Component
class PermissionMapperHelper {

	private final PermissionServiceInternal permissionServiceInternal;

	public PermissionMapperHelper(PermissionServiceInternal permissionServiceInternal) {
		this.permissionServiceInternal = permissionServiceInternal;
	}

	Set<PermissionResponse> mapPermissions(Set<RolePermissions> rolePermissions) {
		if (rolePermissions == null || rolePermissions.isEmpty()) {
			return Collections.emptySet();
		}

		Set<UUID> ids = rolePermissions.stream().map(RolePermissions::permissionId).collect(Collectors.toSet());
		Set<Permission> permissions = this.permissionServiceInternal.findAllById(ids);

		return permissions.stream().map(this::mapToPermissionResponse).collect(Collectors.toSet());
	}

	private PermissionResponse mapToPermissionResponse(Permission permission) {
		return new PermissionResponse(
				permission.id(),
				permission.name(),
				permission.createdAt(),
				permission.lastUpdatedAt()
		);
	}

}
