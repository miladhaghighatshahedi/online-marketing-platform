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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
/**
 * @author Milad Haghighat Shahedi
 */
@ExtendWith(MockitoExtension.class)
public class MobileNumberPatternUnitTest {

	@Mock
	private ValidationProperties properties;

	private MobileNumberPattern mobileNumberPattern;

	@BeforeEach
	void setUp() {
		when(this.properties.mobileNumberPattern()).thenReturn("^09(0[1-5]|[1-3]\\d|2[0-2]|98)\\d{7}$");
        this.mobileNumberPattern = new MobileNumberPattern(properties);
	}

	@Test
	void isValid_shouldPass_whenValidPattern() {
		// Arrange
		String mobileNumber = "09365437543";
		// Act
		Optional<ValidationError> result = this.mobileNumberPattern.isValid(mobileNumber);
		// Assert
		assertTrue(result.isEmpty());
		assertNotNull(this.properties.mobileNumberPattern());
	}

	@Test
	void isValid_shouldFail_whenInvalidPattern_1() {
		// Arrange
		String mobileNumber = "0936543754";
		// Act
		Optional<ValidationError> result = this.mobileNumberPattern.isValid(mobileNumber);
		// Assert
		assertTrue(result.isPresent());
		assertEquals("MOBILE-NUMBER format is invalid",result.get().message());
		assertEquals("MOBILE-NUMBER",result.get().field());
		assertEquals("MOBILE_NUMBER_INVALID_PATTERN",result.get().status());
		assertNotNull(this.properties.mobileNumberPattern());
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
	void isValid_shouldFail_whenInvalidPattern_2(String mobileNumber) {
		// Act
		Optional<ValidationError> result = this.mobileNumberPattern.isValid(mobileNumber);
		// Assert
		assertTrue(result.isPresent());
		assertEquals("MOBILE-NUMBER format is invalid",result.get().message());
		assertEquals("MOBILE-NUMBER",result.get().field());
		assertEquals("MOBILE_NUMBER_INVALID_PATTERN",result.get().status());
		assertNotNull(this.properties.mobileNumberPattern());
		System.out.println(this.properties.mobileNumberPattern());
	}

}
