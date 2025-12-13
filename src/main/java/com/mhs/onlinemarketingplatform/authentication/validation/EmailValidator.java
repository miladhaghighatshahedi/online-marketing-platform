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
import java.util.regex.Pattern;
/**
 * @author Milad Haghighat Shahedi
 */
@Component
public class EmailValidator {

	private final List<EmailValidationStrategy> emailValidationStrategies;

	EmailValidator(List<EmailValidationStrategy> emailValidationStrategies) {
		this.emailValidationStrategies = emailValidationStrategies;
	}

	public Optional<ValidationError> validate(String email) {
		return emailValidationStrategies.stream()
				.map(strategy -> strategy.isValid(email))
				.filter(Optional::isPresent)
				.findFirst()
				.orElse(Optional.empty());
	}

}

interface EmailValidationStrategy {

	Optional<ValidationError> isValid(String email);

}

@Component
class EmailBlank implements EmailValidationStrategy {

	@Override
	public Optional<ValidationError> isValid(String email) {
		if(email == null || email.isBlank()) {
			return Optional.of(
					new ValidationError(
							"EMAIL Can not be null or blank",
							"EMAIL",
							"EMAIL_BLANK"
					));}
		return Optional.empty();
	}

}

@Component
class EmailLength implements EmailValidationStrategy {

	private final ApplicationProperties properties;

	public EmailLength(ApplicationProperties properties) {
		this.properties = properties;
	}

	@Override
	public Optional<ValidationError> isValid(String email) {
		if(email.length() < properties.corporateEmailMinLength() || email.length() > properties.corporateEmailMaxLength()) {
			return Optional.of(
					new ValidationError(
							String.format("EMAIL must be between % and % charachters",
									properties.corporateEmailMinLength(),
									properties.corporateEmailMaxLength()),
							"EMAIL",
							"EMAIL_INVALID_LENGTH"
					));
		}
		return Optional.empty();
	}

}

@Component
class EmailPattern implements EmailValidationStrategy {

	private static final Pattern EMAIL_PATTERN =
			Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$",Pattern.CASE_INSENSITIVE);

	@Override
	public Optional<ValidationError> isValid(String email) {
		if(!EMAIL_PATTERN.matcher(email).matches()) {
			return Optional.of(
					new ValidationError(
					"EMAIL must have a valid pattern!",
					"EMAIL",
					"EMAIL_INVALID_PATTERN"));
		}
		return Optional.empty();
	}

}

