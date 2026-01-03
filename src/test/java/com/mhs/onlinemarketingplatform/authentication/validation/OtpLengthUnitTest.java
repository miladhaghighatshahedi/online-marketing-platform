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
import com.mhs.onlinemarketingplatform.authentication.otp.OtpCoreProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
/**
 * @author Milad Haghighat Shahedi
 */
@ExtendWith(MockitoExtension.class)
public class OtpLengthUnitTest {

	@Mock
	private OtpCoreProperties otpCoreProperties;

	@InjectMocks
	private OtpLength otpLength;

	@BeforeEach
	void setUp() {
		when(this.otpCoreProperties.length()).thenReturn(6);
	}

	@Test
	void isValid_shouldPass_WhenOtpHasValidLength() {
		// Arrange
		String otpCode = "123543";
		// Act
		Optional<ValidationError> result = this.otpLength.isValid(otpCode);
		// Assert
		assertTrue(result.isEmpty());
	}

	@Test
	void isValid_shouldFail_WhenOtpLengthIsShort() {
		// Arrange
		String otpCode = "123";
		ValidationError validationError = new ValidationError(
				"OTP_CODE must be exactly 6 characters long!",
				"OTP_CODE",
				"OTP_CODE_INVALID_LENGTH");
		// Act
		Optional<ValidationError> result = this.otpLength.isValid(otpCode);
		// Assert
		assertTrue(result.isPresent());
		assertEquals("OTP_CODE must be exactly 6 characters long!",result.get().message());
		assertEquals("OTP_CODE",result.get().field());
		assertEquals("OTP_CODE_INVALID_LENGTH",result.get().status());
	}

	@Test
	void isValid_shouldFail_WhenOtpLengthIsExceeded() {
		// Arrange
		String otpCode = "1234567";
		ValidationError validationError = new ValidationError(
				"OTP_CODE must be exactly 6 characters long!",
				"OTP_CODE",
				"OTP_CODE_INVALID_LENGTH");
		// Act
		Optional<ValidationError> result = this.otpLength.isValid(otpCode);
		// Assert
		assertTrue(result.isPresent());
		assertEquals("OTP_CODE must be exactly 6 characters long!",result.get().message());
		assertEquals("OTP_CODE",result.get().field());
		assertEquals("OTP_CODE_INVALID_LENGTH",result.get().status());
	}

}
