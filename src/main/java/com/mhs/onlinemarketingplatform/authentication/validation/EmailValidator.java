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
import org.springframework.core.annotation.Order;
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
@Order(1)
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
@Order(2)
class EmailLength implements EmailValidationStrategy {

	private final ValidationProperties properties;

	public EmailLength(ValidationProperties properties) {
		this.properties = properties;
	}

	@Override
	public Optional<ValidationError> isValid(String email) {
		if(email.length() < this.properties.emailMinLength() || email.length() > this.properties.emailMaxLength()) {
			return Optional.of(
					new ValidationError(
							String.format("EMAIL must be between %d and %d charachters",
									this.properties.emailMinLength(),
									this.properties.emailMaxLength()),
							"EMAIL",
							"EMAIL_INVALID_LENGTH"
					));
		}
		return Optional.empty();
	}

}

@Component
@Order(3)
class EmailPattern implements EmailValidationStrategy {

	private final Pattern pattern;

	public EmailPattern(ValidationProperties properties) {
		this.pattern = Pattern.compile(properties.emailPattern(),Pattern.CASE_INSENSITIVE);
	}

	@Override
	public Optional<ValidationError> isValid(String email) {
		if(!this.pattern.matcher(email).matches()) {
			return Optional.of(
					new ValidationError(
					"EMAIL must have a valid pattern!",
					"EMAIL",
					"EMAIL_INVALID_PATTERN"));
		}
		return Optional.empty();
	}

}

