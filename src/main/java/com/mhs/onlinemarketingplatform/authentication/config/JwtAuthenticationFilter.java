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
package com.mhs.onlinemarketingplatform.authentication.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mhs.onlinemarketingplatform.authentication.jwt.JwtService;
import com.mhs.onlinemarketingplatform.authentication.error.token.InvalidAccessTokenException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
/**
 * @author Milad Haghighat Shahedi
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private final JwtService jwtService;

	public JwtAuthenticationFilter(JwtService jwtService) {
		this.jwtService = jwtService;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
		String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (this.jwtService.isBearerToken(authorizationHeader)) {
			String token = authorizationHeader.substring(7);
			try {
				Jwt decodedJwt = this.jwtService.validateAccessToken(token);
				Authentication authentication = this.jwtService.buildAuthenticationFromJwt(decodedJwt);
				SecurityContextHolder.getContext().setAuthentication(authentication);
			} catch (InvalidAccessTokenException e) {
				SecurityContextHolder.clearContext();
				writeErrorResponse(response, e);
				return;
			}
		}
		filterChain.doFilter(request, response);
	}

	private void writeErrorResponse(HttpServletResponse response, InvalidAccessTokenException ex) throws IOException {
		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		response.setContentType("application/json");

		Map<String,String> errorResponse = Map.of("error:",ex.getMessage(), "errorCode:",ex.getCode().name());

		ObjectMapper mapper = new ObjectMapper();
		mapper.writeValue(response.getWriter(),errorResponse);
	}

}
