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

import com.mhs.onlinemarketingplatform.authentication.error.otp.*;
import com.mhs.onlinemarketingplatform.authentication.props.OtpCoreProperties;
import com.mhs.onlinemarketingplatform.authentication.props.OtpRateLimitProperties;
import com.mhs.onlinemarketingplatform.authentication.props.OtpRedisProperties;
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
import java.util.Objects;
import java.util.Optional;
/**
 * @author Milad Haghighat Shahedi
 */
public interface OtpService {
	void sendOtp(String phoneNumber);
	void verifyOtp(String phoneNumber,String otp);
}

interface OtpGenerator {
	String generate(int length);
}

interface OtpStore {
	void saveOtp(String key,String hashedOtp);
	Optional<String> getOtp(String key);
	void deleteOtp(String key);
}

interface OtpRateLimiter {
	void validateSendCoolDown(String key);
	void validateCanSend(String key);
	void recordSendAttempts(String key);
	void validateNotBlocked(String key);
	void recordVerifyAttempts(String key);
	void recordFailure(String key);
	void recordSuccess(String key);
}

@Service
class OtpServiceImpl implements OtpService {

	private final OtpStore otpStore;
	private final SmsSender otpSmsSender;
	private final OtpRateLimiter otpRateLimiter;
	private final OtpGenerator otpGenerator;
	private final OtpCoreProperties otpCoreProperties;
	private final HashUtility hashUtility;
	private final OtpValidator otpValidator;
	private final MessageSource messageSource;

	 OtpServiceImpl(
			 OtpStore otpStore,
			 SmsSender otpSmsSender,
			 OtpRateLimiter otpRateLimiter,
			 OtpGenerator otpGenerator,
			 OtpCoreProperties otpCoreProperties,
			 HashUtility hashUtility,
			 OtpValidator otpValidator,
			 MessageSource messageSource) {
		 this.otpStore = otpStore;
		 this.otpSmsSender = otpSmsSender;
		 this.otpRateLimiter = otpRateLimiter;
		 this.otpGenerator = otpGenerator;
		 this.otpCoreProperties = otpCoreProperties;
		 this.hashUtility = hashUtility;
		 this.otpValidator = otpValidator;
		 this.messageSource = messageSource;
	}

	public void sendOtp(String mobileNumber) {
		 this.otpRateLimiter.validateSendCoolDown(mobileNumber);
		 this.otpRateLimiter.validateCanSend(mobileNumber);
		 String otp = this.otpGenerator.generate(this.otpCoreProperties.length());
		 String hashedOtp = this.hashUtility.sha256Base64(otp);
		 this.otpStore.saveOtp(mobileNumber, hashedOtp);
		 this.otpSmsSender.sendOtpSms(mobileNumber, otp);
		 this.otpRateLimiter.recordSendAttempts(mobileNumber);
	}

	public void verifyOtp(String mobileNumber, String otp) {
		this.otpRateLimiter.validateNotBlocked(mobileNumber);
		this.otpValidator.validate(otp);
		this.otpRateLimiter.recordVerifyAttempts(mobileNumber);

		String storedOtpHash = this.otpStore.getOtp(mobileNumber).orElseThrow(
				() -> {
					this.otpRateLimiter.recordFailure(mobileNumber);
					return new OtpConfigurationException(
							messageSource.getMessage("error.otp.code.invalid.otp",
									new Objects[]{}, Locale.getDefault()),
							OtpErrorCode.OTP_INVALID);
				      });

		if(!this.hashUtility.verfiyToken(otp,storedOtpHash)) {
			this.otpRateLimiter.recordFailure(mobileNumber);
			throw new OtpConfigurationException(
					messageSource.getMessage("error.otp.code.invalid.otp",
							new Objects[]{}, Locale.getDefault()),
					OtpErrorCode.OTP_INVALID);
		}

		this.otpStore.deleteOtp(mobileNumber);
		this.otpRateLimiter.recordSuccess(mobileNumber);
	}

}

@Component
class NumericOtpGenerator implements OtpGenerator {

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

	@Override
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
							new Objects[] {},
							Locale.getDefault()),
					OtpErrorCode.OTP_LENGTH_NOT_SUPPORTED);
		}
	}

}

@Component
class RedisOtpStore implements OtpStore {

	private final OtpRedisProperties redisProperties;
	private final OtpCoreProperties coreProperties;
	private final RedisTemplate<String,String> redis;

	RedisOtpStore(
			OtpRedisProperties redisProperties,
			OtpCoreProperties coreProperties,
			RedisTemplate<String, String> redis) {
		this.redisProperties = redisProperties;
		this.coreProperties = coreProperties;
		this.redis = redis;
	}

	@Override
	public void saveOtp(String key, String code) {
		String saveKey = this.redisProperties.prefixKey() + key;
		this.redis.opsForValue().set(saveKey, code, Duration.ofSeconds(this.coreProperties.ttlInSec()));
	}

	@Override
	public Optional<String> getOtp(String key) {
		String otpKey = this.redisProperties.prefixKey() + key;
		return Optional.ofNullable(this.redis.opsForValue().get(otpKey));
	}

	@Override
	public void deleteOtp(String key) {
		String otpKey = this.redisProperties.prefixKey() + key;
		this.redis.delete(otpKey);
	}

}

@Component
class RedisRateLimiter implements OtpRateLimiter {

	private final RedisTemplate<String, String> redis;
	private final OtpKeyBuilder keyBuilder;
	private final OtpRateLimitProperties rateLimitProperties;
	private final OtpRedisProperties otpRedisProperties;
	private final MessageSource messageSource;

	RedisRateLimiter(
			RedisTemplate<String, String> redis,
			OtpKeyBuilder keyBuilder,
			OtpRateLimitProperties rateLimitProperties,
			OtpRedisProperties otpRedisProperties,
			MessageSource messageSource) {
		this.redis = redis;
		this.keyBuilder = keyBuilder;
		this.rateLimitProperties = rateLimitProperties;
		this.otpRedisProperties = otpRedisProperties;
		this.messageSource = messageSource;
	}

	@Override
	public void validateSendCoolDown(String key) {
		String sendCoolDownKey = this.keyBuilder.buildSendCoolDownKey(key);
		if(Boolean.TRUE.equals(this.redis.hasKey(sendCoolDownKey))) {
			throw new OtpRateLimitExceededException(
					messageSource.getMessage("error.otp.code.cooldown.too.many.attempts",
							new Objects[] {},
							Locale.getDefault()),
					OtpErrorCode.OTP_COOLDOWN);
		}
	}

	@Override
	public void validateCanSend(String key) {
		String sendKey = this.keyBuilder.buildSendKey(key);

		Long attempts = this.redis.opsForValue().increment(sendKey);
        if(attempts != null && attempts == 1) {
			this.redis.expire(sendKey,Duration.ofSeconds(this.otpRedisProperties.sendTtlInSec()));
        }

		if(attempts != null && attempts > this.rateLimitProperties.maxSendAttemptsPerHour()) {
			throw new OtpRateLimitExceededException(messageSource.getMessage("error.otp.code.too.many.failed.attempts",
					new Objects[] {},
					Locale.getDefault()),
					OtpErrorCode.OTP_RATELIMIT_EXCEEDED);
		}
	}

	@Override
	public void recordSendAttempts(String key) {
		String cooldownKey = this.keyBuilder.buildSendCoolDownKey(key);
		this.redis.opsForValue().set(cooldownKey,"1",Duration.ofSeconds(this.otpRedisProperties.coolDownTtlInSec()));
	}

	@Override
	public void validateNotBlocked(String key) {
		String blockKey = this.keyBuilder.buildBlockKey(key);
		if(Boolean.TRUE.equals(this.redis.hasKey(blockKey))) {
			throw new OtpBlockedException(
					messageSource.getMessage("error.otp.code.blocked",
							new Objects[] {},
							Locale.getDefault()),
					OtpErrorCode.OTP_BLOCKED);
		}
	}

	@Override
	public void recordVerifyAttempts(String key) {
		String verifyKey = this.keyBuilder.buildVerifyKey(key);

		Long attempts = this.redis.opsForValue().increment(verifyKey);
		if(attempts != null && attempts == 1) {
			this.redis.expire(verifyKey,Duration.ofSeconds(this.otpRedisProperties.verifyTtlInSec()));
		}

		if(attempts != null && attempts > this.rateLimitProperties.maxVerifyAttemptsPerHour()) {
			block(key);
			throw new OtpRateLimitExceededException(messageSource.getMessage("error.otp.code.too.many.failed.attempts",
					new Objects[] {},
					Locale.getDefault()),
					OtpErrorCode.OTP_RATELIMIT_EXCEEDED);
		}
	}

	@Override
	public void recordFailure(String key) {
		String failureKey = this.keyBuilder.buildFailureKey(key);
		Long failure = this.redis.opsForValue().increment(failureKey);
		if (failure != null && failure == 1) {
			this.redis.expire(failureKey,Duration.ofSeconds(this.otpRedisProperties.verifyTtlInSec()));
		}

		if (failure != null && failure >= this.rateLimitProperties.maxFailedAttemptsPerHour()) {
			block(key);
		}
	}

	@Override
	public void recordSuccess(String key) {
		this.redis.delete(this.keyBuilder.buildVerifyKey(key));
		this.redis.delete(this.keyBuilder.buildFailureKey(key));
		this.redis.delete(this.keyBuilder.buildBlockKey(key));
		this.redis.delete(this.keyBuilder.buildSendKey(key));
		this.redis.delete(this.keyBuilder.buildSendCoolDownKey(key));
	}

	private void block(String key) {
		String blockKey = this.keyBuilder.buildBlockKey(key);
		this.redis.opsForValue().set(blockKey, "1", Duration.ofSeconds(this.rateLimitProperties.blockDurationInSec()));
	}

}

@Component
class OtpKeyBuilder {

	private final MessageSource messageSource;
	private final OtpRedisProperties otpRedisProperties;

	OtpKeyBuilder(
			MessageSource messageSource,
			OtpRedisProperties otpRedisProperties) {
		this.messageSource = messageSource;
		this.otpRedisProperties = otpRedisProperties;
	}

	String buildSendCoolDownKey(String key) {
		validateKey(key);
		return this.otpRedisProperties.sendCoolDownPrefixKey() + key;
	}

	String buildVerifyCoolDownKey(String key) {
		validateKey(key);
		return this.otpRedisProperties.verifyCoolDownPrefixKey() + key;
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

	private void validateKey(String key) {
		if(key == null || key.isBlank()) {
			throw new OtpConfigurationException(
					messageSource.getMessage("error.otp.validation.key.missing.property",
							new Objects[] {},
							Locale.getDefault()),
					OtpErrorCode.OTP_MISSING_PROPERTY);
		}
	}

}




