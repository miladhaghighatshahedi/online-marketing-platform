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

	private final ApplicationProperties properties;

	public MobileNumberLength(ApplicationProperties properties) {
		this.properties = properties;
	}

	@Override
	public Optional<ValidationError> isValid(String mobileNumber) {
		if(mobileNumber.length() != properties.userMobileNumberLength()) {
			return Optional.of(
					new ValidationError(
							String.format("MOBILE-NUMBER must be % charachter long",properties.userMobileNumberLength()),
							"MOBILE-NUMBER",
							"MOBILE_NUMBER_BLANK"
					));}
		return Optional.empty();
	}

}


