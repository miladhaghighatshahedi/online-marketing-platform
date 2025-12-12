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

import com.mhs.onlinemarketingplatform.authentication.error.permission.PermissionAlreadyExistsException;
import com.mhs.onlinemarketingplatform.authentication.error.permission.PermissionNotFoundException;
import com.mhs.onlinemarketingplatform.authentication.error.role.RoleAlreadyExistsException;
import com.mhs.onlinemarketingplatform.authentication.error.role.RoleNotFoundException;
import com.mhs.onlinemarketingplatform.authentication.error.token.InvalidAccessTokenException;
import com.mhs.onlinemarketingplatform.authentication.error.token.InvalidRefreshTokenException;
import com.mhs.onlinemarketingplatform.authentication.error.token.TokenDecodingException;
import com.mhs.onlinemarketingplatform.authentication.error.user.UserAlreadyExistsException;
import com.mhs.onlinemarketingplatform.authentication.error.user.UserNotFoundException;
import com.mhs.onlinemarketingplatform.common.ErrorLogger;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ConstraintViolation;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
/**
 * @author Milad Haghighat Shahedi
 */
@RestControllerAdvice
public class AuthenticationExceptionHandler {

	private final ErrorLogger errorLogger;

	public AuthenticationExceptionHandler(ErrorLogger errorLogger) {
		this.errorLogger = errorLogger;
	}


	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ApiErrorMessage> handleIllegalArgumentException(IllegalArgumentException ex) {
		ApiErrorMessage error = new ApiErrorMessage(
				HttpStatus.NOT_FOUND.value(),
				ex.getMessage(),
				"ILLEGALE_ARGUMENT_EXCEPTION",
				null
		);
		errorLogger.logError("ILLEGALE_ARGUMENT_EXCEPTION", "ILLEGALE_ARGUMENT_EXCEPTION", "Error: " + ex.getMessage());
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
	}



	@ExceptionHandler(UserNotFoundException.class)
	public ResponseEntity<ApiErrorMessage> handleUserNotFoundException(UserNotFoundException ex) {
		ApiErrorMessage error = new ApiErrorMessage(
				HttpStatus.NOT_FOUND.value(),
				ex.getMessage(),
				"USER_NOT_FOUND",
				null
		);
		errorLogger.logError("USER", "USER_NOT_FOUND", "Error: " + ex.getMessage());
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
	}

	@ExceptionHandler(UserAlreadyExistsException.class)
	public ResponseEntity<ApiErrorMessage> handleUserAlreadyExistsException(UserAlreadyExistsException ex) {
		ApiErrorMessage error = new ApiErrorMessage(
				HttpStatus.BAD_REQUEST.value(),
				ex.getMessage(),
				"USER_ALREADY_EXISTS",
				null
		);
		errorLogger.logError("USER", "USER_ALREADY_EXISTS", "Error: " + ex.getMessage());
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
	}



	@ExceptionHandler(RoleNotFoundException.class)
	public ResponseEntity<ApiErrorMessage> handleRoleNotFoundException(RoleNotFoundException ex) {
		ApiErrorMessage error = new ApiErrorMessage(
				HttpStatus.NOT_FOUND.value(),
				ex.getMessage(),
				"ROLE_NOT_FOUND",
				null
		);
		errorLogger.logError("ROLE", "ROLE_NOT_FOUND", "Error: " + ex.getMessage());
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
	}

	@ExceptionHandler(RoleAlreadyExistsException.class)
	public ResponseEntity<ApiErrorMessage> handleRoleAlreadyExistsException(RoleAlreadyExistsException ex) {
		ApiErrorMessage error = new ApiErrorMessage(
				HttpStatus.BAD_REQUEST.value(),
				ex.getMessage(),
				"ROLE_ALREADY_EXISTS",
				null
		);
		errorLogger.logError("ROLE", "ROLE_ALREADY_EXISTS", "Error: " + ex.getMessage());
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
	}



	@ExceptionHandler(PermissionNotFoundException.class)
	public ResponseEntity<ApiErrorMessage> handlePermissionNotFoundException(PermissionNotFoundException ex) {
		ApiErrorMessage error = new ApiErrorMessage(
				HttpStatus.NOT_FOUND.value(),
				ex.getMessage(),
				"PERMISSION_NOT_FOUND",
				null
		);
		errorLogger.logError("PERMISSION", "PERMISSION_NOT_FOUND", "Error: " + ex.getMessage());
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
	}

	@ExceptionHandler(PermissionAlreadyExistsException.class)
	public ResponseEntity<ApiErrorMessage> handlePermissionAlreadyExistsException(PermissionAlreadyExistsException ex) {
		ApiErrorMessage error = new ApiErrorMessage(
				HttpStatus.BAD_REQUEST.value(),
				ex.getMessage(),
				"PERMISSION_ALREADY_EXISTS",
				null
		);
		errorLogger.logError("PERMISSION", "PERMISSION_ALREADY_EXISTS", "Error: " + ex.getMessage());
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
	}



	@ExceptionHandler(TokenDecodingException.class)
	public ResponseEntity<ApiErrorMessage> handleTokenDecodingException(TokenDecodingException ex) {
		ApiErrorMessage error = new ApiErrorMessage(
				HttpStatus.UNAUTHORIZED.value(),
				ex.getMessage(),
				"TOKEN_DECODING",
				null
		);
		errorLogger.logError("TOKEN", "TOKEN_DECODING", "Error: " + ex.getMessage());
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
	}

	@ExceptionHandler(InvalidAccessTokenException.class)
	public ResponseEntity<ApiErrorMessage> handleInvalidAccessTokenException(InvalidAccessTokenException ex) {
		ApiErrorMessage error = new ApiErrorMessage(
				HttpStatus.UNAUTHORIZED.value(),
				ex.getMessage(),
				"INVALID_ACCESS_TOKEN",
				null
		);
		errorLogger.logError("ACCESS_TOKEN", "INVALID_ACCESS_TOKEN", "Error: " + ex.getMessage());
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
	}

	@ExceptionHandler(InvalidRefreshTokenException.class)
	public ResponseEntity<ApiErrorMessage> handleInvalidRefreshTokenException(InvalidRefreshTokenException ex) {
		ApiErrorMessage error = new ApiErrorMessage(
				HttpStatus.UNAUTHORIZED.value(),
				ex.getMessage(),
				"INVALID_REFRESH_TOKEN",
				null
		);
		errorLogger.logError("REFRESH_TOKEN", "INVALID_REFRESH_TOKEN", "Error: " + ex.getMessage());
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
	}



	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ApiErrorMessage> handleConstraintViolationException(ConstraintViolationException ex) {
		Map<String,List<String>> constrainErrors = ex.getConstraintViolations()
				.stream()
				.collect(Collectors.groupingBy(
						violation -> violation.getPropertyPath().toString(),
						Collectors.mapping(
								ConstraintViolation::getMessage,
								Collectors.toList())
				));
		ApiErrorMessage error = new ApiErrorMessage(
				HttpStatus.BAD_REQUEST.value(),
				"VALIDATION_FAILED",
				"PATH_VARIABLE_INVALID",
				constrainErrors
		);
		errorLogger.logError("VALIDATION_FAILED", "PATH_VARIABLE_INVALID", "Error: " + ex.getMessage());
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiErrorMessage> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
		Map<String, List<String>> validationErrors = ex.getBindingResult()
				.getFieldErrors()
				.stream()
				.collect(Collectors.groupingBy(
						FieldError::getField,
						Collectors.mapping(
								FieldError::getDefaultMessage,
								Collectors.toList()
						)
				));
		ApiErrorMessage error = new ApiErrorMessage(
				HttpStatus.BAD_REQUEST.value(),
				"VALIDATION_FAILED",
				"PATH_VARIABLE_INVALID",
				validationErrors
		);

		errorLogger.logError("VALIDATION_FAILED", "PATH_VARIABLE_INVALID", "Error: " + ex.getMessage());
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ApiErrorMessage> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException ex) {
		ApiErrorMessage response = new ApiErrorMessage(
				HttpStatus.BAD_REQUEST.value(),
				"VALIDATION_FAILED",
				"PATH_VARIABLE_TYPE_MISMATCH",
				Map.of(ex.getName(), List.of("Expected type: " + ex.getRequiredType().getSimpleName()))
		);


		errorLogger.logError("VALIDATION_FAILED", "PATH_VARIABLE_TYPE_MISMATCH", "Error: " + ex.getMessage());
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<ApiErrorMessage> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex) {
		ApiErrorMessage response = new  ApiErrorMessage(
				HttpStatus.BAD_REQUEST.value(),
				"VALIDATION_FAILED",
				"EMPTY_REQUEST_BODY",
				null
		);
		errorLogger.logError("VALIDATION_FAILED", "EMPTY_REQUEST_BODY", "Error: " + ex.getMessage());
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);

	}

	@ExceptionHandler(MissingPathVariableException.class)
	ResponseEntity<ApiErrorMessage> handleHttpMissingPathVariableException(MissingPathVariableException ex) {
		ApiErrorMessage response = new  ApiErrorMessage(
				HttpStatus.BAD_REQUEST.value(),
				"VALIDATION_FAILED",
				"MISSING_PATH_VARIABLE",
				null
		);
		errorLogger.logError("VALIDATION_FAILED", "MISSING_PATH_VARIABLE", "Error: " + ex.getMessage());
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	ResponseEntity<ApiErrorMessage> handleMethodNotSupportedException(HttpRequestMethodNotSupportedException ex) {
		ApiErrorMessage response = new  ApiErrorMessage(
				HttpStatus.BAD_REQUEST.value(),
				"HTTP_METHOD_NOT_SUPPORTED",
				"HTTP_METHOD_NOT_SUPPORTED",
				null
		);
		errorLogger.logError("HTTP_METHOD_NOT_SUPPORTED", "HTTP_METHOD_NOT_SUPPORTED", "Error: " + ex.getMessage());
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
	}




}
