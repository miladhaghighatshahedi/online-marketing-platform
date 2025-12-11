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
import com.mhs.onlinemarketingplatform.authentication.api.RoleServiceInternal;
import com.mhs.onlinemarketingplatform.authentication.dto.AddUserRequest;
import com.mhs.onlinemarketingplatform.authentication.error.user.UserNotFoundException;
import com.mhs.onlinemarketingplatform.authentication.model.Role;
import com.mhs.onlinemarketingplatform.authentication.model.User;
import com.mhs.onlinemarketingplatform.authentication.model.UserRoles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
/**
 * @author Milad Haghighat Shahedi
 */
@ExtendWith(MockitoExtension.class)
public class UserServiceUnitTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private UserRoleRepository userRoleRepository;

	@Mock
	private RoleServiceInternal roleServiceInternal;

	@Mock
	private MessageSource messageSource;

	@Mock
	private UserMapper userMapper;

	@Spy
	@InjectMocks
	private UserService userService;

	@Test
    void findByPhoneNumberOrCreate_method_shouldFindAUserByPhoneNumber() {
		UUID roleId = UuidCreator.getTimeOrderedEpoch();
		UUID userId = UuidCreator.getTimeOrderedEpoch();
		String phoneNumber = "+989123234323";
		User mockedUser = new User(userId,0,phoneNumber, LocalDateTime.now(),null,true);
		// Arrange
		AddUserRequest request = new AddUserRequest(phoneNumber, Set.of(roleId));
		when(this.userService.existsByPhoneNumber(phoneNumber)).thenReturn(true);
		when(this.userRepository.findByPhoneNumber(phoneNumber)).thenReturn(Optional.of(mockedUser));
		// Act
		User result = this.userService.findByPhoneNumberOrCreate(request);
		// Assert
		assertNotNull(result);
		assertEquals(phoneNumber,result.phoneNumber());

		verify(this.userService,times(1)).existsByPhoneNumber(any());
		verify(this.userMapper,never()).mapAddRequestToUser(any());
		verify(this.userRepository,never()).save(any());
		verify(this.userRepository,times(1)).findByPhoneNumber(any());
    }

	@Test
	void findByPhoneNumberOrCreate_method_shouldCreateAndSaveAnewUser() {
		UUID roleId = UuidCreator.getTimeOrderedEpoch();
		UUID userId = UuidCreator.getTimeOrderedEpoch();
		String phoneNumber = "+989123234323";

		User mappedUser = new User(userId,0,phoneNumber, LocalDateTime.now(),null,true);
		User mockedUser = new User(userId,0,phoneNumber, LocalDateTime.now(),null,true);

		// Arrange
		AddUserRequest request = new AddUserRequest(phoneNumber, Set.of(roleId));
		when(this.userService.existsByPhoneNumber(phoneNumber)).thenReturn(false);
		when(this.userMapper.mapAddRequestToUser(request)).thenReturn(mappedUser);
		when(this.userRepository.save(mappedUser)).thenReturn(mockedUser);
		// Act
		User result = this.userService.findByPhoneNumberOrCreate(request);
		//
		assertNotNull(result);
		assertEquals(phoneNumber,result.phoneNumber());

		verify(this.userService,times(1)).existsByPhoneNumber(any());
		verify(this.userMapper,times(1)).mapAddRequestToUser(any());
		verify(this.userRepository,times(1)).save(any());
		verify(this.userRepository,never()).findByPhoneNumber(any());
	}

	@Test
	void findByPhoneNumber_method_shouldReturnUser() {
		UUID userId = UuidCreator.getTimeOrderedEpoch();
		String phoneNumber = "+989123234323";

		User mockedUser = new User(userId,0,phoneNumber, LocalDateTime.now(),null,true);
		// Arrange
		when(this.userRepository.findByPhoneNumber(phoneNumber)).thenReturn(Optional.of(mockedUser));
		// Act
		User result = this.userService.findByPhoneNumber(phoneNumber);
		//Assert
		assertNotNull(result);
		assertEquals(phoneNumber,result.phoneNumber());
		verify(this.userService,times(1)).findByPhoneNumber(any());
	}

	@Test
	void findByPhoneNumber_method_shouldThrowUserNotFoundException_whenUserDoesNotExistsWithId() {
		UUID userId = UuidCreator.getTimeOrderedEpoch();
		String phoneNumber = "+989123234323";
		// Arrange
		when(this.userRepository.findByPhoneNumber(phoneNumber)).thenReturn(Optional.empty());
		when(this.messageSource.getMessage(
			eq("error.authentication.user.not.found.with.phone.number"),
				eq(new Object[]{phoneNumber}),
				any(Locale.class))).thenReturn("User with the phoneNumber "+phoneNumber+" not found.");
		// Act
		UserNotFoundException exception = assertThrows(UserNotFoundException.class,
				()-> this.userService.findByPhoneNumber(phoneNumber));
		// Assert
		assertEquals("User with the phoneNumber +989123234323 not found.",exception.getMessage());
		verify(this.userRepository,times(1)).findByPhoneNumber(any());
	}

	@Test
    void existsByPhoneNumber_method_shouldReturnTrue() {
		String phoneNumber = "+989123234323";
		// Arrange
		when(this.userRepository.existsByPhoneNumber(phoneNumber)).thenReturn(true);
		// Act
		boolean result = this.userService.existsByPhoneNumber(phoneNumber);
		// Assert
		assertTrue(result);
	}

	@Test
	void existsByPhoneNumber_method_shouldReturnFalse() {
		String phoneNumber = "+989123234323";
		// Arrange
		when(this.userRepository.existsByPhoneNumber(phoneNumber)).thenReturn(false);
		// Act
		boolean result = this.userService.existsByPhoneNumber(phoneNumber);
		// Assert
		assertFalse(result);
	}

	@Test
	void findRolesByUserId_method_shouldReturnSetOfRoles() {
		UUID userId = UuidCreator.getTimeOrderedEpoch();

		UUID roleId1 = UuidCreator.getTimeOrderedEpoch();
		UUID roleId2 = UuidCreator.getTimeOrderedEpoch();
		Set<UUID> mockedRoleIds = Set.of(roleId1,roleId2);
		Role roleUser = new Role(roleId1,0,LocalDateTime.now(),LocalDateTime.now(),"ROLE_USER");
		Role roleAdmin = new Role(roleId1,0,LocalDateTime.now(),LocalDateTime.now(),"ROLE_ADMIN");

		Set<Role> mockedRoles = Set.of(roleUser,roleAdmin);

		UserRoles userRoles_USER = new UserRoles(userId,roleId1);
		UserRoles userRoles_ADMIN = new UserRoles(userId,roleId2);

		Set<UserRoles> mockedUserRoles = Set.of(userRoles_USER,userRoles_ADMIN);
		//
		when(this.userRoleRepository.findByRoleId(userId)).thenReturn(mockedUserRoles);
		when(this.roleServiceInternal.findAllById(mockedRoleIds)).thenReturn(mockedRoles);
		// Act
		Set<Role> result = this.userService.findRolesByUserId(userId);
		// Assert
		assertEquals(2,result.size());
		assertTrue(result.contains(roleUser));
		assertTrue(result.contains(roleAdmin));
		assertTrue(result.stream().anyMatch(x -> x.name().equals("ROLE_USER")));
		assertTrue(result.stream().anyMatch(x -> x.name().equals("ROLE_ADMIN")));

		verify(this.userRoleRepository,times(1)).findByRoleId(any());
		verify(this.roleServiceInternal,times(1)).findAllById(any());
	}

	@Test
	void findRolesByUserId_method_returnempty() {
		UUID userId = UuidCreator.getTimeOrderedEpoch();
		// Arrange
		when(this.userRoleRepository.findByRoleId(userId)).thenReturn(Set.of());
		when(this.roleServiceInternal.findAllById(Set.of())).thenReturn(Set.of());
		// Act
		Set<Role> result = this.userService.findRolesByUserId(userId);
		// Assert
		assertNotNull(result);
		assertTrue(result.isEmpty());

		verify(userRoleRepository, times(1)).findByRoleId(userId);
		verify(roleServiceInternal, times(1)).findAllById(Set.of());
	}


}
