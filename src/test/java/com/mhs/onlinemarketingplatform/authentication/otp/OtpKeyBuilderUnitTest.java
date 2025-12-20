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
package com.mhs.onlinemarketingplatform.authentication.otp;

import com.mhs.onlinemarketingplatform.authentication.error.otp.OtpConfigurationException;
import com.mhs.onlinemarketingplatform.authentication.props.OtpRedisProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.AssertionsKt.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
/**
 * @author Milad Haghighat Shahedi
 */
@ExtendWith(MockitoExtension.class)
public class OtpKeyBuilderUnitTest {

	@InjectMocks
	private OtpKeyBuilder keyBuilder;

	@Mock
	private OtpRedisProperties otpRedisProperties;

	@Mock
	private MessageSource messageSource;

	@Test
	void buildOtpKey_method_shouldBuildOtpKey() {
		String key = "04323452343";
		// Arrange
		when(this.otpRedisProperties.prefixKey()).thenReturn("OTP_KEY_");
		// Act
		String result = this.keyBuilder.buildOtpKey(key);
		// Assert
		assertNotNull(result);
		assertEquals("OTP_KEY_04323452343",result);
	}

	@Test
	void buildSendCoolDownKey_method_shouldBuildSendkey() {
		String key = "04323452343";
		// Arrange
		when(this.otpRedisProperties.sendCoolDownPrefixKey()).thenReturn("OTP_SEND_COOLDOWN_");
		// Act
		String result = this.keyBuilder.buildSendCoolDownKey(key);
		// Assert
		assertNotNull(result);
		assertEquals("OTP_SEND_COOLDOWN_04323452343",result);
	}

	@Test
	void buildSendKey_method_shouldBuildSendkey() {
		String key = "04323452343";
		// Arrange
		 when(this.otpRedisProperties.sendCountPrefixKey()).thenReturn("OTP_SEND_COUNT_");
		// Act
		String result = this.keyBuilder.buildSendKey(key);
		// Assert
		assertNotNull(result);
		assertEquals("OTP_SEND_COUNT_04323452343",result);
	}

	@Test
	void buildVerifyKey_method_shouldBuildSendkey() {
		String key = "04323452343";
		// Arrange
		when(this.otpRedisProperties.verifyCountPrefixKey()).thenReturn("OTP_VERIFY_COUNT_");
		// Act
		String result = this.keyBuilder.buildVerifyKey(key);
		// Assert
		assertNotNull(result);
		assertEquals("OTP_VERIFY_COUNT_04323452343",result);
	}

	@Test
	void buildFailureKey_method_shouldBuildSendkey() {
		String key = "04323452343";
		// Arrange
		when(this.otpRedisProperties.failurePrefixKey()).thenReturn("OTP_FAILURE_");
		// Act
		String result = this.keyBuilder.buildFailureKey(key);
		// Assert
		assertNotNull(result);
		assertEquals("OTP_FAILURE_04323452343",result);
	}

	@Test
	void buildBlockKey_method_shouldBuildSendkey() {
		String key = "04323452343";
		// Arrange
		when(this.otpRedisProperties.blockPrefixKey()).thenReturn("OTP_BLOCKED_");
		// Act
		String result = this.keyBuilder.buildBlockKey(key);
		// Assert
		assertNotNull(result);
		assertEquals("OTP_BLOCKED_04323452343",result);
	}




	@Test
	void buildSendKey_method_shouldThrowOtpConfigurationException_WhenKeyIdIsNull() {
		// Arrange
		when(this.messageSource.getMessage(
				eq("error.otp.validation.key.missing.property"),
				eq(new Object[] {}),
				any(Locale.class))).thenReturn("Otp key should not be null or blank");
		// Act
		OtpConfigurationException exception = assertThrows(OtpConfigurationException.class,
				()-> this.keyBuilder.buildSendKey(null));
		// Assert
		assertNotNull(exception);
		assertEquals("Otp key should not be null or blank",exception.getMessage());

	}

	@Test
	void buildSendKey_method_shouldThrowOtpConfigurationException_WhenKeyIdIsEmpty() {
		// Arrange
		when(this.messageSource.getMessage(
				eq("error.otp.validation.key.missing.property"),
				eq(new Object[] {}),
				any(Locale.class))).thenReturn("Otp key should not be null or blank");
		// Act
		OtpConfigurationException exception = assertThrows(OtpConfigurationException.class,
				()-> this.keyBuilder.buildSendKey(" "));
		// Assert
		assertNotNull(exception);
		assertEquals("Otp key should not be null or blank",exception.getMessage());

	}


}
