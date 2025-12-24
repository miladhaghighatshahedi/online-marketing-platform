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
import com.mhs.onlinemarketingplatform.authentication.error.otp.OtpErrorCode;
import com.mhs.onlinemarketingplatform.authentication.error.otp.OtpRateLimitExceededException;
import com.mhs.onlinemarketingplatform.authentication.util.HashUtility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
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

	@Mock
	private SetOperations<String,String> setOperations;

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
		assertEquals(OtpErrorCode.OTP_COOLDOWN,exception.getCode());

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
		assertEquals(OtpErrorCode.OTP_RATELIMIT_EXCEEDED,exception.getCode());

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
		String blockKey = "OTP_BLOCKED_"+key;
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
		String blockKey = "OTP_BLOCKED_"+key;
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
		assertEquals(OtpErrorCode.OTP_BLOCKED,exception.getCode());

		verify(this.keyBuilder,times(1)).buildBlockKey(anyString());
		verify(this.redis,times(1)).hasKey(anyString());
	}

	@Test
	void recordVerifyAttempts_method_shouldRecordVerifyAttempts() {
		// Arrange
		String key = "09439562343";
		String verifyKey = "OTP_VERIFY_COUNT_"+key;
		when(this.keyBuilder.buildVerifyKey(key)).thenReturn(verifyKey);
		when(this.redis.opsForValue()).thenReturn(valueOperations);
		when(this.valueOperations.increment(verifyKey)).thenReturn(1L);
		when(this.otpRedisProperties.verifyTtlInSec()).thenReturn(3600);
		when(this.otpRateLimitProperties.maxVerifyAttemptsPerHour()).thenReturn(3);
		// Act
		this.otpRateLimiter.recordVerifyAttempts(key);
		// Assert
		verify(this.redis,times(1)).expire(eq(verifyKey),eq(Duration.ofSeconds(3600)));

		verify(this.keyBuilder,times(1)).buildVerifyKey(anyString());
		verify(this.redis,times(1)).opsForValue();
		verify(this.valueOperations,times(1)).increment(anyString());
		verify(this.otpRedisProperties,times(1)).verifyTtlInSec();
		verify(this.otpRateLimitProperties,times(1)).maxVerifyAttemptsPerHour();

		assertDoesNotThrow(() -> this.otpRateLimiter.recordVerifyAttempts(key));
	}

	@Test
	void recordVerifyAttempts_method_shouldNotResetExpire() {
		// Arrange
		String key = "09439562343";
		String verifyKey = "OTP_VERIFY_COUNT_"+key;
		when(this.keyBuilder.buildVerifyKey(key)).thenReturn(verifyKey);
		when(this.redis.opsForValue()).thenReturn(valueOperations);
		when(this.valueOperations.increment(verifyKey)).thenReturn(2L);

		when(this.otpRateLimitProperties.maxVerifyAttemptsPerHour()).thenReturn(3);
		// Act
		this.otpRateLimiter.recordVerifyAttempts(key);
		// Assert
		verify(this.redis,never()).expire(eq(verifyKey),eq(Duration.ofSeconds(3600)));
		verify(this.keyBuilder,times(1)).buildVerifyKey(anyString());
		verify(this.redis,times(1)).opsForValue();
		verify(this.valueOperations,times(1)).increment(anyString());
		verify(this.otpRedisProperties,never()).verifyTtlInSec();
		verify(this.otpRateLimitProperties,times(1)).maxVerifyAttemptsPerHour();

		assertDoesNotThrow(() -> this.otpRateLimiter.recordVerifyAttempts(key));
	}

	@Test
	void recordVerifyAttempts_method_shouldThrowOtpRateLimitExceededException() {
		// Arrange
		String key = "09439562343";
		String verifyKey = "OTP_VERIFY_COUNT_"+key;
		String blockKey = "OTP_BLOCKED_"+key;
		when(this.keyBuilder.buildVerifyKey(key)).thenReturn(verifyKey);
		when(this.redis.opsForValue()).thenReturn(valueOperations);
		when(this.valueOperations.increment(verifyKey)).thenReturn(4L);
		when(this.otpRateLimitProperties.maxVerifyAttemptsPerHour()).thenReturn(3);

		when(this.keyBuilder.buildBlockKey(key)).thenReturn(blockKey);

		when(this.messageSource.getMessage(
				eq("error.otp.code.too.many.failed.attempts"),
				eq(new Object[] {}),
				any(Locale.class))).thenReturn("Otp request too many failed attempts [Temporarily blocked].");
		// Act
		OtpRateLimitExceededException exception = assertThrows(OtpRateLimitExceededException.class,
				()-> this.otpRateLimiter.recordVerifyAttempts(key));
		// Assert
		assertNotNull(exception);
		assertEquals("Otp request too many failed attempts [Temporarily blocked].",exception.getMessage());
		assertEquals(OtpErrorCode.OTP_RATELIMIT_EXCEEDED,exception.getCode());

		verify(this.redis,never()).expire(eq(verifyKey),eq(Duration.ofSeconds(3600)));
		verify(this.keyBuilder,times(1)).buildVerifyKey(anyString());
		verify(this.redis,atLeastOnce()).opsForValue();
		verify(this.valueOperations,times(1)).increment(anyString());
		verify(this.otpRedisProperties,never()).verifyTtlInSec();
		verify(this.otpRateLimitProperties,times(1)).maxVerifyAttemptsPerHour();

		verify(this.valueOperations).setIfAbsent(contains("OTP_BLOCKED_"),any(),any(Duration.class));

	}

	@Test
	void recordFailure_method_shouldRecordFailure() {
		// Arrange
		String key = "09439562343";
		String failureKey = "OTP_FAILURE_"+key;
		when(this.keyBuilder.buildFailureKey(key)).thenReturn(failureKey);
		when(this.redis.opsForValue()).thenReturn(valueOperations);
		when(this.valueOperations.increment(failureKey)).thenReturn(1L);
		when(this.otpRedisProperties.failureTtlInSec()).thenReturn(3600);
		when(this.otpRateLimitProperties.maxFailedAttemptsPerHour()).thenReturn(3);
		// Act
		this.otpRateLimiter.recordFailure(key);
		// Assert
		verify(this.keyBuilder,times(1)).buildFailureKey(anyString());
		verify(this.redis,times(1)).opsForValue();
		verify(this.valueOperations,times(1)).increment(anyString());
		verify(this.otpRedisProperties,times(1)).failureTtlInSec();
		verify(this.redis,times(1)).expire(failureKey,Duration.ofSeconds(3600));
		verify(this.otpRateLimitProperties,times(1)).maxFailedAttemptsPerHour();

		assertDoesNotThrow(() -> this.otpRateLimiter.recordFailure(key));
	}

	@Test
	void recordFailure_method_shouldNotResetExpire() {
		// Arrange
		String key = "09439562343";
		String failureKey = "OTP_FAILURE_"+key;
		when(this.keyBuilder.buildFailureKey(key)).thenReturn(failureKey);
		when(this.redis.opsForValue()).thenReturn(valueOperations);
		when(this.valueOperations.increment(failureKey)).thenReturn(2L);
		when(this.otpRateLimitProperties.maxFailedAttemptsPerHour()).thenReturn(3);
		// Act
		this.otpRateLimiter.recordFailure(key);
		// Assert
		verify(this.redis,never()).expire(failureKey,Duration.ofSeconds(3600));

		verify(this.keyBuilder,times(1)).buildFailureKey(anyString());
		verify(this.redis,times(1)).opsForValue();
		verify(this.valueOperations,times(1)).increment(anyString());
		verify(this.otpRedisProperties,never()).failureTtlInSec();
		verify(this.otpRateLimitProperties,times(1)).maxFailedAttemptsPerHour();

		assertDoesNotThrow(() -> this.otpRateLimiter.recordFailure(key));
	}

	@Test
	void recordFailure_method_shouldBlock() {
		// Arrange
		String key = "09439562343";
		String failureKey = "OTP_FAILURE_"+key;
		String blockedKey = "OTP_BLOCKED_"+key;
		when(this.keyBuilder.buildFailureKey(key)).thenReturn(failureKey);
		when(this.redis.opsForValue()).thenReturn(valueOperations);
		when(this.valueOperations.increment(failureKey)).thenReturn(4L);
		when(this.otpRateLimitProperties.maxFailedAttemptsPerHour()).thenReturn(3);
		when(this.keyBuilder.buildBlockKey(key)).thenReturn(blockedKey);
		// Act
		this.otpRateLimiter.recordFailure(key);
		// Assert
		verify(this.valueOperations).setIfAbsent(contains("OTP_BLOCKED_"),any(),any(Duration.class));

		verify(this.redis,never()).expire(failureKey,Duration.ofSeconds(3600));
		verify(this.keyBuilder,times(1)).buildFailureKey(anyString());
		verify(this.redis,atLeastOnce()).opsForValue();
		verify(this.valueOperations,times(1)).increment(anyString());
		verify(this.otpRedisProperties,never()).failureTtlInSec();
		verify(this.otpRateLimitProperties,times(1)).maxFailedAttemptsPerHour();

		assertDoesNotThrow(() -> this.otpRateLimiter.recordFailure(key));
	}

	@Test
	void recordSuccess_shouldClearAllOtpRelatedKeys() {
		// Arrange
		String key = "09439562343";
		String verifyKey = "OTP_VERIFY_COUNT_" + key;
		String failureKey = "OTP_FAILURE_" + key;
		String blockKey = "OTP_BLOCKED_" + key;
		String sendKey = "OTP_SEND_COUNT_" + key;
		String cooldownKey = "OTP_SEND_COOLDOWN_" + key;

		when(keyBuilder.buildVerifyKey(key)).thenReturn(verifyKey);
		when(keyBuilder.buildFailureKey(key)).thenReturn(failureKey);
		when(keyBuilder.buildBlockKey(key)).thenReturn(blockKey);
		when(keyBuilder.buildSendKey(key)).thenReturn(sendKey);
		when(keyBuilder.buildSendCoolDownKey(key)).thenReturn(cooldownKey);
		// Act
		otpRateLimiter.recordSuccess(key);
		// Assert
		verify(redis).delete(verifyKey);
		verify(redis).delete(failureKey);
		verify(redis).delete(blockKey);
		verify(redis).delete(sendKey);
		verify(redis).delete(cooldownKey);
	}

	@Test
	void validateCardinality() {
		// Arrange
		String ip = "192.168.1.1";
		String cardinalityKey = "OTP_CARDINALITY_" + ip;

		String mobileNumber = "09439562343";
		String hash = "b74c0a2633e50768ef2d3fa2cceabf377b41c61566f9a1a95b17b8103890e01d";

		when(this.keyBuilder.buildCardinalityKey(ip)).thenReturn(cardinalityKey);
		when(this.hashUtility.sha256Base64(mobileNumber)).thenReturn(hash);
		when(this.redis.opsForSet()).thenReturn(setOperations);
		when(this.setOperations.add(cardinalityKey,hash)).thenReturn(1L);
		when(this.redis.getExpire(cardinalityKey)).thenReturn(null);
		when(this.otpRedisProperties.cardinalityInSec()).thenReturn(3600);
		when(this.setOperations.size(cardinalityKey)).thenReturn(1L);
		when(this.otpRateLimitProperties.maxSendAttemptsPerIp()).thenReturn(10);
		// Act
		this.otpRateLimiter.validateCardinality(mobileNumber,ip);
		// Assert
		verify(this.keyBuilder,times(1)).buildCardinalityKey(anyString());
		verify(this.hashUtility,times(1)).sha256Base64(anyString());
		verify(this.redis,atLeastOnce()).opsForSet();
		verify(this.setOperations,times(1)).add(anyString(),anyString());
		verify(this.redis,times(1)).getExpire(anyString());
		verify(this.otpRedisProperties,times(1)).cardinalityInSec();
		verify(this.setOperations,times(1)).size(anyString());
		verify(this.otpRateLimitProperties,times(1)).maxSendAttemptsPerIp();

		assertDoesNotThrow(() -> this.otpRateLimiter.validateCardinality(mobileNumber,ip));
	}

	@Test
	void validateCardinality_method_shouldNotCallGetExpire_WhenAddedCardinalityIsNullOrOne() {
		// Arrange
		String ip = "192.168.1.1";
		String cardinalityKey = "OTP_CARDINALITY_" + ip;

		String mobileNumber = "09439562343";
		String hash = "b74c0a2633e50768ef2d3fa2cceabf377b41c61566f9a1a95b17b8103890e01d";

		when(this.keyBuilder.buildCardinalityKey(ip)).thenReturn(cardinalityKey);
		when(this.hashUtility.sha256Base64(mobileNumber)).thenReturn(hash);
		when(this.redis.opsForSet()).thenReturn(setOperations);
		when(this.setOperations.add(cardinalityKey,hash)).thenReturn(2L);

		when(this.setOperations.size(cardinalityKey)).thenReturn(1L);
		when(this.otpRateLimitProperties.maxSendAttemptsPerIp()).thenReturn(10);
        // Act
		this.otpRateLimiter.validateCardinality(mobileNumber,ip);
		//Assert
		verify(this.redis,never()).getExpire(anyString());
		verify(this.redis,never()).expire(anyString(),any(Duration.class));
		verify(this.otpRedisProperties,never()).cardinalityInSec();

		verify(this.keyBuilder,times(1)).buildCardinalityKey(anyString());
		verify(this.hashUtility,times(1)).sha256Base64(anyString());
		verify(this.redis,atLeastOnce()).opsForSet();
		verify(this.setOperations,times(1)).add(anyString(),anyString());
		verify(this.setOperations,times(1)).size(anyString());
		verify(this.otpRateLimitProperties,times(1)).maxSendAttemptsPerIp();

		assertDoesNotThrow(() -> this.otpRateLimiter.validateCardinality(mobileNumber,ip));
	}

	@Test
	void validateCardinality_method_shouldNotCallGetExpire_whenExpiryIsNotNullOrIsNotMinusOne() {
		// Arrange
		// Arrange
		String ip = "192.168.1.1";
		String cardinalityKey = "OTP_CARDINALITY_" + ip;

		String mobileNumber = "09439562343";
		String hash = "b74c0a2633e50768ef2d3fa2cceabf377b41c61566f9a1a95b17b8103890e01d";

		when(this.keyBuilder.buildCardinalityKey(ip)).thenReturn(cardinalityKey);
		when(this.hashUtility.sha256Base64(mobileNumber)).thenReturn(hash);
		when(this.redis.opsForSet()).thenReturn(setOperations);
		when(this.setOperations.add(cardinalityKey,hash)).thenReturn(1L);
		when(this.redis.getExpire(cardinalityKey)).thenReturn(1L);

		when(this.setOperations.size(cardinalityKey)).thenReturn(9L);
		when(this.otpRateLimitProperties.maxSendAttemptsPerIp()).thenReturn(10);
        // Act
		this.otpRateLimiter.validateCardinality(mobileNumber,ip);
		// Assert
		verify(this.redis,never()).expire(anyString(),any(Duration.class));
		verify(this.otpRedisProperties,never()).cardinalityInSec();

		verify(this.keyBuilder,times(1)).buildCardinalityKey(anyString());
		verify(this.hashUtility,times(1)).sha256Base64(anyString());
		verify(this.redis,atLeastOnce()).opsForSet();
		verify(this.setOperations,times(1)).add(anyString(),anyString());
		verify(this.redis,times(1)).getExpire(anyString());

		verify(this.setOperations,times(1)).size(anyString());
		verify(this.otpRateLimitProperties,times(1)).maxSendAttemptsPerIp();

		assertDoesNotThrow(() -> this.otpRateLimiter.validateCardinality(mobileNumber,ip));

	}

	@Test
	void validateCardinality_method_shouldThrowOtpRateLimitExceededException() {
		// Arrange
		String ip = "192.168.1.1";
		String cardinalityKey = "OTP_CARDINALITY_" + ip;

		String mobileNumber = "09439562343";
		String hash = "b74c0a2633e50768ef2d3fa2cceabf377b41c61566f9a1a95b17b8103890e01d";

		when(this.keyBuilder.buildCardinalityKey(ip)).thenReturn(cardinalityKey);
		when(this.hashUtility.sha256Base64(mobileNumber)).thenReturn(hash);
		when(this.redis.opsForSet()).thenReturn(setOperations);
		when(this.setOperations.add(cardinalityKey,hash)).thenReturn(1L);
		when(this.redis.getExpire(cardinalityKey)).thenReturn(-1L);
		when(this.otpRedisProperties.cardinalityInSec()).thenReturn(3600);
		when(this.setOperations.size(cardinalityKey)).thenReturn(11L);
		when(this.otpRateLimitProperties.maxSendAttemptsPerIp()).thenReturn(10);
		when(this.messageSource.getMessage(
				eq("error.otp.code.too.many.distinct.requets.from.same.ip"),
				eq(new Object[] {}),
				any(Locale.class))).thenReturn("Otp rquest too many attemps from same ip.");
		// Act
		OtpRateLimitExceededException exception = assertThrows(OtpRateLimitExceededException.class,
				() -> this.otpRateLimiter.validateCardinality(mobileNumber,ip));

		// Assert
		assertNotNull(exception);
		assertEquals("Otp rquest too many attemps from same ip.",exception.getMessage());
		assertEquals(OtpErrorCode.OTP_RATELIMIT_EXCEEDED,exception.getCode());

		verify(this.keyBuilder,times(1)).buildCardinalityKey(anyString());
		verify(this.hashUtility,times(1)).sha256Base64(anyString());
		verify(this.redis,atLeastOnce()).opsForSet();
		verify(this.setOperations,times(1)).add(anyString(),anyString());
		verify(this.redis,times(1)).getExpire(anyString());
		verify(this.otpRedisProperties,times(1)).cardinalityInSec();
		verify(this.setOperations,times(1)).size(anyString());
		verify(this.otpRateLimitProperties,times(1)).maxSendAttemptsPerIp();

	}

	@ParameterizedTest
	@CsvSource({
			"1,6,false",
			"2,6,false",
			"3,6,false",
			"4,6,false",
			"5,6,false",
			"6,6,false",
			"7,6,true",
			"8,6,true",
	})
	void recordVerifyAttempts_parametrizedYest(long attempts,int maxAttempts,boolean shouldBlock) {
		// Arrange
		String key = "09439562343";
		String verifyKey = "OTP_VERIFY_COUNT_" + key;
		String blockKey = "OTP_BLOCKED_" + key;

		when(this.keyBuilder.buildVerifyKey(key)).thenReturn(verifyKey);

		when(this.redis.opsForValue()).thenReturn(valueOperations);
		when(this.valueOperations.increment(verifyKey)).thenReturn(attempts);
		when(this.otpRateLimitProperties.maxVerifyAttemptsPerHour()).thenReturn(maxAttempts);

        // Act
		if(shouldBlock) {
			when(this.keyBuilder.buildBlockKey(key)).thenReturn(blockKey);
			when(this.otpRateLimitProperties.blockDurationInSec()).thenReturn(3600);
			assertThrows(OtpRateLimitExceededException.class,
					() -> this.otpRateLimiter.recordVerifyAttempts(key));
		} else {
			assertDoesNotThrow(() -> this.otpRateLimiter.recordVerifyAttempts(key));
		}
		// Assert
		if (shouldBlock) {
			verify(this.valueOperations).setIfAbsent(
					eq(blockKey),
					eq("1"),
					eq(Duration.ofSeconds(3600))
			);
		} else {
			verify(this.valueOperations, never()).setIfAbsent(
					anyString(),
					anyString(),
					any()
			);
		}
	}

}
