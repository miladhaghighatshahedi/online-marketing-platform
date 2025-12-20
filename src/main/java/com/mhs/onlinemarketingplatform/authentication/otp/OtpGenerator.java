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
import com.mhs.onlinemarketingplatform.authentication.props.OtpCoreProperties;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Locale;
/**
 * @author Milad Haghighat Shahedi
 */
public interface OtpGenerator {
	String generate(int length);
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
							new Object[] {},
							Locale.getDefault()),
					OtpErrorCode.OTP_LENGTH_NOT_SUPPORTED);
		}
	}

}
