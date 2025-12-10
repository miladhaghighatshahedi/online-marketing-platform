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

import com.github.f4b6a3.uuid.UuidCreator;
import com.mhs.onlinemarketingplatform.authentication.api.PermissionServiceInternal;
import com.mhs.onlinemarketingplatform.authentication.error.permission.PermissionNotFoundException;
import com.mhs.onlinemarketingplatform.authentication.error.role.RoleAlreadyExistsException;
import com.mhs.onlinemarketingplatform.authentication.error.role.RoleNotFoundException;
import com.mhs.onlinemarketingplatform.authentication.model.Permission;
import com.mhs.onlinemarketingplatform.authentication.model.Role;
import com.mhs.onlinemarketingplatform.authentication.model.RolePermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
/**
 * @author Milad Haghighat Shahedi
 */
@ExtendWith(MockitoExtension.class)
public class RoleServiceUnitTest {

	@Mock
	private RoleRepository roleRepository;

	@Mock
	private RolePermissionsRepository rolePermissionsRepository;

	@Mock
	private PermissionServiceInternal permissionServiceInternal;

	@Spy
	@InjectMocks
	private RoleService roleService;

	@Mock
	private RoleMapper mapper;

	@Mock
	private  MessageSource messageSource;


	@Test
	void add_method_shouldSaveRole_ReturnResponse() {
		UUID roleId = UuidCreator.getTimeOrderedEpoch();
		Set<UUID> permissionIds = buildPermissionIds(3);
		Set<Permission> permissions = buildPermissions(permissionIds);

		LocalDateTime createdAt = LocalDateTime.of(2025,1,1,12,0,0);
		LocalDateTime lastUpdatedAt = LocalDateTime.of(2025,1,1,12,0,0);

		Role mappedRole = new Role(roleId,0,createdAt,lastUpdatedAt,"USER_ROLE");
		Role savedRole = new Role(roleId,0,createdAt,lastUpdatedAt,"USER_ROLE");

		Set<RolePermissions> savedRolePermissions = buildRolePermissions(savedRole.id(), permissionIds);

		RoleResponse response = buildRoleResponse(savedRole,permissions);
		// Arrange
		AddRoleRequest request = new AddRoleRequest("USER_ROLE",permissionIds);
		doReturn(false).when(this.roleService).existsByName("USER_ROLE");
		when(this.permissionServiceInternal.findAllById(anySet())).thenReturn(permissions);

		when(this.mapper.mapAddRequestToRole(any(AddRoleRequest.class))).thenReturn(mappedRole);
		when(this.roleRepository.save(any())).thenReturn(savedRole);
		when(this.rolePermissionsRepository.findByRoleId(any(UUID.class))).thenReturn(savedRolePermissions);
		when(this.mapper.mapRoleToResponse(any(Role.class),anySet())).thenReturn(response);
		// Act
		RoleResponse result = this.roleService.add(request);
		System.out.println(result);
		//
		assertNotNull(result);
		assertEquals("USER_ROLE",result.name());
		assertEquals(3,result.permissions().size());
		verify(this.roleService,times(1)).existsByName(any());
		verify(this.permissionServiceInternal,times(1)).findAllById(any());
		verify(this.mapper,times(1)).mapAddRequestToRole(any());
		verify(this.roleRepository,times(1)).save(any());
		verify(this.rolePermissionsRepository,times(3)).insert(any(),any());
		verify(this.rolePermissionsRepository,times(1)).findByRoleId(any());
		verify(this.mapper,times(1)).mapRoleToResponse(any(),any());
	}

	@Test
	void add_method_shouldThrowRoleAlreadyExistsException_WhenRoleWithNameAlreadyExists() {
		// Arrange
		Set<UUID> permissionIds = buildPermissionIds(3);
		AddRoleRequest request = new AddRoleRequest("USER_ROLE",permissionIds);
		doReturn(true).when(this.roleService).existsByName("USER_ROLE");
		when(this.messageSource.getMessage(
				eq("error.authentication.role.already.exists.with.name"),
				eq(new Object[]{request.name()}),
				any(Locale.class)
		)).thenReturn("Role with the name USER_ROLE already exists.");
		// Act
		RoleAlreadyExistsException exception = assertThrows(
				RoleAlreadyExistsException.class,
				() -> this.roleService.add(request));
		// Assert
		assertEquals("Role with the name USER_ROLE already exists.",exception.getMessage());
		verify(this.mapper,never()).mapAddRequestToRole(any());
		verify(this.permissionServiceInternal,never()).findAllById(any());
		verify(mapper,never()).mapAddRequestToRole(any());
		verify(this.roleRepository,never()).save(any());
		verify(this.rolePermissionsRepository,never()).insert(any(),any());
		verify(mapper,never()).mapRoleToResponse(any(),any());
	}

	@Test
	void add_method_shouldThrowPermissionNotFoundException_WhenNumberOfRequestAndExsitingRolesNotEqual() {
		Set<UUID> sentPermissionIdsWithRequest = buildPermissionIds(3);

		Set<UUID> permissionIds_2 = buildPermissionIds(1);
		Set<Permission> actualAvailablePermissionsInDB = buildPermissions(permissionIds_2);
		// Arrange
		AddRoleRequest request = new AddRoleRequest("USER_ROLE",sentPermissionIdsWithRequest);
		doReturn(false).when(this.roleService).existsByName("USER_ROLE");
		when(this.permissionServiceInternal.findAllById(any())).thenReturn(actualAvailablePermissionsInDB);
		when(this.messageSource.getMessage(
				eq("error.authentication.permission.not.found.with.ids"),
				eq(new Object[]{request.permissionIds().size()}),
				any(Locale.class)
		)).thenReturn("Permission with the ids length of 3 not found.");
		// Act
		PermissionNotFoundException exception = assertThrows(
				PermissionNotFoundException.class,
				() -> this.roleService.add(request));
		// Assert
		assertEquals("Permission with the ids length of 3 not found.",exception.getMessage());
		verify(this.roleService,times(1)).existsByName(any());
		verify(this.permissionServiceInternal,times(1)).findAllById(any());
		verify(this.mapper,never()).mapAddRequestToRole(any());
		verify(mapper,never()).mapAddRequestToRole(any());
		verify(this.roleRepository,never()).save(any());
		verify(this.rolePermissionsRepository,never()).insert(any(),any());
		verify(mapper,never()).mapRoleToResponse(any(),any());
	}

	@Test
	void update_method_shouldSaveRole_ReturnResponse() {
		UUID roleId = UuidCreator.getTimeOrderedEpoch();
		LocalDateTime createdAt = LocalDateTime.of(2025,1,1,12,0,0);
		LocalDateTime lastUpdatedAt_old = LocalDateTime.of(2025,1,1,12,0,0);
		LocalDateTime lastUpdatedAt_new = LocalDateTime.of(2025,1,1,12,0,0);

		Role existingRole = new Role(roleId, 0, createdAt, lastUpdatedAt_old, "USER_ROLE");
		Role mappedRole = new Role(roleId, 0, createdAt, lastUpdatedAt_new, "USER_ROLE_UPDATED");
		Role updatedRole = new Role(roleId, 1, createdAt, lastUpdatedAt_new, "USER_ROLE_UPDATED");

		Set<UUID> permissionIds = buildPermissionIds(3);
		Set<Permission> permissions = buildPermissions(permissionIds);
		Set<RolePermissions> rolePermissions = buildRolePermissions(updatedRole.id(), permissionIds);
		RoleResponse response = buildRoleResponse(updatedRole, permissions);

		// Arrange
		UpdateRoleRequest request = new UpdateRoleRequest(roleId,"USER_ROLE_UPDATED");
		when(this.roleRepository.findById(any())).thenReturn(Optional.of(existingRole));
		when(this.mapper.mapUpdateRequestToRole(any(),any())).thenReturn(mappedRole);
		when(this.roleRepository.save(any())).thenReturn(updatedRole);
		when(this.rolePermissionsRepository.findByRoleId(any())).thenReturn(rolePermissions);
		when(this.mapper.mapRoleToResponse(any(),anySet())).thenReturn(response);
		// Act
		RoleResponse result = this.roleService.update(request);
		// Assert
		assertNotNull(result);
		assertNotEquals("USER_ROLE",result.name());
		assertEquals("USER_ROLE_UPDATED",result.name());
		assertEquals(lastUpdatedAt_new,result.lastUpdatedAt());
		assertEquals(3,result.permissions().size());
		verify(this.roleRepository,times(1)).findById(any());
		verify(this.mapper,times(1)).mapUpdateRequestToRole(any(),any());
		verify(this.roleRepository,times(1)).save(any());
		verify(this.rolePermissionsRepository,times(1)).findByRoleId(any());
		verify(this.mapper,times(1)).mapRoleToResponse(any(),any());
	}

	@Test
	void update_method_shouldThrowRoleNotFoundException_WhenRoleByIdDoesNotExist() {
		UUID roleId = UuidCreator.getTimeOrderedEpoch();
		// Arrange
		UpdateRoleRequest request = new UpdateRoleRequest(roleId,"USER_ROLE_UPDATED");
		when(this.roleRepository.findById(any())).thenReturn(Optional.empty());
		when(this.messageSource.getMessage(
				eq("error.authentication.role.not.found.with.id"),
				eq(new Object[]{request.id()}),
			any(Locale.class))
		).thenReturn("Role with the id "+request.id()+" not found.");
		// ACT
		RoleNotFoundException exception = assertThrows(RoleNotFoundException.class,
				() -> this.roleService.update(request));
		// Assert
		assertEquals("Role with the id "+request.id()+" not found.",exception.getMessage());
		verify(this.roleRepository,times(1)).findById(any());
		verify(this.mapper,never()).mapUpdateRequestToRole(any(),any());
		verify(this.roleRepository,never()).save(any());
		verify(this.rolePermissionsRepository,never()).findByRoleId(any());
		verify(this.mapper,never()).mapRoleToResponse(any(),any());
	}

    @Test
	void addPermissionsToARole_method_shouldReturnResponse() {
	    UUID roleId = UuidCreator.getTimeOrderedEpoch();

		UUID id_1 = UUID.fromString("019afe7f-0f4a-71eb-98ab-e653ae61b76d");
	    UUID id_2 = UUID.fromString("019afe7f-0f4a-71eb-98ac-4573f991db0b");
	    UUID id_3 = UUID.fromString("019afe7f-0f4a-71eb-98ad-5136149f8da8");

	    UUID id_4 = UUID.fromString("019afe7f-0f4a-71eb-98ab-e653ae61c76d");
	    UUID id_5 = UUID.fromString("019afe7f-0f4a-71eb-98ac-4573f991ab0b");
	    UUID id_6 = UUID.fromString("019afe7f-0f4a-71eb-98ad-513614938da8");

		Set<UUID> requestPermissionIds = Set.of(id_1,id_2,id_3);
	    Set<Permission> foundPermissions = buildPermissions(requestPermissionIds);
	    Set<RolePermissions> newRolePermissions = buildRolePermissions(roleId, requestPermissionIds);

	    Set<UUID> oldPermissionIds = Set.of(id_4,id_5,id_6);
	    Set<Permission> foundPermissionsInTheDB = buildPermissions(oldPermissionIds);
	    Set<RolePermissions> existingRolePermissions = buildRolePermissions(roleId, oldPermissionIds);

	    Set<RolePermissions> allRolePermissionsForTheRequestedRole = Stream.concat(
			    existingRolePermissions.stream(),
			    newRolePermissions.stream()).collect(Collectors.toSet());

	    Set<Permission> allPermissions = Stream.concat(
			    foundPermissions.stream(),
			    foundPermissionsInTheDB.stream()).collect(Collectors.toSet());

	    LocalDateTime createdAt = LocalDateTime.of(2025,1,1,12,0,0);
	    LocalDateTime lastUpdatedAt = LocalDateTime.of(2025,1,1,12,0,0);
	    Role existingRole = new Role(roleId,0,createdAt,lastUpdatedAt,"ROLE_USER");

	    RoleResponse response = buildRoleResponse(existingRole, allPermissions);

	    // Arrange
	    AddPermissionsToARoleRequest request = new AddPermissionsToARoleRequest(roleId,requestPermissionIds);
		when(this.roleRepository.findById(request.roleId())).thenReturn(Optional.of(existingRole));
		when(this.permissionServiceInternal.findAllById(request.permissionIds())).thenReturn(foundPermissions);
		when(this.rolePermissionsRepository.findByRoleId(existingRole.id())).thenReturn(existingRolePermissions);
	    when(this.rolePermissionsRepository.findByRoleId(request.roleId())).thenReturn(allRolePermissionsForTheRequestedRole);
		when(this.mapper.mapRoleToResponse(any(),any())).thenReturn(response);

		int counter = 0 ;
	    for(UUID permissionId : requestPermissionIds) {
		    if(!oldPermissionIds.contains(permissionId)) {
				counter++;
			    this.rolePermissionsRepository.insert(roleId,permissionId);
		    }
	    }

		// Act
	    RoleResponse result = this.roleService.addPermissionsToARole(request);
		// Assert
	    assertNotNull(result);
	    assertEquals("ROLE_USER",result.name());
		assertEquals(6,result.permissions().size());

	    verify(this.roleRepository,times(1)).findById(any());
	    verify(this.permissionServiceInternal,times(1)).findAllById(any());
		verify(this.rolePermissionsRepository,times(2)).findByRoleId(any());
	    verify(this.rolePermissionsRepository,times(counter)).insert(any(),any());
	    verify(this.mapper,times(1)).mapRoleToResponse(any(),any());

    }

	@Test
	void addPermissionsToARole_method_shouldThrowRoleNotFoundException_WhenRoleByIdDoesNotExist(){
		UUID roleId = UuidCreator.getTimeOrderedEpoch();
		UUID id_1 = UUID.fromString("019afe7f-0f4a-71eb-98ab-e653ae61b76d");
		UUID id_2 = UUID.fromString("019afe7f-0f4a-71eb-98ac-4573f991db0b");
		UUID id_3 = UUID.fromString("019afe7f-0f4a-71eb-98ad-5136149f8da8");

		Set<UUID> requestPermissionIds = Set.of(id_1,id_2,id_3);
		// Arrange
		AddPermissionsToARoleRequest request = new AddPermissionsToARoleRequest(roleId,requestPermissionIds);
		when(this.roleRepository.findById(any())).thenReturn(Optional.empty());
		when(this.messageSource.getMessage(
				eq("error.authentication.role.not.found.with.id"),
				eq(new Object[]{request.roleId()}),
				any(Locale.class))
		).thenReturn("Role with the id "+request.roleId()+" not found.");
		// ACT
		RoleNotFoundException exception = assertThrows(RoleNotFoundException.class,
				() -> this.roleService.addPermissionsToARole(request));
		// Assert
		assertEquals("Role with the id "+request.roleId()+" not found.",exception.getMessage());
		verify(this.roleRepository,times(1)).findById(any());
		verify(this.roleRepository,never()).findAllById(any());
		verify(this.rolePermissionsRepository,never()).insert(any(),any());
		verify(this.mapper,never()).mapRoleToResponse(any(),any());
	}

	@Test
	void addPermissionsToARole_method_shouldThrowPermissionNotFoundException_WhenNumberOfRequestAndExsitingRolesNotEqual() {
		UUID roleId = UuidCreator.getTimeOrderedEpoch();
		UUID id_1 = UUID.fromString("019afe7f-0f4a-71eb-98ab-e653ae61b76d");
		UUID id_2 = UUID.fromString("019afe7f-0f4a-71eb-98ac-4573f991db0b");
		UUID id_3 = UUID.fromString("019afe7f-0f4a-71eb-98ad-5136149f8da8");

		LocalDateTime createdAt = LocalDateTime.of(2025,1,1,12,0,0);
		LocalDateTime lastUpdatedAt = LocalDateTime.of(2025,1,1,12,0,0);
		Role existingRole = new Role(roleId,0,createdAt,lastUpdatedAt,"ROLE_USER");

		Set<UUID> requestPermissionIds = Set.of(id_1,id_2,id_3);
		Set<UUID> mockSmallerList = Set.of(id_1,id_2);
		Set<Permission> foundPermissions = buildPermissions(mockSmallerList);
		// Arrange
		AddPermissionsToARoleRequest request = new AddPermissionsToARoleRequest(roleId,requestPermissionIds);
		when(this.roleRepository.findById(request.roleId())).thenReturn(Optional.of(existingRole));
		when(this.permissionServiceInternal.findAllById(request.permissionIds())).thenReturn(foundPermissions);
		when(this.messageSource.getMessage(
				eq("error.authentication.permission.not.found.with.ids"),
				eq(new Object[]{request.permissionIds().size()}),
				any(Locale.class)
		)).thenReturn("Permission with the ids length of 3 not found.");
		// Act
		PermissionNotFoundException exception = assertThrows(
				PermissionNotFoundException.class,
				() -> this.roleService.addPermissionsToARole(request));
		// Assert
		verify(this.roleRepository,times(1)).findById(any());
		verify(this.permissionServiceInternal,times(1)).findAllById(request.permissionIds());
		verify(this.rolePermissionsRepository,never()).findByRoleId(existingRole.id());
		verify(this.rolePermissionsRepository,never()).insert(any(),any());
		verify(this.rolePermissionsRepository,never()).findByRoleId(request.roleId());
		verify(mapper,never()).mapRoleToResponse(any(),any());
	}

    @Test
    void removePermissionsFromARole_method_shouldReturnResponse() {
	    UUID id_1 = UUID.fromString("019afe7f-0f4a-71eb-98ab-e653ae61b76d");
	    UUID id_2 = UUID.fromString("019afe7f-0f4a-71eb-98ac-4573f991db0b");
	    UUID id_3 = UUID.fromString("019afe7f-0f4a-71eb-98ad-5136149f8da8");

	    UUID id_4 = UUID.fromString("019afe7f-0f4a-71eb-98ab-e653ae61c76d");
	    UUID id_5 = UUID.fromString("019afe7f-0f4a-71eb-98ac-4573f991ab0b");
	    UUID id_6 = UUID.fromString("019afe7f-0f4a-71eb-98ad-513614938da8");

		UUID id = UuidCreator.getTimeOrderedEpoch();
		LocalDateTime createAt = LocalDateTime.of(2025,1,1,12,0,0);
	    LocalDateTime lastUpdatedAt = LocalDateTime.of(2025,1,1,12,1,0);
		Role mockedRole = new Role(id,0,createAt,lastUpdatedAt,"ROLE_USER");

	    Set<UUID> permissionIds = Set.of(id_1,id_2,id_3);
	    RemovePermissionsFromARoleRequest request = new RemovePermissionsFromARoleRequest(id,permissionIds);
	    Set<Permission> mockedExistingPermissionInDBByRequest = buildPermissions(request.permissionIds());

	    Set<UUID> existingPermissionIds = Set.of(id_4,id_5,id_6);
		Set<RolePermissions> mockedUpdateRolePermissions = buildRolePermissions(id,existingPermissionIds);

	    Set<Permission> mockRolePermissionsAfterUpdate = buildPermissions(existingPermissionIds);
	    RoleResponse mockedRoleResponse = buildRoleResponse(mockedRole,mockRolePermissionsAfterUpdate);
		// Arrange
	    when(this.roleRepository.findById(id)).thenReturn(Optional.of(mockedRole));
		when(this.permissionServiceInternal.findAllById(request.permissionIds())).thenReturn(mockedExistingPermissionInDBByRequest);

		when(this.rolePermissionsRepository.findByRoleId(id)).thenReturn(mockedUpdateRolePermissions);
	    when(this.mapper.mapRoleToResponse(any(),any())).thenReturn(mockedRoleResponse);

	    // Act
	    RoleResponse result = this.roleService.removePermissionsFromARole(request);
		// Assert
	    assertNotNull(result);
	    assertEquals("ROLE_USER",result.name());
	    assertEquals(3,result.permissions().size());

	    verify(this.roleRepository,times(1)).findById(any());
	    verify(this.permissionServiceInternal,times(1)).findAllById(any());
	    verify(this.rolePermissionsRepository,times(3)).delete(any(),any());
	    verify(this.rolePermissionsRepository,times(1)).findByRoleId(any());
	    verify(this.mapper,times(1)).mapRoleToResponse(any(),any());
    }

	@Test
	void removePermissionsFromARole_method_shouldThrowRoleNotFoundException_WhenRoleByIdDoesNotExist() {
		UUID id = UuidCreator.getTimeOrderedEpoch();
		UUID id_1 = UUID.fromString("019afe7f-0f4a-71eb-98ab-e653ae61b76d");
		UUID id_2 = UUID.fromString("019afe7f-0f4a-71eb-98ac-4573f991db0b");
		UUID id_3 = UUID.fromString("019afe7f-0f4a-71eb-98ad-5136149f8da8");
		Set<UUID> permissionIds = Set.of(id_1,id_2,id_3);
		RemovePermissionsFromARoleRequest request = new RemovePermissionsFromARoleRequest(id,permissionIds);
		// Arrange
		when(this.roleRepository.findById(any())).thenReturn(Optional.empty());
		when(this.messageSource.getMessage(
				eq("error.authentication.role.not.found.with.id"),
				eq(new Object[]{request.roleId()}),
				any(Locale.class))
		).thenReturn("Role with the id "+request.roleId()+" not found.");
		// Act
		RoleNotFoundException exception = assertThrows(RoleNotFoundException.class,
				() -> this.roleService.removePermissionsFromARole(request));
		// Assert
		assertEquals("Role with the id "+request.roleId()+" not found.",exception.getMessage());
		verify(this.roleRepository,times(1)).findById(any());
		verify(this.roleRepository,never()).findAllById(any());
		verify(this.rolePermissionsRepository,never()).delete(any(),any());
		verify(this.rolePermissionsRepository,never()).findByRoleId(any());
		verify(this.mapper,never()).mapRoleToResponse(any(),any());
	}

	@Test
	void removePermissionsFromARole_method_shouldThrowPermissionNotFoundException_WhenNumberOfRequestAndExsitingRolesNotEqual() {
		UUID id = UuidCreator.getTimeOrderedEpoch();
		UUID id_1 = UUID.fromString("019afe7f-0f4a-71eb-98ab-e653ae61b76d");
		UUID id_2 = UUID.fromString("019afe7f-0f4a-71eb-98ac-4573f991db0b");
		UUID id_3 = UUID.fromString("019afe7f-0f4a-71eb-98ad-5136149f8da8");
		Set<UUID> permissionIds = Set.of(id_1,id_2,id_3);
		LocalDateTime createdAt = LocalDateTime.of(2025,1,1,12,0,0);
		LocalDateTime lastUpdatedAt = LocalDateTime.of(2025,1,1,12,0,0);
		Role existingRole = new Role(id,0,createdAt,lastUpdatedAt,"ROLE_USER");

		Set<UUID> requestPermissionIds = Set.of(id_1,id_2,id_3);
		Set<UUID> mockSmallerList = Set.of(id_1,id_2);
		Set<Permission> foundPermissions = buildPermissions(mockSmallerList);

		// Arrange
		RemovePermissionsFromARoleRequest request = new RemovePermissionsFromARoleRequest(id,requestPermissionIds);
		when(this.roleRepository.findById(request.roleId())).thenReturn(Optional.of(existingRole));
		when(this.permissionServiceInternal.findAllById(request.permissionIds())).thenReturn(foundPermissions);
		// Act
		when(this.messageSource.getMessage(
				eq("error.authentication.permission.not.found.with.ids"),
				eq(new Object[]{request.permissionIds().size()}),
				any(Locale.class)
		)).thenReturn("Permission with the ids length of 3 not found.");
		// Act
		PermissionNotFoundException exception = assertThrows(
				PermissionNotFoundException.class,
				() -> this.roleService.removePermissionsFromARole(request));
		// Assert
		verify(this.roleRepository,times(1)).findById(any());
		verify(this.permissionServiceInternal,times(1)).findAllById(request.permissionIds());
		verify(this.rolePermissionsRepository,never()).delete(any(),any());
		verify(this.rolePermissionsRepository,never()).findByRoleId(request.roleId());
		verify(mapper,never()).mapRoleToResponse(any(),any());
	}

	@Test
	void delete_method_shouldDelete() {
		UUID id = UuidCreator.getTimeOrderedEpoch();
		LocalDateTime createAt = LocalDateTime.of(2025,1,1,12,0,0);
		LocalDateTime lastUpdatedAt = LocalDateTime.of(2025,1,1,12,1,0);
		Role mockedRole = new Role(id,0,createAt,lastUpdatedAt,"ROLE_USER");
		// Arrange
		when(this.roleRepository.findById(any())).thenReturn(Optional.of(mockedRole));
		doNothing().when(this.rolePermissionsRepository).deleteByRoleId(id);
		doNothing().when(this.roleRepository).delete(mockedRole);
		// Act
		this.roleService.delete(id);
		// Assert
		verify(this.roleRepository,times(1)).findById(any());
		verify(this.rolePermissionsRepository,times(1)).deleteByRoleId(any());
		verify(this.roleRepository,times(1)).delete(any(Role.class));
	}

	@Test
	void delete_method_shouldThrowRoleNotFoundException_WhenRoleByIdDoesNotExist() {
		UUID id = UuidCreator.getTimeOrderedEpoch();
		// Arrange
		when(this.roleRepository.findById(any())).thenReturn(Optional.empty());
		when(this.messageSource.getMessage(
				eq("error.authentication.role.not.found.with.id"),
				eq(new Object[]{id}),
				any(Locale.class))).thenReturn("Role with the id "+id+" not found.");
		// Act
		RoleNotFoundException exception = assertThrows(
				RoleNotFoundException.class,
				() -> this.roleService.delete(id));
		// Assert
		assertEquals("Role with the id "+id+" not found.",exception.getMessage());
		verify(this.roleRepository,times(1)).findById(any());
		verify(this.rolePermissionsRepository,never()).deleteByRoleId(any());
		verify(this.rolePermissionsRepository,never()).delete(any());
	}

	@Test
	void findAll_method_shouldRetrunRoles() {
		UUID id1 = UuidCreator.getTimeOrderedEpoch();
		UUID id2 = UuidCreator.getTimeOrderedEpoch();
		UUID id3 = UuidCreator.getTimeOrderedEpoch();
		// Arrange
		Role userRole = new Role(id1,0,LocalDateTime.now(),LocalDateTime.now(),"USER_ROLE");
		Role adminRole = new Role(id1,0,LocalDateTime.now(),LocalDateTime.now(),"ADMIN_ROLE");
		Role managementRole = new Role(id1,0,LocalDateTime.now(),LocalDateTime.now(),"Management_ROLE");

		when(this.roleRepository.findAll()).thenReturn(Set.of(userRole,adminRole,managementRole));
		// Act
		Set<Role> roles = this.roleService.findAll();
		// Assert
		assertEquals(3,roles.size());
	}

	@Test
	void findAllByIds_method_shouldRetrunRoles() {
		UUID id1 = UuidCreator.getTimeOrderedEpoch();
		UUID id2 = UuidCreator.getTimeOrderedEpoch();
		UUID id3 = UuidCreator.getTimeOrderedEpoch();
		Set<UUID> ids = Set.of(id1,id2,id3);
		// Arrange
		Role userRole = new Role(id1,0,LocalDateTime.now(),LocalDateTime.now(),"USER_ROLE");
		Role adminRole = new Role(id1,0,LocalDateTime.now(),LocalDateTime.now(),"ADMIN_ROLE");
		Role managementRole = new Role(id1,0,LocalDateTime.now(),LocalDateTime.now(),"Management_ROLE");
		Set<Role> mockedRoles = Set.of(userRole,adminRole,managementRole);
		when(this.roleRepository.findAllById(ids)).thenReturn(mockedRoles);
		// Act
		Set<Role> roles = this.roleService.findAllById(ids);
		// Assert
		assertEquals(3,roles.size());
	}

	@Test
	void findAllByIds_method_shouldNotThrowException() {
		UUID id1 = UuidCreator.getTimeOrderedEpoch();
		UUID id2 = UuidCreator.getTimeOrderedEpoch();
		UUID id3 = UuidCreator.getTimeOrderedEpoch();
		Set<UUID> ids = Set.of(id1,id2,id3);
		// Arrange
		Role userRole = new Role(id1,0,LocalDateTime.now(),LocalDateTime.now(),"USER_ROLE");
		Role adminRole = new Role(id1,0,LocalDateTime.now(),LocalDateTime.now(),"ADMIN_ROLE");
		Set<Role> mockedRoles = Set.of(userRole,adminRole);
		when(this.roleRepository.findAllById(ids)).thenReturn(mockedRoles);
		// Act
		Set<Role> roles = this.roleService.findAllById(ids);
		// Assert
		assertEquals(2,roles.size());
	}

	@Test
	void fetchAllMapToSet_method_shouldReturnSetOfRoleResponse() {
		UUID roleId1 = UuidCreator.getTimeOrderedEpoch();
		UUID roleId2 = UuidCreator.getTimeOrderedEpoch();
		UUID roleId3 = UuidCreator.getTimeOrderedEpoch();

		UUID permissionId1 = UuidCreator.getTimeOrderedEpoch();
		UUID permissionId2 = UuidCreator.getTimeOrderedEpoch();
		UUID permissionId3 = UuidCreator.getTimeOrderedEpoch();
		Set<UUID> permissionIds = Set.of(permissionId1, permissionId2, permissionId3);

		Set<RolePermissions> rolePermissions1 = buildRolePermissions(roleId1, permissionIds);
		Set<RolePermissions> rolePermissions2 = buildRolePermissions(roleId2, permissionIds);
		Set<RolePermissions> rolePermissions3 = buildRolePermissions(roleId3, permissionIds);

		Role userRole = new Role(roleId1,0,LocalDateTime.now(),LocalDateTime.now(),"USER_ROLE");
		Role adminRole = new Role(roleId2,0,LocalDateTime.now(),LocalDateTime.now(),"ADMIN_ROLE");
		Role managementRole = new Role(roleId3,0,LocalDateTime.now(),LocalDateTime.now(),"Management_ROLE");

		Set<Role> mockedRoles = Set.of(userRole, adminRole, managementRole);

		// Arrange
		when(this.roleRepository.findAll()).thenReturn(mockedRoles);
		// Act
		Set<RoleResponse> responses = this.roleService.fetchAllMapToSet();
		// Assert
		verify(this.roleRepository,times(1)).findAll();
		System.out.println(responses);
		verify(this.rolePermissionsRepository, times(3)).findByRoleId(any());
		verify(this.mapper, times(3)).mapRoleToResponse(any(),any());
	}

	@Test
	void findById_method_returnRole() {
		UUID id = UuidCreator.getTimeOrderedEpoch();
		Role mockedRole = new Role(id,0,LocalDateTime.now(),LocalDateTime.now(),"ROLE_USER");
		// Arrange
		when(this.roleRepository.findById(any())).thenReturn(Optional.of(mockedRole));
		// Act
		Role result = this.roleService.findById(any());
		// Assert
		assertNotNull(result);
		assertEquals("ROLE_USER",result.name());
		verify(this.roleRepository,times(1)).findById(any());
	}

	@Test
	void findById_method_shouldThrowRoleNotFoundException_WhenRoleByIdDoesNotExist() {
		UUID id = UuidCreator.getTimeOrderedEpoch();
		// Arrange
		when(this.roleRepository.findById(any())).thenReturn(Optional.empty());
		when(this.messageSource.getMessage(
				eq("error.authentication.role.not.found.with.id"),
				eq(new Object[]{id}),
				any(Locale.class))).thenReturn("Role with the id "+id+" not found.");
		// Act
		RoleNotFoundException exception = assertThrows(
				RoleNotFoundException.class,
				() -> this.roleService.delete(id));
		// Assert
		assertEquals("Role with the id "+id+" not found.",exception.getMessage());
		verify(this.roleRepository,times(1)).findById(any());
	}

	@Test
	void fetchById_method_returnRole() {
		UUID id = UuidCreator.getTimeOrderedEpoch();
		LocalDateTime createdAt = LocalDateTime.of(2025,1,1,12,0,0);
		LocalDateTime lastUpdatedAt = LocalDateTime.of(2025,1,1,12,0,0);

		Role mockedRole = new Role(id,0,LocalDateTime.now(),LocalDateTime.now(),"ROLE_USER");
		Set<UUID> permissionsIds = buildPermissionIds(3);
		Set<RolePermissions> mockedRolePermissions = buildRolePermissions(mockedRole.id(),permissionsIds);

		PermissionResponse permissionResponse1 = new PermissionResponse(id,"x",LocalDateTime.now(),LocalDateTime.now());
		PermissionResponse permissionResponse2 = new PermissionResponse(id,"y",LocalDateTime.now(),LocalDateTime.now());
		PermissionResponse permissionResponse3 = new PermissionResponse(id,"z",LocalDateTime.now(),LocalDateTime.now());
		Set<PermissionResponse> prs = Set.of(permissionResponse1,permissionResponse2,permissionResponse3);

		RoleResponse mockcedRoleResponse = new RoleResponse(id,"ROLE_USER",createdAt,lastUpdatedAt,prs);
		// Arrange
		when(this.roleRepository.findById(any())).thenReturn(Optional.of(mockedRole));
		when(this.rolePermissionsRepository.findByRoleId(mockedRole.id())).thenReturn(mockedRolePermissions);
		when(this.mapper.mapRoleToResponse(any(),any())).thenReturn(mockcedRoleResponse);
		// Act
		RoleResponse result = this.roleService.fetchById(mockedRole.id());
		// Assert
		assertNotNull(result);
		assertEquals("ROLE_USER",result.name());
		assertEquals(3,result.permissions().size());
		verify(this.roleRepository,times(1)).findById(any());
		verify(this.rolePermissionsRepository,times(1)).findByRoleId(any());
		verify(this.mapper,times(1)).mapRoleToResponse(any(),any());
	}



	Role buildANewRole(UUID id,int version, LocalDateTime createAt,LocalDateTime lastUpdatedAt, String name) {
		return new Role(id,version,createAt,lastUpdatedAt,name);
	}

	Set<UUID> buildPermissionIds(int size) {
		Set<UUID> pemissionIds = new HashSet<>();
		for(int i = 1; i <=  size; i++ ) {
			pemissionIds.add(UuidCreator.getTimeOrderedEpoch());
		}
		return pemissionIds;
	}

	Set<Permission> buildPermissions(Set<UUID> permissionIds) {
		Set<Permission> permissions = new HashSet<>();
		for (UUID id : permissionIds) {
			Permission permission = new Permission(id, 0, LocalDateTime.now(), LocalDateTime.now(), "ADD_ADV_PERM");
			permissions.add(permission);
		}
		return permissions;
	}

	Set<RolePermissions> buildRolePermissions(UUID roleId, Set<UUID> permissionIds) {
		return permissionIds.stream().map(id -> new RolePermissions(roleId,id)).collect(Collectors.toSet());
	}

	RoleResponse buildRoleResponse(Role role,Set<Permission> permissions) {
		Set<PermissionResponse> permissionResponses = permissions.stream().map(
				Permission -> new PermissionResponse(
						Permission.id(),
						Permission.name(),
						Permission.createdAt(),
						Permission.lastUpdatedAt())).collect(Collectors.toSet());

		return new RoleResponse(
				role.id(),
				role.name(),
				role.createdAt(),
				role.lastUpdatedAt(),
				permissionResponses
				);
	}

	Set<PermissionResponse> buildPermissionResponse(UUID id,String name,LocalDateTime created,LocalDateTime lastUpdatedAt,int size) {
		Set<PermissionResponse> permissionResponses = new HashSet<>();
		for(int i= 1 ; i <= size ; i++) {
			PermissionResponse permissionResponse = new PermissionResponse(id,name,created,lastUpdatedAt);
			permissionResponses.add(permissionResponse);
		}
		return permissionResponses;
	}

}
