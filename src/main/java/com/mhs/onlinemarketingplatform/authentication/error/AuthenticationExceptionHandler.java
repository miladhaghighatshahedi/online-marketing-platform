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
package com.mhs.onlinemarketingplatform.authentication.error;

import com.mhs.onlinemarketingplatform.authentication.error.ApiErrorMessage;
import com.mhs.onlinemarketingplatform.common.ErrorLogger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * @author Milad Haghighat Shahedi
 */
@RestControllerAdvice
public class AuthenticationExceptionHandler {

	private final ErrorLogger errorLogger;

	public AuthenticationExceptionHandler(ErrorLogger errorLogger) {
		this.errorLogger = errorLogger;
	}

	@ExceptionHandler(UserNotFoundException.class)
	public ResponseEntity<ApiErrorMessage> handleUserNotFoundException(UserNotFoundException ex) {
		ApiErrorMessage error = new ApiErrorMessage(
				HttpStatus.NOT_FOUND.value(),
				ex.getMessage(),
				"USER_NOT_FOUND"
		);
		errorLogger.logError("USER", "USER_NOT_FOUND", "Error: " + ex.getMessage());
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
	}

	@ExceptionHandler(UserAlreadyExistsException.class)
	public ResponseEntity<ApiErrorMessage> handleUserAlreadyExistsException(UserAlreadyExistsException ex) {
		ApiErrorMessage error = new ApiErrorMessage(
				HttpStatus.BAD_REQUEST.value(),
				ex.getMessage(),
				"USER_ALREADY_EXISTS"
		);
		errorLogger.logError("USER", "USER_ALREADY_EXISTS", "Error: " + ex.getMessage());
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
	}

	@ExceptionHandler(RoleNotFoundException.class)
	public ResponseEntity<ApiErrorMessage> handleRoleNotFoundException(RoleNotFoundException ex) {
		ApiErrorMessage error = new ApiErrorMessage(
				HttpStatus.NOT_FOUND.value(),
				ex.getMessage(),
				"ROLE_NOT_FOUND"
		);
		errorLogger.logError("ROLE", "ROLE_NOT_FOUND", "Error: " + ex.getMessage());
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
	}

	@ExceptionHandler(RoleAlreadyExistsException.class)
	public ResponseEntity<ApiErrorMessage> handleRoleAlreadyExistsException(RoleAlreadyExistsException ex) {
		ApiErrorMessage error = new ApiErrorMessage(
				HttpStatus.BAD_REQUEST.value(),
				ex.getMessage(),
				"ROLE_ALREADY_EXISTS"
		);
		errorLogger.logError("ROLE", "ROLE_ALREADY_EXISTS", "Error: " + ex.getMessage());
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
	}

	@ExceptionHandler(PermissionNotFoundException.class)
	public ResponseEntity<ApiErrorMessage> handlePermissionNotFoundException(PermissionNotFoundException ex) {
		ApiErrorMessage error = new ApiErrorMessage(
				HttpStatus.NOT_FOUND.value(),
				ex.getMessage(),
				"PERMISSION_NOT_FOUND"
		);
		errorLogger.logError("PERMISSION", "PERMISSION_NOT_FOUND", "Error: " + ex.getMessage());
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
	}

	@ExceptionHandler(PermissionAlreadyExistsException.class)
	public ResponseEntity<ApiErrorMessage> handlePermissionAlreadyExistsException(PermissionAlreadyExistsException ex) {
		ApiErrorMessage error = new ApiErrorMessage(
				HttpStatus.BAD_REQUEST.value(),
				ex.getMessage(),
				"PERMISSION_ALREADY_EXISTS"
		);
		errorLogger.logError("PERMISSION", "PERMISSION_ALREADY_EXISTS", "Error: " + ex.getMessage());
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
	}

	@ExceptionHandler(TokenDecodingException.class)
	public ResponseEntity<ApiErrorMessage> handleTokenDecodingException(TokenDecodingException ex) {
		ApiErrorMessage error = new ApiErrorMessage(
				HttpStatus.UNAUTHORIZED.value(),
				ex.getMessage(),
				"TOKEN_DECODING"
		);
		errorLogger.logError("TOKEN", "TOKEN_DECODING", "Error: " + ex.getMessage());
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
	}


	@ExceptionHandler(InvalidAccessTokenException.class)
	public ResponseEntity<ApiErrorMessage> handleInvalidAccessTokenException(InvalidAccessTokenException ex) {
		ApiErrorMessage error = new ApiErrorMessage(
				HttpStatus.UNAUTHORIZED.value(),
				ex.getMessage(),
				"INVALID_ACCESS_TOKEN"
		);
		errorLogger.logError("ACCESS_TOKEN", "INVALID_ACCESS_TOKEN", "Error: " + ex.getMessage());
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
	}

	@ExceptionHandler(InvalidRefreshTokenException.class)
	public ResponseEntity<ApiErrorMessage> handleInvalidRefreshTokenException(InvalidRefreshTokenException ex) {
		ApiErrorMessage error = new ApiErrorMessage(
				HttpStatus.UNAUTHORIZED.value(),
				ex.getMessage(),
				"INVALID_REFRESH_TOKEN"
		);
		errorLogger.logError("REFRESH_TOKEN", "INVALID_REFRESH_TOKEN", "Error: " + ex.getMessage());
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
	}

}
