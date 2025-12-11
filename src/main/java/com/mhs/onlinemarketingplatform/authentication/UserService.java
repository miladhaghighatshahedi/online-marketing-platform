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
import com.mhs.onlinemarketingplatform.authentication.error.AuthenticationErrorCode;
import com.mhs.onlinemarketingplatform.authentication.error.user.UserNotFoundException;
import com.mhs.onlinemarketingplatform.authentication.model.Role;
import com.mhs.onlinemarketingplatform.authentication.model.User;
import com.mhs.onlinemarketingplatform.authentication.model.UserRoles;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
/**
 * @author Milad Haghighat Shahedi
 */
@Service
public class UserService {

	private final UserRepository userRepository;
	private final UserRoleRepository userRoleRepository;
	private final RoleServiceInternal roleServiceInternal;
	private final MessageSource messageSource;
	private final UserMapper mapper;

	UserService(
			UserRepository userRepository,
			UserRoleRepository userRoleRepository,
			RoleServiceInternal roleServiceInternal,
			MessageSource messageSource,
			UserMapper mapper) {
		this.userRepository = userRepository;
		this.userRoleRepository = userRoleRepository;
		this.roleServiceInternal = roleServiceInternal;
		this.messageSource = messageSource;
		this.mapper = mapper;
	}

	@Transactional
	public User findByPhoneNumberOrCreate(AddUserRequest addUserRequest) {
		if(!existsByPhoneNumber(addUserRequest.phoneNumber())) {
			User mappedUser = this.mapper.mapAddRequestToUser(addUserRequest);
			return this.userRepository.save(mappedUser);
		}
		return this.userRepository.findByPhoneNumber(addUserRequest.phoneNumber()).get();
	}

	public User findByPhoneNumber(String phoneNumber) {
		return this.userRepository.findByPhoneNumber(phoneNumber).orElseThrow(() ->
				new UserNotFoundException(
						messageSource.getMessage("error.authentication.user.not.found.with.phone.number",
								new Object[]{phoneNumber},
								LocaleContextHolder.getLocale()), AuthenticationErrorCode.USER_NOT_FOUND));
	}

	public boolean existsByPhoneNumber(String phoneNumber) {
		return this.userRepository.existsByPhoneNumber(phoneNumber);
	}

    public Set<Role> findRolesByUserId(UUID userId) {
	    Set<UserRoles> userRoles = this.userRoleRepository.findByRoleId(userId);
	    Set<UUID> roleIds = userRoles.stream().map(UserRoles::roleId).collect(Collectors.toSet());
	    return this.roleServiceInternal.findAllById(roleIds);
    }

}

@Repository
interface UserRepository extends CrudRepository<User, UUID> {

	Optional<User> findByPhoneNumber(String phoneNumber);

	boolean existsByPhoneNumber(String phoneNumber);

}

@Repository
interface UserRoleRepository extends CrudRepository<UserRoles,UUID> {
	@Modifying
	@Query("INSERT INTO auth_user_roles(user_id, role_id) VALUES (:userId,:roleId)")
	void insert(@Param("userId") UUID userId, @Param("roleId") UUID roleId);

	@Modifying
	@Query("DELETE FROM auth_user_roles WHERE  user_id= :userId AND role_id= :roleId")
	void delete(@Param("userId") UUID userId, @Param("roleId")UUID roleId);

	@Modifying
	@Query("DELETE FROM auth_user_roles WHERE user_id = :userId")
	void deleteByUserId(@Param("userId") UUID userId);

	Set<UserRoles> findByRoleId(@Param("userId") UUID userId);
}

@Mapper(componentModel = "spring",imports = {UuidCreator.class, LocalDateTime.class})
interface UserMapper {

	@Mapping(target = "id", expression = "java(UuidCreator.getTimeOrderedEpoch())")
	@Mapping(target = "version", ignore = true)
	@Mapping(target = "joinedAt", expression = "java(LocalDateTime.now())")
	@Mapping(target = "disabledAt", ignore = true)
	@Mapping(target = "enabled", constant = "false")
	User mapAddRequestToUser(AddUserRequest dto);

}

