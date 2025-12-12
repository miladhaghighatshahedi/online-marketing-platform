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
public class PasswordValidator  {

	private final List<PasswordValidationStrategy> passValidationStrategies;

	PasswordValidator(List<PasswordValidationStrategy> passValidationStrategies) {
		this.passValidationStrategies = passValidationStrategies;
	}

	public Optional<ValidationError> passwordIsValid(String password) {
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
class PasswordComplexity implements PasswordValidationStrategy {

	private static final Pattern PASSWORD_STRENGTH = Pattern.compile("^(?=.*[0-9])(?=.*[!@#$%^&*]).{8,}$",Pattern.CASE_INSENSITIVE);

	@Override
	public Optional<ValidationError> isValid(String password) {
		if(!PASSWORD_STRENGTH.matcher(password).matches()){
			return Optional.of(
					new ValidationError(
							"Password must contain at least one number and one special character!",
							"PASSWORD",
							"PASSWORD_WEAK"));}
		return Optional.empty();
	}

}

@Component
class PasswordLength implements PasswordValidationStrategy {

	private static final int PASSWORD_MIN_LENGTH = 8;

	@Override
	public Optional<ValidationError> isValid(String password) {
		if (password.length() < PASSWORD_MIN_LENGTH) {
			return Optional.of(
					new ValidationError(
							"Password must be min 8 characters long!",
							"PASSWORD",
							"PASSWORD_INVALID_LENGTH"));}
		return Optional.empty();
	}

}


