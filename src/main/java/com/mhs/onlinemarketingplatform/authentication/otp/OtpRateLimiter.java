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
import com.mhs.onlinemarketingplatform.authentication.props.OtpRateLimitProperties;
import com.mhs.onlinemarketingplatform.authentication.props.OtpRedisProperties;
import org.springframework.context.MessageSource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;
/**
 * @author Milad Haghighat Shahedi
 */
public interface OtpRateLimiter {
	void validateSendCoolDown(String key);
	void validateCanSend(String key);
	void recordSendAttempts(String key);
	void validateNotBlocked(String key);
	void recordVerifyAttempts(String key);
	void recordFailure(String key);
	void recordSuccess(String key);
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
							new Object[] {this.otpRedisProperties.coolDownTtlInSec()},
							Locale.getDefault()),
					OtpErrorCode.OTP_COOLDOWN);
		}
	}

	@Override
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
							new Object[] {},
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
			throw new OtpRateLimitExceededException(
					messageSource.getMessage("error.otp.code.too.many.failed.attempts",
							new Object[] {},
							Locale.getDefault()),
					OtpErrorCode.OTP_RATELIMIT_EXCEEDED);
		}
	}

	@Override
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
