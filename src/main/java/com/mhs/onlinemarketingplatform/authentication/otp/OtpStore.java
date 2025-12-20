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

import com.mhs.onlinemarketingplatform.authentication.props.OtpCoreProperties;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
/**
 * @author Milad Haghighat Shahedi
 */
public interface OtpStore {
	void saveOtp(String key, String hashedOtp);
	Optional<String> getOtp(String key);
	void deleteOtp(String key);
}

@Component
class RedisOtpStore implements OtpStore {

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

	@Override
	public void saveOtp(String key, String code) {
		String otpKey = this.keyBuilder.buildOtpKey(key);
		this.redis.opsForValue().set(otpKey, code, Duration.ofSeconds(this.coreProperties.ttlInSec()));
	}

	@Override
	public Optional<String> getOtp(String key) {
		String otpKey = this.keyBuilder.buildOtpKey(key);
		return Optional.ofNullable(this.redis.opsForValue().get(otpKey));
	}

	@Override
	public void deleteOtp(String key) {
		String otpKey = this.keyBuilder.buildOtpKey(key);
		this.redis.delete(otpKey);
	}

}
