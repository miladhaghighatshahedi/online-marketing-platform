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
package com.mhs.onlinemarketingplatform.authentication.device;

import com.mhs.onlinemarketingplatform.authentication.dto.AddDeviceBindingRequest;
import com.mhs.onlinemarketingplatform.authentication.dto.UpdateDeviceBindingRequest;
import com.mhs.onlinemarketingplatform.authentication.error.devicebinding.DeviceBindingErrorCode;
import com.mhs.onlinemarketingplatform.authentication.error.devicebinding.UnauthorizedDeviceException;
import com.mhs.onlinemarketingplatform.authentication.model.DeviceBinding;
import com.mhs.onlinemarketingplatform.authentication.model.DeviceBindingId;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.context.MessageSource;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
/**
 * @author Milad Haghighat Shahedi
 */
@Service
public class DeviceBindingService {

    private final DeviceBindingRepository repository;
	private final MessageSource messageSource;
	private final DeviceBindingMapper mapper;

	DeviceBindingService(
			DeviceBindingRepository repository,
			MessageSource messageSource,
			DeviceBindingMapper mapper) {
		this.repository = repository;
		this.messageSource = messageSource;
		this.mapper = mapper;
	}

	@Transactional
	public DeviceBinding saveOrUpdate(AddDeviceBindingRequest addDeviceBindingRequest) {
		UUID userId = addDeviceBindingRequest.userId();
		String deviceIdHash = addDeviceBindingRequest.deviceIdHash();
		String userAgentHash = addDeviceBindingRequest.userAgentHash();
		String ipHash = addDeviceBindingRequest.ipHash();
		String jtiHash = addDeviceBindingRequest.jtiHash();

		if(existsForDifferentUser(userId,deviceIdHash)) {
			throw new UnauthorizedDeviceException(
					messageSource.getMessage("error.authentication.device.binding.unauthorized.device",
							new Object[] {},
							Locale.getDefault()), DeviceBindingErrorCode.UNAUTHORIZED_DEVICE);}

		Optional<DeviceBinding> deviceBinding = findByUserIdAndDeviceIdHash(userId, deviceIdHash);
		if (deviceBinding.isEmpty()) {
			DeviceBinding mappedDeviceBindingToAdd = this.mapper.mapAddRequestToDeviceBinding(addDeviceBindingRequest);
			return this.repository.save(mappedDeviceBindingToAdd);
		}

		UpdateDeviceBindingRequest updateDeviceBindingRequest = new UpdateDeviceBindingRequest(userAgentHash, ipHash, jtiHash);
		DeviceBinding mappedDeviceBindingtoUpdate = this.mapper.mapUpdateRequestToDeviceBinding(updateDeviceBindingRequest, deviceBinding.get());
		return this.repository.save(mappedDeviceBindingtoUpdate);
	}

	private boolean existsForDifferentUser(UUID userId, String deviceIdHash) {
		return this.repository.findByDeviceIdHash(deviceIdHash)
				.filter(deviceBinding -> !deviceBinding.id().userId().equals(userId))
				.isPresent();
	}

	private Optional<DeviceBinding> findByUserIdAndDeviceIdHash(UUID userId, String deviceIdHash) {
		return this.repository.findByUserIdAndDeviceIdHash(userId,deviceIdHash);
	}

	public boolean existsByDeviceIdHash(String deviceId) {
		return this.repository.existsByDeviceIdHash(deviceId);
	}

	public boolean existsByUserAgentHash(String userAgentHash) {
		return this.repository.existsByUserAgentHash(userAgentHash);
	}

	public boolean existsByIpHash(String ipHash) {
		return this.repository.existsByIpHash(ipHash);
	}

	public boolean isReplay(String jtiHash) {
		return this.repository.isReplay(jtiHash);
	}

}

@Repository
interface DeviceBindingRepository extends ListCrudRepository<DeviceBinding, DeviceBindingId> {

	@Query("SELECT * FROM auth_device_binding WHERE user_id= :userId AND device_id_hash= :deviceIdHash")
	Optional<DeviceBinding> findByUserIdAndDeviceIdHash(@Param("userId") UUID userId, @Param("deviceIdHash") String deviceIdHash);

	@Query("SELECT * FROM auth_device_binding WHERE device_id_hash= :deviceIdHash")
	Optional<DeviceBinding> findByDeviceIdHash(@Param("deviceIdHash") String deviceIdHash);

	@Query("SELECT CASE WHEN COUNT(1) > 0 THEN TRUE ELSE FALSE END FROM auth_device_binding WHERE device_id_hash= :deviceIdHash")
	boolean existsByDeviceIdHash(@Param("deviceIdHash") String deviceIdHash);

	@Query("SELECT CASE WHEN COUNT(1) > 0 THEN TRUE ELSE FALSE END FROM auth_device_binding WHERE user_agent_hash= :userAgentHash")
	boolean existsByUserAgentHash(@Param("userAgentHash") String userAgentHash);

	@Query("SELECT CASE WHEN COUNT(1) > 0 THEN TRUE ELSE FALSE END FROM auth_device_binding WHERE ip_hash= :ipHash")
	boolean existsByIpHash(@Param("ipHash") String ipHash);

	@Query("SELECT CASE WHEN COUNT(1) > 0 THEN TRUE ELSE FALSE END FROM auth_device_binding WHERE jti_hash= :jtiHash")
	boolean isReplay(@Param("jtiHash") String jtiHash);
}

@Mapper(componentModel = "spring", imports = {LocalDateTime.class})
interface DeviceBindingMapper {

	@Mapping(target = "id", expression = "java(new DeviceBindingId(request.userId(),request.deviceIdHash()))")
	@Mapping(target = "version", ignore = true)
	@Mapping(target = "createdAt", expression = "java(LocalDateTime.now())")
	@Mapping(target = "lastUsedAt", expression = "java(LocalDateTime.now())")
	DeviceBinding mapAddRequestToDeviceBinding(AddDeviceBindingRequest request);

	@Mapping(target = "id", source = "deviceBinding.id")
	@Mapping(target = "version", source = "deviceBinding.version")
	@Mapping(target = "createdAt", source = "deviceBinding.createdAt")
	@Mapping(target = "lastUsedAt", expression = "java(LocalDateTime.now())")
	@Mapping(target = "userAgentHash", source = "request.userAgentHash")
	@Mapping(target = "ipHash", source = "request.ipHash")
	@Mapping(target = "jtiHash", source = "request.jtiHash")
	DeviceBinding mapUpdateRequestToDeviceBinding(UpdateDeviceBindingRequest request, DeviceBinding deviceBinding);

}


