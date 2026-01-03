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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
/**
 * @author Milad Haghighat Shahedi
 */
@ExtendWith(MockitoExtension.class)
public class OtpBlankUnitTest {

	@InjectMocks
	private OtpBlank otpBlank;

	@Test
	void isValid_shouldPass_WhenOtpIsNotBlank() {
		// Arrange
		String otpCode = "123543";
		// Act
		Optional<ValidationError> result = this.otpBlank.isValid(otpCode);
		// Assert
		assertTrue(result.isEmpty());
	}

	@Test
	void isValid_shouldFail_WhenOtpIsBlank() {
		// Arrange
		String otpCode = "";
		// Act
		Optional<ValidationError> result = this.otpBlank.isValid(otpCode);
		// Assert
		assertTrue(result.isPresent());
		assertEquals("OTP_CODE can not be null or blank!",result.get().message());
		assertEquals("OTP_CODE",result.get().field());
		assertEquals("OTP_CODE_BLANK",result.get().status());
	}

	@Test
	void isValid_shouldFail_WhenOtpIsEmpty() {
		// Arrange
		String otpCode = "";
		// Act
		Optional<ValidationError> result = this.otpBlank.isValid(otpCode);
		// Assert
		assertTrue(result.isPresent());
		assertEquals("OTP_CODE can not be null or blank!",result.get().message());
		assertEquals("OTP_CODE",result.get().field());
		assertEquals("OTP_CODE_BLANK",result.get().status());
	}

	@Test
	void isValid_shouldFail_WhenOtpIsEmptySpace() {
		// Arrange
		String otpCode = " ";
		// Act
		Optional<ValidationError> result = this.otpBlank.isValid(otpCode);
		// Assert
		assertTrue(result.isPresent());
		assertEquals("OTP_CODE can not be null or blank!",result.get().message());
		assertEquals("OTP_CODE",result.get().field());
		assertEquals("OTP_CODE_BLANK",result.get().status());
	}

}
