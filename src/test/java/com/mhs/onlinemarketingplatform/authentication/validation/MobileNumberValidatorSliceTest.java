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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
/**
 * @author Milad Haghighat Shahedi
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
		MobileNumberValidator.class,
		MobileNumberPattern.class,
		MobileNumberLength.class,
		MobileNumberBlank.class
})
@EnableConfigurationProperties(ValidationProperties.class)
@TestPropertySource("classpath:application-dev.properties")
public class MobileNumberValidatorSliceTest {

	@Autowired
	private MobileNumberValidator mobileNumberValidator;

	@Test
	void validate_shouldPass_WhenValidMobileNumber() {
		String mobileNumber = "09368765432";
		Optional<ValidationError> result = this.mobileNumberValidator.validate(mobileNumber);
		assertTrue(result.isEmpty());
	}

	@Test
	void validate_shouldFail_WhenMobileNumberIsEmpty() {
		String mobileNumber = "";
		Optional<ValidationError> result = this.mobileNumberValidator.validate(mobileNumber);
		assertTrue(result.isPresent());
		assertEquals("MOBILE-NUMBER can not be null or blank",result.get().message());
		assertEquals("MOBILE-NUMBER",result.get().field());
		assertEquals("MOBILE_NUMBER_BLANK",result.get().status());
	}

	@Test
	void validate_shouldFail_WhenMobileNumberIsWhiteSpace() {
		String mobileNumber = " ";
		Optional<ValidationError> result = this.mobileNumberValidator.validate(mobileNumber);
		assertTrue(result.isPresent());
		assertEquals("MOBILE-NUMBER can not be null or blank",result.get().message());
		assertEquals("MOBILE-NUMBER",result.get().field());
		assertEquals("MOBILE_NUMBER_BLANK",result.get().status());
	}

	@Test
	void validate_shouldFail_WhenMobileNumberIsNull() {
		String mobileNumber = null;
		Optional<ValidationError> result = this.mobileNumberValidator.validate(mobileNumber);
		assertTrue(result.isPresent());
		assertEquals("MOBILE-NUMBER can not be null or blank",result.get().message());
		assertEquals("MOBILE-NUMBER",result.get().field());
		assertEquals("MOBILE_NUMBER_BLANK",result.get().status());
	}

	@Test
	void validate_shouldFail_WhenMobileNumberIsShort() {
		String mobileNumber = "0936";
		Optional<ValidationError> result = this.mobileNumberValidator.validate(mobileNumber);
		assertTrue(result.isPresent());
		assertEquals("MOBILE-NUMBER must be 11 charachter long",result.get().message());
		assertEquals("MOBILE-NUMBER",result.get().field());
		assertEquals("MOBILE_NUMBER_LENGTH",result.get().status());
	}

	@Test
	void validate_shouldFail_WhenMobileNumberIsLong() {
		String mobileNumber = "093632343233";
		Optional<ValidationError> result = this.mobileNumberValidator.validate(mobileNumber);
		assertTrue(result.isPresent());
		assertEquals("MOBILE-NUMBER must be 11 charachter long",result.get().message());
		assertEquals("MOBILE-NUMBER",result.get().field());
		assertEquals("MOBILE_NUMBER_LENGTH",result.get().status());
	}

	@ParameterizedTest
	@CsvSource({
			"93654375411",
			"x9365437541",
			"0936543754x",
			"00654375411",
			"00000000000"
	})
	void validate_shouldFail_WhenMobileNumberHasInvalidPattern(String mobileNumber) {
		Optional<ValidationError> result = this.mobileNumberValidator.validate(mobileNumber);
		assertTrue(result.isPresent());
		assertEquals("MOBILE-NUMBER format is invalid",result.get().message());
		assertEquals("MOBILE-NUMBER",result.get().field());
		assertEquals("MOBILE_NUMBER_INVALID_PATTERN",result.get().status());
	}

}
