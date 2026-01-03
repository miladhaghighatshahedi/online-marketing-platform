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
public class PasswordValidator  {

	private final List<PasswordValidationStrategy> passValidationStrategies;

	PasswordValidator(List<PasswordValidationStrategy> passValidationStrategies) {
		this.passValidationStrategies = passValidationStrategies;
	}

	public Optional<ValidationError> validate(String password) {
		return passValidationStrategies.stream()
				.map(strategy -> strategy.isValid(password))
				.filter(Optional::isPresent)
				.findFirst()
				.orElse(Optional.empty());
	}

}

interface PasswordValidationStrategy {

	Optional<ValidationError> isValid(String password);

}

@Component
@Order(1)
class PasswordBlank implements PasswordValidationStrategy {

	@Override
	public Optional<ValidationError> isValid(String password) {
		if(password == null || password.isBlank()){
			return Optional.of(
					new ValidationError(
							"Password can not be null or blank!",
							"PASSWORD",
							"PASSWORD_BLANK"));}
		return Optional.empty();
	}

}


@Component
@Order(2)
class PasswordLength implements PasswordValidationStrategy {

	private final ValidationProperties properties;

	public PasswordLength(ValidationProperties properties) {
		this.properties = properties;
	}

	@Override
	public Optional<ValidationError> isValid(String password) {
		if (password.length() < this.properties.passwordMinLength()) {
			return Optional.of(
					new ValidationError(
							String.format("Password must be min %d characters long!",this.properties.passwordMinLength()),
							"PASSWORD",
							"PASSWORD_INVALID_LENGTH"));}
		return Optional.empty();
	}

}

@Component
@Order(3)
class PasswordComplexity implements PasswordValidationStrategy {

	private final Pattern pattern;

	public PasswordComplexity(ValidationProperties properties) {
		this.pattern = Pattern.compile(properties.passwordPattern(),Pattern.CASE_INSENSITIVE);
	}

	@Override
	public Optional<ValidationError> isValid(String password) {
		if(!this.pattern.matcher(password).matches()){
			return Optional.of(
					new ValidationError(
							"Password must contain at least one number and one special character!",
							"PASSWORD",
							"PASSWORD_WEAK"));}
		return Optional.empty();
	}

}
