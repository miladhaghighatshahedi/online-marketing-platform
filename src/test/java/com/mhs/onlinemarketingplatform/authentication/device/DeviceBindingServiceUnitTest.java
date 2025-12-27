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
import com.mhs.onlinemarketingplatform.authentication.error.devicebinding.DeviceBindingErrorCode;
import com.mhs.onlinemarketingplatform.authentication.error.devicebinding.UnauthorizedDeviceException;
import com.mhs.onlinemarketingplatform.authentication.model.DeviceBinding;
import com.mhs.onlinemarketingplatform.authentication.model.DeviceBindingId;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.AssertionsKt.assertNotNull;
import static org.mockito.Mockito.*;
/**
 * @author Milad Haghighat Shahedi
 */
@ExtendWith(MockitoExtension.class)
public class DeviceBindingServiceUnitTest {

	@Mock
	private DeviceBindingRepository deviceBindingRepository;

	@Mock
	private DeviceBindingMapper deviceBindingMapper;

	@Mock
	private MessageSource messageSource;

	@Spy
	@InjectMocks
	private DeviceBindingService deviceBindingService;

	@Test
    void saveOrUpdate_method_shouldSave_WhenDeviceBindingIsEmpty() {
		// Arrange
		UUID userId = UuidCreator.getTimeOrderedEpoch();
		AddDeviceBindingRequest request = new AddDeviceBindingRequest(userId, "deviceIdHash", "userAgentHash", "ipHash", "jtiHash");

		DeviceBindingId id = new DeviceBindingId(userId,"deviceIdHash");
		DeviceBinding mappedDeviceBinding = new DeviceBinding(id, 0, "userAgentHash", "ipHash", "jtiHash", LocalDateTime.now(), LocalDateTime.now());
		DeviceBinding savedDeviceBinding = new DeviceBinding(id, 0, "userAgentHash", "ipHash", "jtiHash", LocalDateTime.now(), LocalDateTime.now());

		when(this.deviceBindingRepository.findByDeviceIdHash("deviceIdHash")).thenReturn(Optional.empty());
		when(this.deviceBindingRepository.findByUserIdAndDeviceIdHash(userId,"deviceIdHash")).thenReturn(Optional.empty());

		when(this.deviceBindingMapper.mapAddRequestToDeviceBinding(request)).thenReturn(mappedDeviceBinding);
		when(this.deviceBindingRepository.save(mappedDeviceBinding)).thenReturn(savedDeviceBinding);
		// Act
		DeviceBinding result = this.deviceBindingService.saveOrUpdate(request);
		// Assert
		assertNotNull(result);
		assertEquals(0,result.version());

		verify(this.deviceBindingMapper,times(1)).mapAddRequestToDeviceBinding(any());
		verify(this.deviceBindingRepository,times(1)).save(any());

		verify(this.deviceBindingMapper,never()).mapUpdateRequestToDeviceBinding(any(),any());
	}

	@Test
	void saveOrUpdate_method_shouldUpdate_WhenDeviceBindingIsNotEmpty() {
		// Arrange
		UUID userId = UuidCreator.getTimeOrderedEpoch();
		LocalDateTime createdAt = LocalDateTime.of(2025,1,1,12,0,0);
		LocalDateTime lastUsedAt = LocalDateTime.of(2025,1,1,12,1,1);
		AddDeviceBindingRequest addRequest = new AddDeviceBindingRequest(userId, "deviceIdHash", "userAgentHash", "ipHash", "jtiHash");
		UpdateDeviceBindingRequest updateRequest = new UpdateDeviceBindingRequest("userAgentHash", "ipHash", "jtiHash");

		DeviceBindingId id = new DeviceBindingId(userId,"deviceIdHash");
		DeviceBinding existingDeviceBinding = new DeviceBinding(id, 0, "userAgentHash", "ipHash", "jtiHash", createdAt, lastUsedAt);
		DeviceBinding mappedDeviceBindingForUpdate = new DeviceBinding(id, 0, "userAgentHash", "ipHash", "jtiHash", createdAt, LocalDateTime.now());
		DeviceBinding updatedDeviceBinding = new DeviceBinding(id, 0, "userAgentHash", "ipHash", "jtiHash", createdAt, LocalDateTime.now());

		when(this.deviceBindingRepository.findByDeviceIdHash("deviceIdHash")).thenReturn(Optional.empty());
		when(this.deviceBindingRepository.findByUserIdAndDeviceIdHash(userId,"deviceIdHash")).thenReturn(Optional.of(existingDeviceBinding));

		when(this.deviceBindingMapper.mapUpdateRequestToDeviceBinding(updateRequest,existingDeviceBinding)).thenReturn(mappedDeviceBindingForUpdate);
		when(this.deviceBindingRepository.save(mappedDeviceBindingForUpdate)).thenReturn(updatedDeviceBinding);
		// Act
		DeviceBinding result = this.deviceBindingService.saveOrUpdate(addRequest);
        // Assert
		assertNotNull(result);
		assertTrue(createdAt.isEqual(result.createdAt()));
		assertTrue(lastUsedAt.isBefore(result.lastUsedAt()));

		verify(this.deviceBindingRepository,times(1)).save(any());
		verify(this.deviceBindingMapper,never()).mapAddRequestToDeviceBinding(any());
		verify(this.deviceBindingMapper,times(1)).mapUpdateRequestToDeviceBinding(any(),any());
	}

	@Test
	void saveOrUpdate_method_shouldThrowUnauthorizedDeviceException() {
		// Arrange
		UUID currentUserId = UuidCreator.getTimeOrderedEpoch();
		UUID otherUserId = UuidCreator.getTimeOrderedEpoch();
		String deviceIdHash = "deviceIdHash";
		DeviceBindingId otherUserDeviceBindingId = new DeviceBindingId(otherUserId,deviceIdHash);

		AddDeviceBindingRequest request = new AddDeviceBindingRequest(currentUserId, deviceIdHash, "userAgentHash", "ipHash", "jtiHash");
		DeviceBinding existingDeviceBinding = new DeviceBinding(otherUserDeviceBindingId, 0, deviceIdHash, "ipHash", "jtiHash", LocalDateTime.now(), LocalDateTime.now());

		when(this.deviceBindingRepository.findByDeviceIdHash("deviceIdHash")).thenReturn(Optional.of(existingDeviceBinding));

		when(this.messageSource.getMessage(
				eq("error.otp.validation.device.unauthorized.for.current.user"),
				eq(new Object[] {}),
				any(Locale.class)
		)).thenReturn("Unauthorized device for the current user");
		// Act
		UnauthorizedDeviceException exception = assertThrows(UnauthorizedDeviceException.class,
				() -> this.deviceBindingService.saveOrUpdate(request));
		// Assert
		assertNotNull(exception);
		assertEquals("Unauthorized device for the current user",exception.getMessage());
		assertEquals(DeviceBindingErrorCode.UNAUTHORIZED_DEVICE,exception.getCode());

		verify(this.deviceBindingMapper, never()).mapAddRequestToDeviceBinding(any());
		verify(this.deviceBindingMapper, never()).mapUpdateRequestToDeviceBinding(any(), any());
		verify(this.deviceBindingRepository, never()).save(any());
	}
	
}
