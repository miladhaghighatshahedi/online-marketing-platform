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

import com.mhs.onlinemarketingplatform.authentication.error.otp.*;
import com.mhs.onlinemarketingplatform.authentication.props.OtpCoreProperties;
import com.mhs.onlinemarketingplatform.authentication.sms.SmsSender;
import com.mhs.onlinemarketingplatform.authentication.util.HashUtility;
import com.mhs.onlinemarketingplatform.authentication.validation.OtpValidator;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import java.util.Locale;
/**
 * @author Milad Haghighat Shahedi
 */
public interface OtpService {
	void sendOtp(String mobileNumber,String ip);
	void verifyOtp(String mobileNumber,String otp);
}

@Service
class OtpServiceImpl implements OtpService {

	private final OtpStore otpStore;
	private final SmsSender otpSmsSender;
	private final OtpRateLimiter otpRateLimiter;
	private final OtpGenerator otpGenerator;
	private final OtpCoreProperties otpCoreProperties;
	private final HashUtility hashUtility;
	private final OtpValidator otpValidator;
	private final MessageSource messageSource;

	 OtpServiceImpl(
			 OtpStore otpStore,
			 SmsSender otpSmsSender,
			 OtpRateLimiter otpRateLimiter,
			 OtpGenerator otpGenerator,
			 OtpCoreProperties otpCoreProperties,
			 HashUtility hashUtility,
			 OtpValidator otpValidator,
			 MessageSource messageSource) {
		 this.otpStore = otpStore;
		 this.otpSmsSender = otpSmsSender;
		 this.otpRateLimiter = otpRateLimiter;
		 this.otpGenerator = otpGenerator;
		 this.otpCoreProperties = otpCoreProperties;
		 this.hashUtility = hashUtility;
		 this.otpValidator = otpValidator;
		 this.messageSource = messageSource;
	}

	public void sendOtp(String mobileNumber,String ip) {

		 this.otpRateLimiter.validateCardinality(mobileNumber,ip);
		 this.otpRateLimiter.validateSendCoolDown(mobileNumber);
		 this.otpRateLimiter.validateCanSend(mobileNumber);
		 String otp = this.otpGenerator.generate(this.otpCoreProperties.length());
		 String hashedOtp = this.hashUtility.sha256Base64(otp);
		 this.otpStore.saveOtp(mobileNumber, hashedOtp);
		 this.otpSmsSender.sendOtpSms(mobileNumber, otp);
		 this.otpRateLimiter.recordSendAttempts(mobileNumber);
	}

	public void verifyOtp(String mobileNumber, String otp) {
		this.otpRateLimiter.validateNotBlocked(mobileNumber);
		this.otpValidator.validate(otp);
		this.otpRateLimiter.recordVerifyAttempts(mobileNumber);

		String storedOtpHash = this.otpStore.getOtp(mobileNumber)
				.orElseThrow(() -> {
					this.otpRateLimiter.recordFailure(mobileNumber);
					return new OtpConfigurationException(
							messageSource.getMessage("error.otp.code.invalid.otp",
									new Object[]{},
									Locale.getDefault()),
							OtpErrorCode.OTP_INVALID);
				});

		if(!this.hashUtility.verfiyToken(otp,storedOtpHash)) {
			this.otpRateLimiter.recordFailure(mobileNumber);
			throw new OtpConfigurationException(
					messageSource.getMessage("error.otp.code.invalid.otp",
							new Object[]{}, Locale.getDefault()),
					OtpErrorCode.OTP_INVALID);
		}

		this.otpStore.deleteOtp(mobileNumber);
		this.otpRateLimiter.recordSuccess(mobileNumber);
	}

}




