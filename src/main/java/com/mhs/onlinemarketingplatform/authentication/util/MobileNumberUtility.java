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
package com.mhs.onlinemarketingplatform.authentication.util;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;
import com.mhs.onlinemarketingplatform.authentication.error.validation.InvalidMobileNumberException;
import com.mhs.onlinemarketingplatform.authentication.error.validation.ValidationErrorCode;
import com.mhs.onlinemarketingplatform.authentication.props.ApplicationProperties;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.util.Locale;
/**
 * @author Milad Haghighat Shahedi
 */
public interface MobileNumberUtility {

	String normalize(String phoneNumber);

}

@Component
class GoogleMobileNumberNormalizer implements MobileNumberUtility {

	private final PhoneNumberUtil util = PhoneNumberUtil.getInstance();
	private final ApplicationProperties properties;
	private final MessageSource messageSource;

	public GoogleMobileNumberNormalizer(
			ApplicationProperties properties,
			MessageSource messageSource) {
		this.properties = properties;
		this.messageSource = messageSource;
	}

	@Override
	public String normalize(String phoneNumber) {
		try {
			PhoneNumber number =  util.parse(phoneNumber.trim(),properties.userMobileNumberRegion());

			if(!util.isValidNumber(number))
				throw new InvalidMobileNumberException(
						messageSource.getMessage("error.validation.phone.number.invalid",
								new Object[] {phoneNumber},
								Locale.getDefault()),
						ValidationErrorCode.PHONE_NUMBER_INVALID);

			return util.format(number, PhoneNumberUtil.PhoneNumberFormat.E164);

		} catch (NumberParseException e) {
			throw new InvalidMobileNumberException(
					messageSource.getMessage("error.validation.phone.number.invalid",
							new Object[] {phoneNumber},
							Locale.getDefault()),
					ValidationErrorCode.PHONE_NUMBER_INVALID);
		}
	}

}
