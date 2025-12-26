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

import com.github.f4b6a3.uuid.UuidCreator;
import com.mhs.onlinemarketingplatform.authentication.dto.AddDeviceBindingRequest;
import com.mhs.onlinemarketingplatform.authentication.dto.UpdateDeviceBindingRequest;
import com.mhs.onlinemarketingplatform.authentication.model.DeviceBinding;
import com.mhs.onlinemarketingplatform.authentication.model.DeviceBindingId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
/**
 * @author Milad Haghighat Shahedi
 */
@ExtendWith(MockitoExtension.class)
public class DeviceBindingMapperUnitTest {

	private final DeviceBindingMapper deviceBindingMapper = Mappers.getMapper(DeviceBindingMapper.class);

	@Test
	void mapAddRequestToDeviceBinding_test() {
		UUID userId = UuidCreator.getTimeOrderedEpoch();
		String deviceIdHash = "deviceIdHash";
		String userAgentHash = "userAgentHash";
		String ipHash = "ipHash";
		String jtiHash = "jtiHash";
		// Arrange
		AddDeviceBindingRequest request = new AddDeviceBindingRequest(
				userId,
				deviceIdHash,
				userAgentHash,
				ipHash,
				jtiHash
		);
		// Act
		DeviceBinding result = this.deviceBindingMapper.mapAddRequestToDeviceBinding(request);
		// Assert
		assertNotNull(result);
		assertNotNull(result.id());
		assertNotNull(result.id().userId());
		assertNotNull(result.id().deviceIdHash());
		assertNotNull(result.createdAt());
		assertNotNull(result.lastUsedAt());
		assertNotNull(result.userAgentHash());
		assertNotNull(result.ipHash());
		assertNotNull(result.jtiHash());
		assertEquals(0,result.version());
	}

	@Test
	void mapUpdateRequestToDeviceBinding_test() {
		UUID userId = UuidCreator.getTimeOrderedEpoch();
		String deviceIdHash = "deviceIdHash";
		String userAgentHash = "userAgentHash";
		String ipHash = "ipHash";
		String jtiHash = "jtiHash";
		LocalDateTime createdAt = LocalDateTime.of(2025,1,1,12,0,0);
		LocalDateTime lastUsedAt = LocalDateTime.of(2025,5,5,12,0,0);

		DeviceBindingId id = new DeviceBindingId(userId,deviceIdHash);
		DeviceBinding oldDeviceBinding = new DeviceBinding(id,0,userAgentHash,ipHash,jtiHash, createdAt,lastUsedAt);

		String newUserAgentHash = "newUserAgentHash";
		String newIpHash = "newIpHash";
		String newJtiHash = "newJtiHash";
		// Arrange
		UpdateDeviceBindingRequest request = new UpdateDeviceBindingRequest(
				newUserAgentHash,
				newIpHash,
				newJtiHash
		);
		// Act
		DeviceBinding result = this.deviceBindingMapper.mapUpdateRequestToDeviceBinding(request, oldDeviceBinding);
		// Assert
		assertNotNull(result);
		assertNotNull(result.id());
		assertTrue(result.createdAt().isEqual(oldDeviceBinding.createdAt()));
		assertFalse(result.lastUsedAt().isEqual(oldDeviceBinding.lastUsedAt()));
        assertFalse(result.userAgentHash().equals(oldDeviceBinding.userAgentHash()));
		assertFalse(result.ipHash().equals(oldDeviceBinding.ipHash()));
		assertFalse(result.jtiHash().equals(oldDeviceBinding.jtiHash()));
	}

}
