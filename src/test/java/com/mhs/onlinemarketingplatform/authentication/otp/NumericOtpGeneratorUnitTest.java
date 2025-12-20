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
package com.mhs.onlinemarketingplatform.authentication.otp;

import com.mhs.onlinemarketingplatform.authentication.error.otp.OtpConfigurationException;
import com.mhs.onlinemarketingplatform.authentication.props.OtpCoreProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import java.security.SecureRandom;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.AssertionsKt.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
/**
 * @author Milad Haghighat Shahedi
 */
@ExtendWith(MockitoExtension.class)
public class NumericOtpGeneratorUnitTest {

	@InjectMocks
	private NumericOtpGenerator numericOtpGenerator;

	@Mock
	private OtpCoreProperties otpCoreProperties;

	@Mock
	private MessageSource messageSource;

    @Mock
	private SecureRandom secureRandom;


	@Test
	void generate_method_shouldGenrateSixDigitOtpCode() {
		// Arrange
		when(this.otpCoreProperties.length()).thenReturn(6);
		// Act
		String result = this.numericOtpGenerator.generate(6);
		// Assert
		assertNotNull(result);
		assertEquals(6,result.length());
	}

	@Test
	void generate_method_shouldReturnOnlyDigits() {
		// Arrange
		when(this.otpCoreProperties.length()).thenReturn(6);
		// Act
		String result = this.numericOtpGenerator.generate(6);
		// Assert
		assertNotNull(result);
		assertEquals(6,result.length());
		assertTrue(result.matches("\\d{6}"));
	}

	@Test
	void generate_method_shouldNotStartwithZero() {
		// Arrange
		when(this.otpCoreProperties.length()).thenReturn(6);
		// Act
		String result = this.numericOtpGenerator.generate(6);
		// Assert
		assertNotNull(result);
		assertEquals(6,result.length());
		assertNotEquals("0",result.charAt(0));
	}

	@Test
	void generate_method_shouldThrowOtpConfigurationException_WhenLengthIsNotCorrect_1() {
		// Arrange
	   when(this.messageSource.getMessage(
			   eq("error.otp.validation.length.is.not.supported"),
			   eq(new Object[] {}),
			   any(Locale.class)
	   )).thenReturn("Otp length must be 6 digits long");
	   // Act
		OtpConfigurationException exception = assertThrows(OtpConfigurationException.class,
				()-> this.numericOtpGenerator.generate(2));
		// Assert
		assertEquals("Otp length must be 6 digits long",exception.getMessage());
	}

	@Test
	void generate_method_shouldThrowOtpConfigurationException_WhenLengthIsNotCorrect_2() {
		// Arrange
		when(this.messageSource.getMessage(
				eq("error.otp.validation.length.is.not.supported"),
				eq(new Object[] {}),
				any(Locale.class)
		)).thenReturn("Otp length must be 6 digits long");
		// Act
		OtpConfigurationException exception = assertThrows(OtpConfigurationException.class,
				()-> this.numericOtpGenerator.generate(20));
		// Assert
		assertEquals("Otp length must be 6 digits long",exception.getMessage());
	}

}
