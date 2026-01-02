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
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
/**
 * @author Milad Haghighat Shahedi
 */
@Component
public class MobileNumberValidator {

	private final List<MobileNumberValidationStrategy> mobileNumberValidationStrategies;

	MobileNumberValidator(List<MobileNumberValidationStrategy> mobileNumberValidationStrategies) {
		this.mobileNumberValidationStrategies = mobileNumberValidationStrategies;
	}

	public Optional<ValidationError> validate(String mobileNumber) {
		return mobileNumberValidationStrategies.stream()
				.map(strategy -> strategy.isValid(mobileNumber))
				.filter(Optional::isPresent)
				.findFirst()
				.orElse(Optional.empty());
	}
}

interface MobileNumberValidationStrategy {

	Optional<ValidationError> isValid(String mobileNumber);

}

@Component
class MobileNumberBlank implements MobileNumberValidationStrategy {

	@Override
	public Optional<ValidationError> isValid(String mobileNumber) {
		if(mobileNumber == null || mobileNumber.isBlank()) {
			return Optional.of(
					new ValidationError(
						"MOBILE-NUMBER can not be null or blank",
						"MOBILE-NUMBER",
						"MOBILE_NUMBER_BLANK"
					));}
		return Optional.empty();
	}
}

@Component
class MobileNumberLength implements MobileNumberValidationStrategy {

	private final ValidationProperties properties;

	public MobileNumberLength(ValidationProperties properties) {
		this.properties = properties;
	}

	@Override
	public Optional<ValidationError> isValid(String mobileNumber) {
		if(mobileNumber.length() != this.properties.mobileNumberLength()) {
			return Optional.of(
					new ValidationError(
							String.format("MOBILE-NUMBER must be %d charachter long",this.properties.mobileNumberLength()),
							"MOBILE-NUMBER",
							"MOBILE_NUMBER_BLANK"
					));}
		return Optional.empty();
	}

}

@Component
class MobileNumberPattern implements MobileNumberValidationStrategy {

	private final Pattern pattern;

	public MobileNumberPattern(ValidationProperties properties) {
		this.pattern = Pattern.compile(properties.mobileNumberPattern());
	}

	@Override
	public Optional<ValidationError> isValid(String mobileNumber) {
		if(!pattern.matcher(mobileNumber).matches()) {
			return Optional.of(
					new ValidationError(
							"MOBILE-NUMBER format is invalid",
							"MOBILE-NUMBER",
							"MOBILE_NUMBER_INVALID_PATTERN"
					));}
		return Optional.empty();
	}

}


