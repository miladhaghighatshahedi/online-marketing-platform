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
import com.mhs.onlinemarketingplatform.authentication.error.PermissionAlreadyExistsException;
import com.mhs.onlinemarketingplatform.authentication.error.PermissionNotFoundException;
import com.mhs.onlinemarketingplatform.authentication.model.Permission;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.AssertionsKt.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * @author Milad Haghighat Shahedi
 */
@ExtendWith(MockitoExtension.class)
public class PermissionServiceUnitTest {

	@Mock
	private PermissionRepository permissionRepository;

	@Spy
	private PermissionMapper mapper = Mappers.getMapper(PermissionMapper.class);

	@Mock
	private MessageSource messageSource;

	@InjectMocks
	private PermissionService permissionService;

	@Test
	void shouldSavePermission_ReturnPermission() {
		UUID id = UuidCreator.getTimeOrderedEpoch();
		LocalDateTime fixedTime = LocalDateTime.of(2025, 1, 1, 12, 0, 0);
		// Arrange
		AddPermissionRequest request = new AddPermissionRequest("ADD_ADVERTISEMENT");
		Permission mapped = new Permission(id,0,fixedTime,fixedTime,"ADD_ADVERTISEMENT");
		Permission savedPermission = new Permission(id,0, fixedTime,fixedTime,"ADD_ADVERTISEMENT");
		PermissionResponse response = new PermissionResponse(id,"ADD_ADVERTISEMENT",fixedTime,fixedTime);

		when(this.permissionRepository.existsByName("ADD_ADVERTISEMENT")).thenReturn(false);
		when(this.mapper.mapAddRequestToPermission(request)).thenReturn(mapped);
		when(this.permissionRepository.save(mapped)).thenReturn(savedPermission);
		when(this.mapper.mapPermissionToResponse(savedPermission)).thenReturn(response);
		// Act
		PermissionResponse result = this.permissionService.add(request);
		// Assert
		assertEquals("ADD_ADVERTISEMENT",result.name());
		assertNotNull(result.id());
		assertNotNull(result.createdAt());
		assertNotNull(result.lastUpdatedAt());
		assertEquals(0,savedPermission.version());

		verify(this.permissionRepository,atMostOnce()).save(any());
	}

	@Test
	void shouldThrowException_WhenPermissionWithNameAlreadyExists() {
		// Arrange
		AddPermissionRequest addPermissionRequest = new AddPermissionRequest("ADD_ADVERTISEMENT");
		when(this.permissionRepository.existsByName("ADD_ADVERTISEMENT")).thenReturn(true);
		when(this.messageSource.getMessage(
				eq("error.authentication.permission.already.exists.with.name"),
						eq(new Object[]{addPermissionRequest.name()}),
						any(Locale.class)
				)).thenReturn("Permission with the name ADD_ADVERTISEMENT already exists.");
		// Act and Assert
		PermissionAlreadyExistsException exception = assertThrows(
				PermissionAlreadyExistsException.class,
				() -> this.permissionService.add(addPermissionRequest));
		assertEquals("Permission with the name ADD_ADVERTISEMENT already exists.",exception.getMessage());
		verify(this.mapper,never()).mapAddRequestToPermission(any());
		verify(mapper,never()).mapAddRequestToPermission(any());
		verify(this.permissionRepository,never()).save(any());
	}

	@Test
	void shouldUpdatePermission_ReturnPermission() {
		UUID id = UuidCreator.getTimeOrderedEpoch();
		LocalDateTime fixedTime = LocalDateTime.of(2025, 1, 1, 12, 0, 0);
		// Arrange
		UpdatePermissionRequest request = new UpdatePermissionRequest(id,"ADD_ADVERTISEMENT_UPDATED");
		Permission existing = new Permission(id,0,fixedTime,fixedTime,"ADD_ADVERTISEMENT");
		Permission mapped = new Permission(id,0,fixedTime,fixedTime,"ADD_ADVERTISEMENT");
		Permission updatedPermission =  new Permission(id,1,fixedTime,fixedTime,"ADD_ADVERTISEMENT");
		PermissionResponse response = new PermissionResponse(id,"ADD_ADVERTISEMENT_UPDATED",fixedTime,fixedTime);

		when(this.permissionRepository.findById(id)).thenReturn(Optional.of(existing));
		when(this.mapper.mapUpdateRequestToPermission(request, existing)).thenReturn(mapped);
		when(this.permissionRepository.save(mapped)).thenReturn(updatedPermission);
		when(this.mapper.mapPermissionToResponse(updatedPermission)).thenReturn(response);
		// ACT
		PermissionResponse result = this.permissionService.update(request);
		// ASSERT
		assertEquals("ADD_ADVERTISEMENT_UPDATED",result.name());
		assertNotNull(result.id());
		assertNotNull(result.createdAt());
		assertNotNull(result.lastUpdatedAt());
        assertEquals(1,updatedPermission.version());

		verify(permissionRepository).findById(id);
		verify(permissionRepository).save(mapped);
	}

	@Test
	void shouldThrowException_WhenPermissionWithIdDoesNotExists() {
		UUID id = UuidCreator.getTimeOrderedEpoch();
		// Arrange
		UpdatePermissionRequest request = new UpdatePermissionRequest(id,"ADD_ADVERTISEMENT_UPDATED");
		when(this.messageSource.getMessage(
				eq("error.authentication.permission.not.found.with.id"),
				eq(new Object[]{request.id()}),
				any(Locale.class)
		)).thenReturn("Permission with the id "+id+" not found.");
		// Act and Assert
		PermissionNotFoundException exception = assertThrows(
				PermissionNotFoundException.class,
				() -> this.permissionService.update(request));
		assertEquals("Permission with the id "+id+" not found.",exception.getMessage());
		verify(mapper,never()).mapUpdateRequestToPermission(any(),any());
		verify(this.permissionRepository,never()).save(any());
	}

	@Test
	void shouldDelete_WhenPermissionExists() {
		UUID id = UuidCreator.getTimeOrderedEpoch();
		LocalDateTime fixedTime = LocalDateTime.of(2025, 1, 1, 12, 0, 0);
		// Arrange
		Permission exisiting = new Permission(id,0,fixedTime,fixedTime,"ADD_ADVERTISEMENT");
		when(this.permissionRepository.findById(id)).thenReturn(Optional.of(exisiting));
		doNothing().when(this.permissionRepository).delete(exisiting);
		// Act
		this.permissionService.delete(id);
		// Assert
		verify(this.permissionRepository,atMostOnce()).findById(id);
		verify(this.permissionRepository,atMostOnce()).delete(exisiting);
	}

	@Test
	void shouldThrowException_whenPermissionWithIdDoesNotExists_2() {
		UUID id = UuidCreator.getTimeOrderedEpoch();
		// Arrange
		when(this.messageSource.getMessage(
				eq("error.authentication.permission.not.found.with.id"),
				eq(new Object[] {id}),
				any(Locale.class)
		)).thenReturn("Permission with the id "+id+" not found.");
		// Act and Assert
		PermissionNotFoundException exception = assertThrows(
				PermissionNotFoundException.class,
				() -> this.permissionService.delete(id));
		assertEquals("Permission with the id "+id+" not found.",exception.getMessage());
		verify(this.permissionRepository,times(1)).findById(any());
		verify(this.permissionRepository,never()).delete(any());
	}

	@Test
	void shouldReturnPermission_WhenIdExists() {
		UUID id = UuidCreator.getTimeOrderedEpoch();
		LocalDateTime fixedTime = LocalDateTime.of(2025, 1, 1, 12, 0, 0);
		// Arrange
		Permission mockedPermission = new Permission(id,0,fixedTime,fixedTime,"ADD_ADVERTISEMENT");
		when(this.permissionRepository.findById(id)).thenReturn(Optional.of(mockedPermission));
		// Act
		Permission foundUser = this.permissionService.findById(id);
		// Assert
		assertEquals("ADD_ADVERTISEMENT",foundUser.name());
		assertEquals(id,foundUser.id());
	}

	@Test
	void shouldThrowException_whenPermissionWithIdNotFound() {
		UUID id = UuidCreator.getTimeOrderedEpoch();
		// Arrange
		when(this.messageSource.getMessage(
				eq("error.authentication.permission.not.found.with.id"),
				eq(new Object[] {id}),
				any(Locale.class)
		)).thenReturn("Permission with the id "+id+" not found.");
		// Act and Assert
		PermissionNotFoundException exception = assertThrows(
				PermissionNotFoundException.class,
				() -> this.permissionService.delete(id));
		assertEquals("Permission with the id "+id+" not found.",exception.getMessage());
		verify(this.permissionRepository,never()).delete(any());
	}

	@Test
	void shouldReturnPermissions_WhenPermissionsExist() {
		UUID id_1 = UuidCreator.getTimeOrderedEpoch();
		UUID id_2 = UuidCreator.getTimeOrderedEpoch();
		UUID id_3 = UuidCreator.getTimeOrderedEpoch();
		Set<Permission> permissions = getPermissions(id_1, id_2, id_3);
		// Arrange
		when(this.permissionRepository.findAll()).thenReturn(permissions);
		// Act
		Set<Permission> result = this.permissionService.findAll();
		// Assert
		assertEquals(3,result.size());
		verify(this.permissionRepository,atMostOnce()).findAll();
	}

	@Test
	void ShouldReturnEmptyList_WhenPermissionsDoNotExist_1() {
		// Arrange
		when(this.permissionRepository.findAll()).thenReturn(Collections.emptySet());
		// Act
		Set<Permission> result = this.permissionService.findAll();
		//
		assertNotNull(result);
		assertEquals(Collections.emptySet(),result);
		assertEquals(0,result.size());
		verify(this.permissionRepository,atMostOnce()).findAll();
	}

	@Test
	void shouldReturnPermissions_WhenPermissionsExistByIds() {
		UUID id_1 = UuidCreator.getTimeOrderedEpoch();
		UUID id_2 = UuidCreator.getTimeOrderedEpoch();
		UUID id_3 = UuidCreator.getTimeOrderedEpoch();
		Set<Permission> permissions = getPermissions(id_1, id_2, id_3);
		// Arrange
		when(this.permissionRepository.findAllById(Set.of(id_1,id_2,id_3))).thenReturn(permissions);
		// Act
		Set<Permission> results = this.permissionService.findAllById(Set.of(id_1, id_2, id_3));
		//
		assertEquals(3,results.size());
		verify(this.permissionRepository,times(1)).findAllById(any());
	}

	@Test
	void shouldReturnEmptySet_WhenPermissionsDoNotExistByIds() {
		// Arrange
		when(this.permissionRepository.findAllById(Collections.emptySet())).thenReturn(Collections.emptySet());
		// Act
		Set<Permission> result = this.permissionService.findAllById(Collections.emptySet());
		//
		assertEquals(0,result.size());
		assertTrue(result.isEmpty());

		verify(this.permissionRepository,times(1)).findAllById(any());
	}

	@Test
	void shouldRetrunPermissions_WhenAnyPermissionExistsByIds() {
		UUID id_1 = UuidCreator.getTimeOrderedEpoch();
		UUID id_2 = UuidCreator.getTimeOrderedEpoch();
		UUID id_3 = UuidCreator.getTimeOrderedEpoch();
		LocalDateTime fixedTime = LocalDateTime.of(2025,1,1,12,0,0);
		Permission existing = new Permission(id_1,0,fixedTime,fixedTime,"ADD_ADVERTISEMENT");
		// Arrange
		when(this.permissionRepository.findAllById(Set.of(id_1,id_2,id_3))).thenReturn(Set.of(existing));
		// Act
		Set<Permission> result = this.permissionService.findAllById(Set.of(id_1, id_2, id_3));
		// Assert
		assertEquals(1,result.size());
		assertTrue(result.contains(existing));

		verify(this.permissionRepository,times(1)).findAllById(any());
	}

	@Test
	void shouldReturnSetOfPermissionResponse_WhenPermissionExist() {
		UUID id_1 = UuidCreator.getTimeOrderedEpoch();
		UUID id_2 = UuidCreator.getTimeOrderedEpoch();
		UUID id_3 = UuidCreator.getTimeOrderedEpoch();
		Set<Permission> permissions = getPermissions(id_1, id_2, id_3);
		Set<PermissionResponse> permissionResponses = getPermissionsAsSet(permissions);
		// Arrange
		when(this.permissionRepository.findAll()).thenReturn(permissions);
		doReturn(permissionResponses).when(this.mapper).mapPermissionsToResponse(any());
		// Act
		Set<PermissionResponse> result = this.permissionService.fetchAllMapToSet();
		// Assert
		assertNotNull(result);
		assertFalse(result.isEmpty());
		assertEquals(3,result.size());
		verify(this.permissionRepository,times(1)).findAll();
		verify(this.mapper,times(1)).mapPermissionsToResponse(any());
	}

	@Test
	void ShouldReturnEmptyList_WhenPermissionsDoNotExist_2() {
		// Arrange
		when(this.permissionRepository.findAll()).thenReturn(Collections.emptySet());
		doReturn(Collections.emptySet()).when(this.mapper).mapPermissionsToResponse(Collections.emptySet());
		// Act
		Set<PermissionResponse> result = this.permissionService.fetchAllMapToSet();
		//
		assertNotNull(result);
		assertTrue(result.isEmpty());
		assertEquals(0,result.size());
		verify(this.permissionRepository,times(1)).findAll();
		verify(this.mapper,times(1)).mapPermissionsToResponse(any());
	}

	@Test
    void shouldRetunPermissionPageResponse() {
		UUID id_1 = UuidCreator.getTimeOrderedEpoch();
	    UUID id_2 = UuidCreator.getTimeOrderedEpoch();
	    UUID id_3 = UuidCreator.getTimeOrderedEpoch();
	    Set<Permission> permissions = getPermissions(id_1, id_2, id_3);

	    Pageable pageable = PageRequest.of(0,6);

	    int pageSize = pageable.getPageSize();

		List<Permission> pageContent = new ArrayList<>(permissions);

	    Page<Permission> permissionPage = new PageImpl<>(pageContent, pageable, pageContent.size());

		PermissionPageResponse<PermissionResponse> response = new PermissionPageResponse<>(
				permissions.stream().map(this.mapper::mapPermissionToResponse).collect(Collectors.toList())
				,0,pageSize,3,1
		);
		// Arrang
        when(this.permissionRepository.findAllByOrderByCreatedAt(pageable)).thenReturn(permissionPage);
		doReturn(response).when(this.mapper).mapPermissionToPageResponse(permissionPage);
		// Act
	    PermissionPageResponse<PermissionResponse> result = this.permissionService.findAllPaged(pageable);
		//
		assertEquals(3,result.totalElements());
		assertEquals(3,result.content().size());

	    verify(this.permissionRepository,times(1)).findAllByOrderByCreatedAt(any());
		verify(this.mapper,times(1)).mapPermissionToPageResponse(any());
    }

	@Test
    void shouldReturnEmptyPermissionPageResponse_whenPermissionsDoNotExists() {
	    Pageable pageable = PageRequest.of(0,6);
		Page<Permission> emptyPage = Page.empty(pageable);
		// Assert
	    when(this.permissionRepository.findAllByOrderByCreatedAt(pageable)).thenReturn(emptyPage);
		// Act
	    PermissionPageResponse<PermissionResponse> result = this.permissionService.findAllPaged(pageable);
		// Assert
	    assertEquals(0,result.totalElements());
	    assertEquals(0,result.content().size());
		assertTrue(result.content().isEmpty());

	    verify(this.permissionRepository,times(1)).findAllByOrderByCreatedAt(any());
	    verify(this.mapper,times(1)).mapPermissionToPageResponse(any());
    }

	@Test
    void shouldReturnPermission_whenPermissionExisits() {
		UUID id = UuidCreator.getTimeOrderedEpoch();
		LocalDateTime fixedTime = LocalDateTime.of(2025,1,1,12,0,0);
		Permission permission = new Permission(id,0,fixedTime,fixedTime,"ADD_ADVERTISEMENT");
		// Arrange
		when(this.permissionRepository.findById(id)).thenReturn(Optional.of(permission));
		// Act
		Permission result = this.permissionService.findById(id);
		//
		assertEquals(id,result.id());
		verify(this.permissionRepository,times(1)).findById(any());
	}

	@Test
	void shouldThrowException_whenPermissionWithIdDoesNotExists() {
		UUID id = UuidCreator.getTimeOrderedEpoch();
		// Arrange
		when(this.messageSource.getMessage(
				eq ("error.authentication.permission.not.found.with.id"),
				eq (new Object[] {id}),
				any (Locale.class)
		)).thenReturn("Permission with the id "+id+" not found.");
		// Act
		PermissionNotFoundException exception = assertThrows(
				PermissionNotFoundException.class,
				() -> this.permissionService.findById(id));
		// Assert
		assertEquals("Permission with the id "+id+" not found.",exception.getMessage());
		verify(this.permissionRepository,times(1)).findById(any());
		verify(this.mapper,never()).mapPermissionToResponse(any());
	}

	@Test
	void shouldReturnResponse_WhenPermissionExistsById() {
		UUID id = UuidCreator.getTimeOrderedEpoch();
		LocalDateTime fixedTime = LocalDateTime.of(2025, 1, 1, 12, 0, 0);
		Permission foundPermission = new Permission(id,0,fixedTime,fixedTime,"ADD_ADVERTISEMENT");
		PermissionResponse response = new PermissionResponse(id,"ADD_ADVERTISEMENT",fixedTime,fixedTime);
		// Arrange
		when(this.permissionRepository.findById(id)).thenReturn(Optional.of(foundPermission));
		doReturn(response).when(this.mapper).mapPermissionToResponse(foundPermission);
		// Act
		PermissionResponse result = this.permissionService.fetchById(id);
		// Assert
		assertEquals(id,result.id());

		verify(this.permissionRepository,times(1)).findById(any());
		verify(this.mapper,times(1)).mapPermissionToResponse(any());
	}

	@Test
	void shouldThrowException_shouldReturnResponse_WhenPermissionDoesNotExistsById() {
		UUID id = UuidCreator.getTimeOrderedEpoch();
		// Arrange
		when(this.messageSource.getMessage(
				eq ("error.authentication.permission.not.found.with.id"),
				eq (new Object[] {id}),
				any (Locale.class)
		)).thenReturn("Permission with the id "+id+" not found.");
		// Act
		PermissionNotFoundException exception = assertThrows(
			PermissionNotFoundException.class, () -> this.permissionService.fetchById(id));
		// Assert
		assertEquals("Permission with the id "+id+" not found.",exception.getMessage());
		verify(this.permissionRepository,times(1)).findById(any());
		verify(this.mapper,never()).mapPermissionToResponse(any());
	}

	@Test
	void shouldReturnPermission_WhenPermissionWithNameExists() {
		UUID id = UuidCreator.getTimeOrderedEpoch();
		LocalDateTime fixedtime = LocalDateTime.of(2025,1,1,12,0,0);
		// Arrange
		Permission foundPermission = new Permission(id,0,fixedtime,fixedtime,"ADD_ADVERTISEMENT");
		PermissionResponse response = new PermissionResponse(id,"ADD_ADVERTISEMENT",fixedtime,fixedtime);
		when(this.permissionRepository.findByName("ADD_ADVERTISEMENT")).thenReturn(Optional.of(foundPermission));
		doReturn(response).when(this.mapper).mapPermissionToResponse(foundPermission);
		// Act
		PermissionResponse result = this.permissionService.findByName("ADD_ADVERTISEMENT");
		// Assert
		assertEquals("ADD_ADVERTISEMENT",result.name());

		verify(this.permissionRepository,times(1)).findByName(any());
		verify(this.mapper,times(1)).mapPermissionToResponse(any());
	}

	@Test
	void shouldThrowException_whenPermissionWithNameDoesNotExists() {
		String name = "ADD_ADVERTISEMENT";
		// Arrange
		when(this.messageSource.getMessage(
				eq ("error.authentication.permission.not.found.with.name"),
				eq (new Object[] {"ADD_ADVERTISEMENT"}),
				any (Locale.class)
		)).thenReturn("Permission with the name ADD_ADVERTISEMENT not found.");
		// Act
		PermissionNotFoundException exception = assertThrows(
				PermissionNotFoundException.class,
				() -> this.permissionService.findByName(name));
		// Assert
		assertEquals("Permission with the name ADD_ADVERTISEMENT not found.",exception.getMessage());
		verify(this.permissionRepository,atMostOnce()).findByName(any());
		verify(this.mapper,never()).mapPermissionToResponse(any());
	}

	@Test
	void shouldReturnTrue_whenIdExists() {
		UUID id = UuidCreator.getTimeOrderedEpoch();
		// Arrange
		when(this.permissionService.existsById(id)).thenReturn(true);
		// Act and Assert
		assertTrue(this.permissionService.existsById(id));
		verify(this.permissionRepository,times(1)).existsById(any());
	}

	@Test
	void shouldReturnFalse_whenIdDoesNotExists() {
		UUID id = UuidCreator.getTimeOrderedEpoch();
		// Arrange
		when(this.permissionService.existsById(id)).thenReturn(false);
		// Act and Assert
		assertFalse(this.permissionService.existsById(id));
		verify(this.permissionRepository,times(1)).existsById(any());
	}

	@Test
	void shouldReturnTrue_whenNameExists() {
		// Arrange
		when(this.permissionService.existsByName("ADD_ADVERTISEMENT")).thenReturn(true);
		// Act and Assert
		assertTrue(this.permissionService.existsByName("ADD_ADVERTISEMENT"));
		verify(this.permissionRepository,times(1)).existsByName(any());
	}

	@Test
	void shouldReturnFalse_whenNameDoesNotExists() {
		// Arrange
		when(this.permissionService.existsByName("ADD_ADVERTISEMENT")).thenReturn(false);
		// Act and Assert
		assertFalse(this.permissionService.existsByName("ADD_ADVERTISEMENT"));
		verify(this.permissionRepository,times(1)).existsByName(any());
	}

	private static Set<Permission> getPermissions(UUID id_1, UUID id_2, UUID id_3) {
		LocalDateTime fixedTime1 = LocalDateTime.of(2025,1,1,12,0,0);
		LocalDateTime fixedTime2 = LocalDateTime.of(2025,1,1,12,0,0);
		LocalDateTime fixedTime3 = LocalDateTime.of(2025,1,1,12,0,0);
		Permission permission1 = new Permission(id_1,0,fixedTime1,fixedTime1,"ADD_ADVERTISEMENT");
		Permission permission2 = new Permission(id_2,0,fixedTime2,fixedTime2,"UPDATE_ADVERTISEMENT");
		Permission permission3 = new Permission(id_3,0,fixedTime3,fixedTime3,"DELETE_ADVERTISEMENT");
		return Set.of(permission1,permission2,permission3);
	}

	private static Set<PermissionResponse> getPermissionsAsSet(Set<Permission> permissions) {
		return permissions.stream().map(p -> new PermissionResponse(p.id(), p.name(), p.createdAt(), p.lastUpdatedAt())).collect(Collectors.toSet());
	}

}
