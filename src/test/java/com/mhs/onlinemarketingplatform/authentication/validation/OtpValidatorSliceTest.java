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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
		OtpValidator.class,
		OtpBlank.class,
		OtpLength.class
})
@EnableConfigurationProperties(OtpCoreProperties.class)
@TestPropertySource("classpath:application-dev.properties")
public class OtpValidatorSliceTest {

	@Autowired
	private OtpValidator otpValidator;

	@Test
	void validate_shouldPath_WhenValidOtp() {
		String otpCode = "546758";
		Optional<ValidationError> result = this.otpValidator.validate(otpCode);
		assertTrue(result.isEmpty());
	}

	@Test
	void validate_shouldFail_WhenOtpIsEmpty() {
		String otpCode = "";
		Optional<ValidationError> result = this.otpValidator.validate(otpCode);
		assertTrue(result.isPresent());
		assertEquals("OTP_CODE can not be null or blank!",result.get().message());
		assertEquals("OTP_CODE",result.get().field());
		assertEquals("OTP_CODE_BLANK",result.get().status());
	}

	@Test
	void validate_shouldFail_WhenOtpIsWhiteSpace() {
		String otpCode = " ";
		Optional<ValidationError> result = this.otpValidator.validate(otpCode);
		assertTrue(result.isPresent());
		assertEquals("OTP_CODE can not be null or blank!",result.get().message());
		assertEquals("OTP_CODE",result.get().field());
		assertEquals("OTP_CODE_BLANK",result.get().status());
	}

	@Test
	void validate_shouldFail_WhenOtpIsNull() {
		String otpCode = null;
		Optional<ValidationError> result = this.otpValidator.validate(otpCode);
		assertTrue(result.isPresent());
		assertEquals("OTP_CODE can not be null or blank!",result.get().message());
		assertEquals("OTP_CODE",result.get().field());
		assertEquals("OTP_CODE_BLANK",result.get().status());
	}

	@Test
	void validate_shouldFail_WhenOtpLengthIsShort() {
		String otpCode = "543";
		Optional<ValidationError> result = this.otpValidator.validate(otpCode);
		assertTrue(result.isPresent());
		assertTrue(result.isPresent());
		assertEquals("OTP_CODE must be exactly 6 characters long!",result.get().message());
		assertEquals("OTP_CODE",result.get().field());
		assertEquals("OTP_CODE_INVALID_LENGTH",result.get().status());
	}

	@Test
	void validate_shouldFail_WhenOtpLengthIsExceeded() {
		String otpCode = "54367666";
		Optional<ValidationError> result = this.otpValidator.validate(otpCode);
		assertTrue(result.isPresent());
		assertTrue(result.isPresent());
		assertEquals("OTP_CODE must be exactly 6 characters long!",result.get().message());
		assertEquals("OTP_CODE",result.get().field());
		assertEquals("OTP_CODE_INVALID_LENGTH",result.get().status());
	}

}
