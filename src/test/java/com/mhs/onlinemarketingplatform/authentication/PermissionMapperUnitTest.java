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
import com.mhs.onlinemarketingplatform.authentication.model.Permission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;

import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
/**
 * @author Milad Haghighat Shahedi
 */
@ExtendWith(MockitoExtension.class)
public class PermissionMapperUnitTest {


	private final PermissionMapper permissionMapper = Mappers.getMapper(PermissionMapper.class);

	private UUID id;
	private LocalDateTime fixedTime;

	@BeforeEach
	void setUp() {
		id = UuidCreator.getTimeOrderedEpoch();
		fixedTime = LocalDateTime.of(2025,1,1,12,0,0);
	}

	@Test
	void mapAddRequestToPermission_test() {
		// Arrange
		AddPermissionRequest request = new AddPermissionRequest("ADD_ADVERTISEMENT_PERM");
		// Act
		Permission result = this.permissionMapper.mapAddRequestToPermission(request);
		// Assert
		assertNotNull(result);
		assertNotNull(result.createdAt());
		assertNotNull(result.lastUpdatedAt());
		assertNotNull(result.name());
		assertEquals("ADD_ADVERTISEMENT_PERM",result.name());
		assertNotNull(result.id());
		assertEquals(0,result.version());

		System.out.println(result.version());
		System.out.println(result.id());
	}

	@Test
	void mapUpdateRequestToPermission_test() {
		// Arrange
		Permission existing = new Permission(id,0,fixedTime,fixedTime,"ADD_ADVERTISEMENT_PERM");
		UpdatePermissionRequest request = new UpdatePermissionRequest(id,"ADD_ADVERTISEMENT_PERM_updated");
		// Act
		Permission result = this.permissionMapper.mapUpdateRequestToPermission(request, existing);
		// Assert
		assertNotNull(result);
		assertNotNull(result.createdAt());
		assertEquals(existing.createdAt(),result.createdAt());
		assertNotEquals(existing.lastUpdatedAt(),result.lastUpdatedAt());
		assertNotEquals(existing.name(),result.name());
	}

	@Test
	void mapPermissionToResponse_test() {
		// Arrange
		PermissionResponse expected = new PermissionResponse(id,"ADD_ADVERTISEMENT_PERM",fixedTime,fixedTime);
		Permission permission = new Permission(id,0,fixedTime,fixedTime,"ADD_ADVERTISEMENT_PERM");
		// Act
		PermissionResponse result = this.permissionMapper.mapPermissionToResponse(permission);
		// Assert
		assertNotNull(result);
		assertNotNull(result.createdAt());
		assertNotNull(result.lastUpdatedAt());
		assertNotNull(result.name());
		assertNotNull(result.id());

		assertEquals(expected.name(),result.name());
		assertEquals(expected.id(),result.id());
		assertEquals(expected.createdAt(),result.createdAt());
		assertEquals(expected.lastUpdatedAt(),result.lastUpdatedAt());
	}

	@Test
	void mapPermissionsToResponse_test() {
		// Arrange
		Permission permission1 = new Permission(id,0,fixedTime,fixedTime,"ADD_ADVERTISEMENT_PERM");
		Permission permission2 = new Permission(id,0,fixedTime,fixedTime,"UPDATE_ADVERTISEMENT_PERM");
		Permission permission3 = new Permission(id,0,fixedTime,fixedTime,"DELETE_ADVERTISEMENT_PERM");
		Set<Permission> permissions = Set.of(permission1,permission2,permission3);
		// Act
		Set<PermissionResponse> result = this.permissionMapper.mapPermissionsToResponse(permissions);
		// Assert
		assertNotNull(result);
		assertEquals(3,result.size());
	}

   @Test
	void mapPermissionToPageResponse_test() {
		// Arrange
	    Page<Permission> permissionPage = getPermissions(PageRequest.of(0,6));
	    // Act
	    PermissionPageResponse<PermissionResponse> response = this.permissionMapper.mapPermissionToPageResponse(permissionPage);
	    // Assert
	   assertEquals(7,response.content().size());
	   assertEquals(7,response.totalElements());
	   assertEquals(2,response.totalPage());
	   assertEquals(6,response.size());

   }

	private Page<Permission> getPermissions(Pageable pageable) {
		Permission permission1 = new Permission(id,0,fixedTime,fixedTime,"ADD_ADVERTISEMENT_PERM");
		Permission permission2 = new Permission(id,0,fixedTime,fixedTime,"UPDATE_ADVERTISEMENT_PERM");
		Permission permission3 = new Permission(id,0,fixedTime,fixedTime,"DELETE_ADVERTISEMENT_PERM");
		Permission permission4 = new Permission(id,0,fixedTime,fixedTime,"ADD_MESSAGE_PERM");
		Permission permission5 = new Permission(id,0,fixedTime,fixedTime,"UPDATE_MESSAGE_PERM");
		Permission permission6 = new Permission(id,0,fixedTime,fixedTime,"DELETE_MESSAGE_PERM");
		Permission permission7 = new Permission(id,0,fixedTime,fixedTime,"DELETE_PHOTO_PERM");
		List<Permission> permissions = List.of(permission1, permission2, permission3,permission4,permission5,permission6,permission7);
		List<Permission> permissionList =  new ArrayList<>(permissions);
		return new PageImpl<>(permissionList, pageable, permissionList.size());
	}
}
