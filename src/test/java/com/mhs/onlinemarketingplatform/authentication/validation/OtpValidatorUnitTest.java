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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
/**
 * @author Milad Haghighat Shahedi
 */
@ExtendWith(MockitoExtension.class)
public class OtpValidatorUnitTest {

	@Mock
	private OtpBlank otpBlank;

	@Mock
	private OtpLength otpLength;

	private OtpValidator otpValidator;

	@BeforeEach
	void setUp() {
		otpValidator = new OtpValidator(
				List.of(otpBlank,
						otpLength));
	}

	@Test
	void validate_shouldPass_WhenValidOtp() {
		// Arrange
		String otpCode = "234654";
		// Act
		Optional<ValidationError> result = this.otpValidator.validate(otpCode);
		// Assert
		assertTrue(result.isEmpty());
	}

	@Test
	void validate_shouldRetunBlankError_WhenOtpIsBlank() {
		// Arrange
		String otpCode = "";
		ValidationError validationError = new ValidationError(
				"OTP_CODE can not be null or blank!",
				"OTP_CODE",
				"OTP_CODE_BLANK");
		when(this.otpValidator.validate(otpCode)).thenReturn(Optional.of(validationError));
		// Act
		Optional<ValidationError> result = this.otpValidator.validate(otpCode);
		// Assert
		assertTrue(result.isPresent());
		assertEquals("OTP_CODE can not be null or blank!",result.get().message());
		assertEquals("OTP_CODE",result.get().field());
		assertEquals("OTP_CODE_BLANK",result.get().status());
	}

	@Test
	void validate_shouldRetunBlankError_WhenOtpIsWhiteSpace() {
		// Arrange
		String otpCode = " ";
		ValidationError validationError = new ValidationError(
				"OTP_CODE can not be null or blank!",
				"OTP_CODE",
				"OTP_CODE_BLANK");
		when(this.otpValidator.validate(otpCode)).thenReturn(Optional.of(validationError));
		// Act
		Optional<ValidationError> result = this.otpValidator.validate(otpCode);
		// Assert
		assertTrue(result.isPresent());
		assertEquals("OTP_CODE can not be null or blank!",result.get().message());
		assertEquals("OTP_CODE",result.get().field());
		assertEquals("OTP_CODE_BLANK",result.get().status());
	}

	@Test
	void validate_shouldRetunBlankError_WhenOtpIsNull() {
		// Arrange
		String otpCode = null;
		ValidationError validationError = new ValidationError(
				"OTP_CODE can not be null or blank!",
				"OTP_CODE",
				"OTP_CODE_BLANK");
		when(this.otpValidator.validate(otpCode)).thenReturn(Optional.of(validationError));
		// Act
		Optional<ValidationError> result = this.otpValidator.validate(otpCode);
		// Assert
		assertTrue(result.isPresent());
		assertEquals("OTP_CODE can not be null or blank!",result.get().message());
		assertEquals("OTP_CODE",result.get().field());
		assertEquals("OTP_CODE_BLANK",result.get().status());
	}

	@Test
	void validate_shouldRetunLengthError_WhenOtpLengthIsShort() {
		String otpCode = "5465";
		ValidationError validationError = new ValidationError(
				"OTP_CODE must be exactly 6 characters long!",
				"OTP_CODE",
				"OTP_CODE_INVALID_LENGTH");
		when(this.otpValidator.validate(otpCode)).thenReturn(Optional.of(validationError));
		// Act
		Optional<ValidationError> result = this.otpValidator.validate(otpCode);
		// Assert
		assertTrue(result.isPresent());
		assertEquals("OTP_CODE must be exactly 6 characters long!",result.get().message());
		assertEquals("OTP_CODE",result.get().field());
		assertEquals("OTP_CODE_INVALID_LENGTH",result.get().status());
	}

	@Test
	void validate_shouldRetunLengthError_WhenOtpLengthIsExceeded() {
		String otpCode = "6544567";
		ValidationError validationError = new ValidationError(
				"OTP_CODE must be exactly 6 characters long!",
				"OTP_CODE",
				"OTP_CODE_INVALID_LENGTH");
		when(this.otpValidator.validate(otpCode)).thenReturn(Optional.of(validationError));
		// Act
		Optional<ValidationError> result = this.otpValidator.validate(otpCode);
		// Assert
		assertTrue(result.isPresent());
		assertEquals("OTP_CODE must be exactly 6 characters long!",result.get().message());
		assertEquals("OTP_CODE",result.get().field());
		assertEquals("OTP_CODE_INVALID_LENGTH",result.get().status());
	}

}
