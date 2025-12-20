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

import com.mhs.onlinemarketingplatform.authentication.error.otp.OtpBlockedException;
import com.mhs.onlinemarketingplatform.authentication.error.otp.OtpRateLimitExceededException;
import com.mhs.onlinemarketingplatform.authentication.props.OtpRateLimitProperties;
import com.mhs.onlinemarketingplatform.authentication.props.OtpRedisProperties;
import com.mhs.onlinemarketingplatform.authentication.util.HashUtility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
/**
 * @author Milad Haghighat Shahedi
 */
@ExtendWith(MockitoExtension.class)
public class OtpRateLimiterUnitTest {

	@Mock
	private OtpKeyBuilder keyBuilder;

	@Mock
	private OtpRateLimitProperties otpRateLimitProperties;

	@Mock
	private OtpRedisProperties otpRedisProperties;

	@Mock
	private HashUtility hashUtility;

	@Mock
	private MessageSource messageSource;

	@Mock
    private RedisTemplate<String,String> redis;

    @Mock
    private ValueOperations<String,String> valueOperations;

	@InjectMocks
	private RedisRateLimiter otpRateLimiter ;

	@Test
	void validateSendCoolDown_method_shouldValidateSendCoolDown() {
		// Arrange
		String key = "09439562343";
		String coolDownKey = "OTP_SEND_COOLDOWN_"+key;
		when(this.keyBuilder.buildSendCoolDownKey(key)).thenReturn(coolDownKey);
		when(this.redis.hasKey(coolDownKey)).thenReturn(Boolean.FALSE);
		// Act
		this.otpRateLimiter.validateSendCoolDown(key);
		// Assert
		verify(this.keyBuilder,times(1)).buildSendCoolDownKey(anyString());
		verify(this.redis,times(1)).hasKey(anyString());
		assertDoesNotThrow(() -> this.otpRateLimiter.validateSendCoolDown(key));
	}

	@Test
	void validateSendCoolDown_method_shouldThrowOtpRateLimitExceededException() {
		// Arrange
		String key = "09439562343";
		String coolDownKey = "OTP_SEND_COOLDOWN_"+key;
		when(this.keyBuilder.buildSendCoolDownKey(key)).thenReturn(coolDownKey);
		when(this.redis.hasKey(coolDownKey)).thenReturn(Boolean.TRUE);
		when(this.otpRedisProperties.coolDownTtlInSec()).thenReturn(60);
		when(this.messageSource.getMessage(
				eq("error.otp.code.cooldown.too.many.attempts"),
				any(),
		        any(Locale.class))).thenReturn("Otp cooldown, Please waint for 60 seconds.");
		// Act
		OtpRateLimitExceededException exception = assertThrows(OtpRateLimitExceededException.class,
				() -> this.otpRateLimiter.validateSendCoolDown(key));
		// Assert
		assertNotNull(exception);
		assertEquals("Otp cooldown, Please waint for 60 seconds.",exception.getMessage());

		verify(this.keyBuilder,times(1)).buildSendCoolDownKey(anyString());
		verify(this.redis,times(1)).hasKey(anyString());
	}

	@Test
	void validateCanSend_method_shouldValidateCanSend() {
		// Arrange
		String key = "09439562343";
		String sendKey = "OTP_SEND_COUNT_"+key;
		when(this.keyBuilder.buildSendKey(key)).thenReturn(sendKey);
		when(this.redis.opsForValue()).thenReturn(valueOperations);
		when(this.valueOperations.increment(sendKey)).thenReturn(1L);
		when(this.otpRedisProperties.sendTtlInSec()).thenReturn(3600);
		when(this.otpRateLimitProperties.maxSendAttemptsPerHour()).thenReturn(10);
		// Act
		this.otpRateLimiter.validateCanSend(key);
		// Asser
		verify(this.keyBuilder,times(1)).buildSendKey(anyString());
        verify(this.redis.opsForValue(),times(1)).increment(anyString());
		verify(this.otpRedisProperties,times(1)).sendTtlInSec();
		verify(this.otpRateLimitProperties,times(1)).maxSendAttemptsPerHour();

		assertDoesNotThrow(() -> this.otpRateLimiter.validateCanSend(key));
	}

	@Test
	void validateCanSend_method_shouldValidateCanSend_shouldNotCallExpire() {
		// Arrange
		String key = "09439562343";
		String sendKey = "OTP_SEND_COUNT_"+key;
		when(this.keyBuilder.buildSendKey(key)).thenReturn(sendKey);
		when(this.redis.opsForValue()).thenReturn(valueOperations);
		when(this.valueOperations.increment(sendKey)).thenReturn(3L);
		when(this.otpRateLimitProperties.maxSendAttemptsPerHour()).thenReturn(10);
		// Act
		this.otpRateLimiter.validateCanSend(key);
		// Asser
		verify(this.redis,never()).expire(anyString(),any());

		verify(this.keyBuilder,times(1)).buildSendKey(anyString());
		verify(this.redis.opsForValue(),times(1)).increment(anyString());
		verify(this.otpRedisProperties,never()).sendTtlInSec();
		verify(this.otpRateLimitProperties,times(1)).maxSendAttemptsPerHour();

		assertDoesNotThrow(() -> this.otpRateLimiter.validateCanSend(key));
	}

	@Test
	void validateCanSend_method_shouldThrowOtpRateLimitExceededException() {
		// Arrange
		String key = "09439562343";
		String sendKey = "OTP_SEND_COUNT_"+key;
		when(this.keyBuilder.buildSendKey(key)).thenReturn(sendKey);
		when(this.redis.opsForValue()).thenReturn(valueOperations);
		when(this.valueOperations.increment(sendKey)).thenReturn(11L);
		when(this.otpRateLimitProperties.maxSendAttemptsPerHour()).thenReturn(10);
		// Act
		when(this.messageSource.getMessage(
				eq("error.otp.code.too.many.failed.attempts"),
				any(),
				any(Locale.class))).thenReturn("Otp request too many failed attempts [Temporarily blocked].");
		// Act
		OtpRateLimitExceededException exception = assertThrows(OtpRateLimitExceededException.class,
				() -> this.otpRateLimiter.validateCanSend(key));
		// Asser
		assertNotNull(exception);
		assertEquals("Otp request too many failed attempts [Temporarily blocked].",exception.getMessage());

		verify(this.keyBuilder,times(1)).buildSendKey(anyString());
		verify(this.redis.opsForValue(),times(1)).increment(anyString());
		verify(this.otpRedisProperties,never()).sendTtlInSec();
		verify(this.otpRateLimitProperties,times(1)).maxSendAttemptsPerHour();
	}

	@Test
	void recordSendAttempts_shouldRecordSendAttempts() {
		// Arrange
		String key = "09439562343";
		String coolDownKey = "OTP_SEND_COOLDOWN_"+key;
		when(this.keyBuilder.buildSendCoolDownKey(key)).thenReturn(coolDownKey);
		when(this.redis.opsForValue()).thenReturn(valueOperations);
		when(this.otpRedisProperties.coolDownTtlInSec()).thenReturn(60);
		// Act
		this.otpRateLimiter.recordSendAttempts(key);
		// Assert
		verify(this.keyBuilder,times(1)).buildSendCoolDownKey(anyString());
		verify(this.otpRedisProperties,times(1)).coolDownTtlInSec();
		verify(this.valueOperations).set(coolDownKey,"1",Duration.ofSeconds(60));

		assertDoesNotThrow(() -> this.otpRateLimiter.validateCanSend(key));
	}

	@Test
	void validateNotBlocked_method() {
		// Arrange
		String key = "09439562343";
		String blockKey = "OTP_BLOCKED__"+key;
		when(this.keyBuilder.buildBlockKey(key)).thenReturn(blockKey);
		when(this.redis.hasKey(blockKey)).thenReturn(Boolean.FALSE);
		// Act
		this.otpRateLimiter.validateNotBlocked(key);
		// Assert
		verify(this.keyBuilder,times(1)).buildBlockKey(anyString());
		verify(this.redis,times(1)).hasKey(anyString());

		assertDoesNotThrow(() -> this.otpRateLimiter.validateNotBlocked(key));
	}

	@Test
	void validateNotBlocked_method_shouldThrowOtpBlockedException() {
		// Arrange
		String key = "09439562343";
		String blockKey = "OTP_BLOCKED__"+key;
		when(this.keyBuilder.buildBlockKey(key)).thenReturn(blockKey);
		when(this.redis.hasKey(blockKey)).thenReturn(Boolean.TRUE);
		// Act
		when(this.messageSource.getMessage(
				eq("error.otp.code.blocked"),
				any(),
				any(Locale.class))).thenReturn("Otp request blocked [Temporarily blocked].");
		// Act
		OtpBlockedException exception = assertThrows(OtpBlockedException.class,
				() -> this.otpRateLimiter.validateNotBlocked(key));
		// Assert
		assertNotNull(exception);
		assertEquals("Otp request blocked [Temporarily blocked].",exception.getMessage());

		verify(this.keyBuilder,times(1)).buildBlockKey(anyString());
		verify(this.redis,times(1)).hasKey(anyString());
	}

}
