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

import com.mhs.onlinemarketingplatform.authentication.error.otp.*;
import com.mhs.onlinemarketingplatform.authentication.sms.SmsSender;
import com.mhs.onlinemarketingplatform.authentication.util.HashUtility;
import com.mhs.onlinemarketingplatform.authentication.validation.OtpValidator;
import org.springframework.context.MessageSource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
/**
 * @author Milad Haghighat Shahedi
 */
@Service
public class OtpService {

	private final RedisOtpStore redisOtpStore;
	private final SmsSender otpSmsSender;
	private final RedisRateLimiter redisRateLimiter;
	private final NumericOtpGenerator numericOtpGenerator;
	private final OtpCoreProperties otpCoreProperties;
	private final HashUtility hashUtility;
	private final OtpValidator otpValidator;
	private final MessageSource messageSource;

	OtpService(
			 RedisOtpStore redisOtpStore,
			 SmsSender otpSmsSender,
			 RedisRateLimiter redisRateLimiter,
			 NumericOtpGenerator numericOtpGenerator,
			 OtpCoreProperties otpCoreProperties,
			 HashUtility hashUtility,
			 OtpValidator otpValidator,
			 MessageSource messageSource) {
		 this.redisOtpStore = redisOtpStore;
		 this.otpSmsSender = otpSmsSender;
		 this.redisRateLimiter = redisRateLimiter;
		 this.numericOtpGenerator = numericOtpGenerator;
		 this.otpCoreProperties = otpCoreProperties;
		 this.hashUtility = hashUtility;
		 this.otpValidator = otpValidator;
		 this.messageSource = messageSource;
	}

	public void sendOtp(String mobileNumber,String ip) {
		 this.redisRateLimiter.validateCardinality(mobileNumber,ip);
		 this.redisRateLimiter.validateSendCoolDown(mobileNumber);
		 this.redisRateLimiter.validateCanSend(mobileNumber);
		 String otp = this.numericOtpGenerator.generate(this.otpCoreProperties.length());
		 String hashedOtp = this.hashUtility.sha256Base64(otp);
		 this.redisOtpStore.saveOtp(mobileNumber, hashedOtp);
		 this.otpSmsSender.sendOtpSms(mobileNumber, otp);
		 this.redisRateLimiter.recordSendAttempts(mobileNumber);
	}

	public void verifyOtp(String mobileNumber, String otp) {
		this.redisRateLimiter.validateNotBlocked(mobileNumber);
		this.otpValidator.validate(otp);
		this.redisRateLimiter.recordVerifyAttempts(mobileNumber);

		String storedOtpHash = this.redisOtpStore.getOtp(mobileNumber)
				.orElseThrow(() -> {
					this.redisRateLimiter.recordFailure(mobileNumber);
					return new OtpConfigurationException(
							messageSource.getMessage("error.otp.code.invalid.otp",
									new Object[]{},
									Locale.getDefault()),
							OtpErrorCode.OTP_INVALID);
				});

		if(!this.hashUtility.verify(otp,storedOtpHash)) {
			this.redisRateLimiter.recordFailure(mobileNumber);
			throw new OtpConfigurationException(
					messageSource.getMessage("error.otp.code.invalid.otp",
							new Object[]{}, Locale.getDefault()),
					OtpErrorCode.OTP_INVALID);
		}

		this.redisOtpStore.deleteOtp(mobileNumber);
		this.redisRateLimiter.recordSuccess(mobileNumber);
	}

}

@Component
class RedisRateLimiter {

	private final RedisTemplate<String, String> redis;
	private final OtpKeyBuilder keyBuilder;
	private final OtpRateLimitProperties rateLimitProperties;
	private final OtpRedisProperties otpRedisProperties;
	private final HashUtility hashUtility;
	private final MessageSource messageSource;

	RedisRateLimiter(
			RedisTemplate<String, String> redis,
			OtpKeyBuilder keyBuilder,
			OtpRateLimitProperties rateLimitProperties,
			OtpRedisProperties otpRedisProperties,
			HashUtility hashUtility,
			MessageSource messageSource) {
		this.redis = redis;
		this.keyBuilder = keyBuilder;
		this.rateLimitProperties = rateLimitProperties;
		this.otpRedisProperties = otpRedisProperties;
		this.hashUtility = hashUtility;
		this.messageSource = messageSource;
	}

	public void validateSendCoolDown(String key) {
		String sendCoolDownKey = this.keyBuilder.buildSendCoolDownKey(key);
		if(Boolean.TRUE.equals(this.redis.hasKey(sendCoolDownKey))) {
			throw new OtpRateLimitExceededException(
					messageSource.getMessage("error.otp.code.cooldown.too.many.attempts",
							new Object[] {this.otpRedisProperties.coolDownTtlInSec()},
							Locale.getDefault()),
					OtpErrorCode.OTP_COOLDOWN);
		}
	}

	public void validateCanSend(String key) {
		String sendKey = this.keyBuilder.buildSendKey(key);

		Long attempts = this.redis.opsForValue().increment(sendKey);
		if(attempts != null && attempts == 1) {
			this.redis.expire(sendKey, Duration.ofSeconds(this.otpRedisProperties.sendTtlInSec()));
		}

		if(attempts != null && attempts > this.rateLimitProperties.maxSendAttemptsPerHour()) {
			throw new OtpRateLimitExceededException(messageSource.getMessage("error.otp.code.too.many.failed.attempts",
					new Object[] {},
					Locale.getDefault()),
					OtpErrorCode.OTP_RATELIMIT_EXCEEDED);
		}
	}

	public void recordSendAttempts(String key) {
		String cooldownKey = this.keyBuilder.buildSendCoolDownKey(key);
		this.redis.opsForValue().set(cooldownKey,"1",Duration.ofSeconds(this.otpRedisProperties.coolDownTtlInSec()));
	}

	public void validateNotBlocked(String key) {
		String blockKey = this.keyBuilder.buildBlockKey(key);
		if(Boolean.TRUE.equals(this.redis.hasKey(blockKey))) {
			throw new OtpBlockedException(
					messageSource.getMessage("error.otp.code.blocked",
							new Object[] {},
							Locale.getDefault()),
					OtpErrorCode.OTP_BLOCKED);
		}
	}

	public void recordVerifyAttempts(String key) {
		String verifyKey = this.keyBuilder.buildVerifyKey(key);

		Long attempts = this.redis.opsForValue().increment(verifyKey);
		if(attempts != null && attempts == 1) {
			this.redis.expire(verifyKey,Duration.ofSeconds(this.otpRedisProperties.verifyTtlInSec()));
		}

		if(attempts != null && attempts > this.rateLimitProperties.maxVerifyAttemptsPerHour()) {
			block(key);
			throw new OtpRateLimitExceededException(
					messageSource.getMessage("error.otp.code.too.many.failed.attempts",
							new Object[] {},
							Locale.getDefault()),
					OtpErrorCode.OTP_RATELIMIT_EXCEEDED);
		}
	}

	public void recordFailure(String key) {
		String failureKey = this.keyBuilder.buildFailureKey(key);
		Long failure = this.redis.opsForValue().increment(failureKey);
		if (failure != null && failure == 1) {
			this.redis.expire(failureKey,Duration.ofSeconds(this.otpRedisProperties.failureTtlInSec()));
		}

		if (failure != null && failure >= this.rateLimitProperties.maxFailedAttemptsPerHour()) {
			block(key);
		}
	}

	public void recordSuccess(String key) {
		this.redis.delete(this.keyBuilder.buildVerifyKey(key));
		this.redis.delete(this.keyBuilder.buildFailureKey(key));
		this.redis.delete(this.keyBuilder.buildBlockKey(key));
		this.redis.delete(this.keyBuilder.buildSendKey(key));
		this.redis.delete(this.keyBuilder.buildSendCoolDownKey(key));
	}

	public void validateCardinality(String key,String ip) {
		String cardinalityKey = this.keyBuilder.buildCardinalityKey(ip);

		String hashedKey = this.hashUtility.sha256Base64(key);
		Long cardinality = this.redis.opsForSet().add(cardinalityKey, hashedKey);
		if(cardinality != null && cardinality == 1) {
			Long expirey = this.redis.getExpire(cardinalityKey);
			if(expirey == null || expirey == -1) {
				this.redis.expire(cardinalityKey,Duration.ofSeconds(this.otpRedisProperties.cardinalityInSec()));
			}
		}

		Long size = this.redis.opsForSet().size(cardinalityKey);
		if(size != null && size > this.rateLimitProperties.maxSendAttemptsPerIp()) {
			throw new OtpRateLimitExceededException(
					this.messageSource.getMessage(
							"error.otp.code.too.many.distinct.requets.from.same.ip",
							new Object[] {},
							Locale.getDefault()
					),
					OtpErrorCode.OTP_RATELIMIT_EXCEEDED
			);
		}
	}

	private void block(String key) {
		String blockKey = this.keyBuilder.buildBlockKey(key);
		this.redis.opsForValue().setIfAbsent(blockKey, "1", Duration.ofSeconds(this.rateLimitProperties.blockDurationInSec()));
	}

}

@Component
class NumericOtpGenerator  {

	private final OtpCoreProperties properties;
	private final SecureRandom random;
	private final MessageSource messageSource;

	public NumericOtpGenerator(
			OtpCoreProperties properties,
			SecureRandom random,
			MessageSource messageSource) {
		this.properties = properties;
		this.random = random;
		this.messageSource = messageSource;
	}

	public String generate(int length) {
		validate(length);
		int upperBound = (int) Math.pow(10,length);
		int lowerBound = upperBound / 10;
		int value = lowerBound + random.nextInt(upperBound - lowerBound);
		return String.format("%0" + length + "d", value);
	}

	private void validate(int length) {
		if(length < this.properties.length() || length > this.properties.length()) {
			throw new OtpConfigurationException(
					messageSource.getMessage("error.otp.validation.length.is.not.supported",
							new Object[] {},
							Locale.getDefault()),
					OtpErrorCode.OTP_LENGTH_NOT_SUPPORTED);
		}
	}

}

@Component
class RedisOtpStore {

	private final OtpCoreProperties coreProperties;
	private final OtpKeyBuilder keyBuilder;
	private final RedisTemplate<String,String> redis;

	RedisOtpStore(
			OtpCoreProperties coreProperties,
			OtpKeyBuilder keyBuilder,
			RedisTemplate<String, String> redis) {
		this.coreProperties = coreProperties;
		this.keyBuilder = keyBuilder;
		this.redis = redis;
	}


	public void saveOtp(String key, String code) {
		String otpKey = this.keyBuilder.buildOtpKey(key);
		this.redis.opsForValue().set(otpKey, code, Duration.ofSeconds(this.coreProperties.ttlInSec()));
	}

	public Optional<String> getOtp(String key) {
		String otpKey = this.keyBuilder.buildOtpKey(key);
		return Optional.ofNullable(this.redis.opsForValue().get(otpKey));
	}

	public void deleteOtp(String key) {
		String otpKey = this.keyBuilder.buildOtpKey(key);
		this.redis.delete(otpKey);
	}

}

@Component
class OtpKeyBuilder {

	private final MessageSource messageSource;
	private final OtpRedisProperties otpRedisProperties;

	OtpKeyBuilder(MessageSource messageSource, OtpRedisProperties otpRedisProperties) {
		this.messageSource = messageSource;
		this.otpRedisProperties = otpRedisProperties;
	}

	String buildOtpKey(String key) {
		validateKey(key);
		return this.otpRedisProperties.prefixKey() + key;
	}

	String buildSendCoolDownKey(String key) {
		validateKey(key);
		return this.otpRedisProperties.sendCoolDownPrefixKey() + key;
	}

	String buildSendKey(String key) {
		validateKey(key);
		return this.otpRedisProperties.sendCountPrefixKey() + key;
	}

	String buildVerifyKey(String key) {
		validateKey(key);
		return this.otpRedisProperties.verifyCountPrefixKey() + key;
	}

	String buildFailureKey(String key) {
		validateKey(key);
		return this.otpRedisProperties.failurePrefixKey() + key;
	}

	String buildBlockKey(String key) {
		validateKey(key);
		return this.otpRedisProperties.blockPrefixKey() + key;
	}

	String buildCardinalityKey(String key) {
		validateKey(key);
		return this.otpRedisProperties.cardinalityPrefixKey() + key;
	}

	private void validateKey(String key) {
		if (key == null || key.isBlank()) {
			throw new OtpConfigurationException(messageSource.getMessage("error.otp.validation.key.missing.property",
					new Object[]{}, Locale.getDefault()), OtpErrorCode.OTP_MISSING_PROPERTY);
		}
	}

}




