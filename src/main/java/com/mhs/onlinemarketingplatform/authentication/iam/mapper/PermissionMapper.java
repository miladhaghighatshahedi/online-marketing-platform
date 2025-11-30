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
package com.mhs.onlinemarketingplatform.authentication.iam.mapper;

import com.github.f4b6a3.uuid.UuidCreator;
import com.mhs.onlinemarketingplatform.authentication.iam.dto.AddPermissionRequest;
import com.mhs.onlinemarketingplatform.authentication.iam.dto.UpdatePermissionRequest;
import com.mhs.onlinemarketingplatform.authentication.iam.model.Permission;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.LocalDateTime;

/**
 * @author Milad Haghighat Shahedi
 */
@Mapper(componentModel = "spring",imports = {UuidCreator.class, LocalDateTime.class})
public interface PermissionMapper {

	@Mapping(target = "id", expression = "java(UuidCreator.getTimeOrderedEpoch())")
	@Mapping(target = "version",ignore = true)
	Permission mapAddRequestToPermission(AddPermissionRequest request);

	@Mapping(target = "id", source = "permission.id")
	@Mapping(target = "version", source = "permission.version")
	@Mapping(target = "name", source = "request.name")
	Permission mapUpdateRequestToPermission(UpdatePermissionRequest request, Permission permission);

}
