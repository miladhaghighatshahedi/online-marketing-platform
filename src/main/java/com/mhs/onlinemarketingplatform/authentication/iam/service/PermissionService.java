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
package com.mhs.onlinemarketingplatform.authentication.iam.service;

import com.mhs.onlinemarketingplatform.authentication.iam.dto.AddPermissionRequest;
import com.mhs.onlinemarketingplatform.authentication.error.AuthenticationErrorCode;
import com.mhs.onlinemarketingplatform.authentication.error.PermissionAlreadyExistsException;
import com.mhs.onlinemarketingplatform.authentication.error.PermissionNotFoundException;
import com.mhs.onlinemarketingplatform.authentication.iam.dto.UpdatePermissionRequest;
import com.mhs.onlinemarketingplatform.authentication.iam.mapper.PermissionMapper;
import com.mhs.onlinemarketingplatform.authentication.iam.model.Permission;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * @author Milad Haghighat Shahedi
 */
@Service
public class PermissionService {

	private final PermissionRepository permissionRepository;
	private final PermissionMapper mapper;
	private final MessageSource messageSource;

	PermissionService(PermissionRepository permissionRepository, PermissionMapper mapper, MessageSource messageSource) {
		this.permissionRepository = permissionRepository;
		this.mapper = mapper;
		this.messageSource = messageSource;
	}

	@Transactional
	public Permission add(AddPermissionRequest request) {
		if(existsByName(request.name())) {
			throw new PermissionAlreadyExistsException(
					messageSource.getMessage("error.authentication.permission.already.exists.with.name",
							new Object[]{request.name()},
							LocaleContextHolder.getLocale()),
					AuthenticationErrorCode.PERMISSION_ALREADY_EXISTS);
		}
		Permission permission = this.mapper.mapAddRequestToPermission(request);
		return this.permissionRepository.save(permission);
	}

	public Permission update(UpdatePermissionRequest request) {
		Permission existingPermission = this.permissionRepository.findById(request.id()).orElseThrow(() ->
				new PermissionNotFoundException(
						messageSource.getMessage("error.authentication.permission.not.found.with.id",
								new Object[]{request.id()},
								LocaleContextHolder.getLocale()),
						AuthenticationErrorCode.PERMISSION_NOT_FOUND));

		Permission mappedToPermission = this.mapper.mapUpdateRequestToPermission(request, existingPermission);
		return this.permissionRepository.save(mappedToPermission);
	}

	public void remove(UUID id) {
		Permission foundPermission = this.permissionRepository.findById(id).orElseThrow(() ->
				new PermissionNotFoundException(
						messageSource.getMessage("error.authentication.permission.not.found.with.id",
								new Object[]{id},
								LocaleContextHolder.getLocale()),
						AuthenticationErrorCode.PERMISSION_NOT_FOUND));
		this.permissionRepository.delete(foundPermission);
	}

	public Permission findById(UUID id) {
		 return this.permissionRepository.findById(id).orElseThrow(() ->
				 new PermissionNotFoundException(messageSource.getMessage("error.authentication.permission.not.found.with.id",
						 new Object[] {id},
						 LocaleContextHolder.getLocale()),
						 AuthenticationErrorCode.PERMISSION_NOT_FOUND));
	}

	public Permission findByName(String name) {
		return this.permissionRepository.findByName(name).orElseThrow(() ->
				new PermissionNotFoundException(messageSource.getMessage("error.authentication.permission.not.found.with.name",
						new Object[] {name},
						LocaleContextHolder.getLocale()),
						AuthenticationErrorCode.PERMISSION_NOT_FOUND));
	}

	public boolean existsByName(String name) {
		return this.permissionRepository.existsByName(name);
	}

}

@Repository
interface PermissionRepository extends CrudRepository<Permission, UUID> {

	Optional<Permission> findById(UUID id);

	Optional<Permission> findByName(String name);

	boolean existsByName(@Param("name") String name);

}

