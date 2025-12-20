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
import com.mhs.onlinemarketingplatform.authentication.error.otp.OtpErrorCode;
import com.mhs.onlinemarketingplatform.authentication.props.OtpRedisProperties;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.util.Locale;
/**
 * @author Milad Haghighat Shahedi
 */
@Component
public class OtpKeyBuilder {

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
