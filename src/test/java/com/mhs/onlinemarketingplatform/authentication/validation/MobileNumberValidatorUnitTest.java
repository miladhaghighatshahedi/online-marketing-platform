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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
public class MobileNumberValidatorUnitTest {

    @Mock
	private MobileNumberPattern mobileNumberPattern;

	@Mock
	private MobileNumberLength mobileNumberLength;

	@Mock
	private MobileNumberBlank mobileNumberBlank;

	private MobileNumberValidator mobileNumberValidator;

	@BeforeEach
	void setUp() {
       mobileNumberValidator = new MobileNumberValidator(
			   List.of(mobileNumberBlank,
					   mobileNumberLength,
					   mobileNumberPattern));
	}

	@Test
	void validate_method_shoudlPass_whenValidMobileNumber() {
		// Arrange
		String mobileNumber = "09364564312";
		when(this.mobileNumberBlank.isValid(mobileNumber)).thenReturn(Optional.empty());
		when(this.mobileNumberLength.isValid(mobileNumber)).thenReturn(Optional.empty());
		when(this.mobileNumberPattern.isValid(mobileNumber)).thenReturn(Optional.empty());
		// Act
		Optional<ValidationError> result = this.mobileNumberValidator.validate(mobileNumber);
		// Assert
		assertTrue(result.isEmpty());
	}

	@Test
	void validate_method_shoudlReturnBlankError_WhenEmptySpace() {
		// Arrange
		String mobileNumber = "";
		ValidationError validationError = new ValidationError(
				"MOBILE-NUMBER can not be null or blank",
				"MOBILE-NUMBER",
				"MOBILE_NUMBER_BLANK");
		when(this.mobileNumberBlank.isValid(mobileNumber)).thenReturn(Optional.of(validationError));
		// Act
		Optional<ValidationError> result = this.mobileNumberValidator.validate(mobileNumber);
		// Assert
		assertTrue(result.isPresent());
		assertEquals("MOBILE-NUMBER can not be null or blank",result.get().message());
		assertEquals("MOBILE-NUMBER",result.get().field());
		assertEquals("MOBILE_NUMBER_BLANK",result.get().status());
	}

	@Test
	void validate_method_shoudlReturnBlankError_WhenWhiteSpace() {
		// Arrange
		String mobileNumber = " ";
		ValidationError validationError = new ValidationError(
				"MOBILE-NUMBER can not be null or blank",
				"MOBILE-NUMBER",
				"MOBILE_NUMBER_BLANK");
		when(this.mobileNumberBlank.isValid(mobileNumber)).thenReturn(Optional.of(validationError));
		// Act
		Optional<ValidationError> result = this.mobileNumberValidator.validate(mobileNumber);
		// Assert
		assertTrue(result.isPresent());
		assertEquals("MOBILE-NUMBER can not be null or blank",result.get().message());
		assertEquals("MOBILE-NUMBER",result.get().field());
		assertEquals("MOBILE_NUMBER_BLANK",result.get().status());
	}

	@Test
	void validate_method_shoudlReturnBlankError_WhenNull() {
		// Arrange
		String mobileNumber = null;
		ValidationError validationError = new ValidationError(
				"MOBILE-NUMBER can not be null or blank",
				"MOBILE-NUMBER",
				"MOBILE_NUMBER_BLANK");
		when(this.mobileNumberBlank.isValid(mobileNumber)).thenReturn(Optional.of(validationError));
		// Act
		Optional<ValidationError> result = this.mobileNumberValidator.validate(mobileNumber);
		// Assert
		assertTrue(result.isPresent());
		assertEquals("MOBILE-NUMBER can not be null or blank",result.get().message());
		assertEquals("MOBILE-NUMBER",result.get().field());
		assertEquals("MOBILE_NUMBER_BLANK",result.get().status());
	}

    @Test
	void validate_method_shoudlReturnLengthError_WhenLengthIsShort() {
		// Arrange
		String mobileNumber = "09364";
		ValidationError validationError = new ValidationError(
				"MOBILE-NUMBER must be 11 charachter long",
				"MOBILE-NUMBER",
				"MOBILE_NUMBER_LENGTH");
		when(this.mobileNumberBlank.isValid(mobileNumber)).thenReturn(Optional.empty());
		when(this.mobileNumberLength.isValid(mobileNumber)).thenReturn(Optional.of(validationError));
		// Act
		Optional<ValidationError> result = this.mobileNumberValidator.validate(mobileNumber);
		// Assert
		assertTrue(result.isPresent());
		assertEquals("MOBILE-NUMBER must be 11 charachter long",result.get().message());
		assertEquals("MOBILE-NUMBER",result.get().field());
		assertEquals("MOBILE_NUMBER_LENGTH",result.get().status());
	}

	@Test
	void validate_method_shoudlReturnLengthError_WhenLengthIsExceeded() {
		// Arrange
		String mobileNumber = "0936443256433";
		ValidationError validationError = new ValidationError(
				"MOBILE-NUMBER must be 11 charachter long",
				"MOBILE-NUMBER",
				"MOBILE_NUMBER_LENGTH");
		when(this.mobileNumberBlank.isValid(mobileNumber)).thenReturn(Optional.empty());
		when(this.mobileNumberLength.isValid(mobileNumber)).thenReturn(Optional.of(validationError));
		// Act
		Optional<ValidationError> result = this.mobileNumberValidator.validate(mobileNumber);
		// Assert
		assertTrue(result.isPresent());
		assertEquals("MOBILE-NUMBER must be 11 charachter long",result.get().message());
		assertEquals("MOBILE-NUMBER",result.get().field());
		assertEquals("MOBILE_NUMBER_LENGTH",result.get().status());
	}

	@ParameterizedTest
	@CsvSource({
			"0936543754",
			"9365437541",
			"1936543754",
			"0236543751",
			"9365437541",
			"x9365437541",
			"0936543754x"
	})
	void validate_method_shoudlReturnPatternError_WhenInvalidPattern(String mobileNumber) {
		// Arrange
		ValidationError validationError = new ValidationError(
				"MOBILE-NUMBER format is invalid",
				"MOBILE-NUMBER",
				"MOBILE_NUMBER_INVALID_PATTERN");
		when(this.mobileNumberBlank.isValid(mobileNumber)).thenReturn(Optional.empty());
		when(this.mobileNumberLength.isValid(mobileNumber)).thenReturn(Optional.empty());
		when(this.mobileNumberLength.isValid(mobileNumber)).thenReturn(Optional.of(validationError));
		// Act
		Optional<ValidationError> result = this.mobileNumberValidator.validate(mobileNumber);
		// Assert
		assertTrue(result.isPresent());
		assertEquals("MOBILE-NUMBER format is invalid",result.get().message());
		assertEquals("MOBILE-NUMBER",result.get().field());
		assertEquals("MOBILE_NUMBER_INVALID_PATTERN",result.get().status());
	}

}
