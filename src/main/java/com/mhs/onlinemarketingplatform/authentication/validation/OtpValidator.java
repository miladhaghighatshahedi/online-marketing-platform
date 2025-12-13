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
package com.mhs.onlinemarketingplatform.authentication.validation;

import com.mhs.onlinemarketingplatform.authentication.error.validation.ValidationError;
import com.mhs.onlinemarketingplatform.authentication.props.ApplicationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
/**
 * @author Milad Haghighat Shahedi
 */
@Component
public class OtpValidator {

	private final List<OtpValidationStrategy> otpValidationStrategies;

	 OtpValidator(List<OtpValidationStrategy> otpValidationStrategies) {
		this.otpValidationStrategies = otpValidationStrategies;
	}

	public Optional<ValidationError> validate(String otpCode) {
		 return this.otpValidationStrategies.stream()
				 .map(strategy -> strategy.isValid(otpCode))
				 .filter(Optional::isPresent)
				 .findFirst()
				 .orElse(Optional.empty());
	}

}

interface OtpValidationStrategy {

	Optional<ValidationError> isValid(String otpCode);

}

@Component
class OtpBlank implements OtpValidationStrategy {

	@Override
	public Optional<ValidationError> isValid(String otpCode) {
		if(otpCode == null || otpCode.isBlank()) {
			return Optional.of(
					new ValidationError(
							"OTP_CODE can not be null or blank!",
							"OTP_CODE",
							"OTP_CODE_BLANK"));}
		return Optional.empty();
	}

}

@Component
class OtpLength implements OtpValidationStrategy {

	private final ApplicationProperties properties;

	public OtpLength(ApplicationProperties properties) {
		this.properties = properties;
	}

	@Override
	public Optional<ValidationError> isValid(String otpCode) {
		if (otpCode.length() < properties.otpLength()) {
			return Optional.of(
					new ValidationError(
							String.format("OTP_CODE must be exactly % characters long!",properties.otpLength()),
							"OTP_CODE",
							"OTP_CODE_INVALID_LENGTH"));}
		return Optional.empty();
	}

}

