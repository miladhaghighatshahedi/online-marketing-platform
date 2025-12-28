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
package com.mhs.onlinemarketingplatform.authentication;

import com.github.f4b6a3.uuid.UuidCreator;
import com.mhs.onlinemarketingplatform.authentication.config.JwtAuthenticationFilter;
import com.mhs.onlinemarketingplatform.authentication.error.AuthenticationExceptionHandler;
import com.mhs.onlinemarketingplatform.common.ErrorLogger;
import com.mhs.onlinemarketingplatform.JaksonConfig;
import org.apache.catalina.security.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
/**
 * @author Milad Haghighat Shahedi
 */
@WebMvcTest(value = PermissionController.class,
		excludeFilters = {
				@ComponentScan.Filter(
						type = FilterType.ASSIGNABLE_TYPE,
						classes = {
								SecurityConfig.class,
								JwtAuthenticationFilter.class,
						}
				)
		})
@AutoConfigureMockMvc(addFilters = false)
@Import({JaksonConfig.class, AuthenticationExceptionHandler.class})
public class PermissionControllerUnitTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private PermissionService permissionService;

	@MockitoBean
	private ErrorLogger errorLogger;

	private UUID id;
    private LocalDateTime fixedTime;

	@BeforeEach
	void setUp() {
		id = UuidCreator.getTimeOrderedEpoch();
		fixedTime = LocalDateTime.of(2025,1,1,12,0,0);
	}


	@Test
	void add_method_ReturnResponse_WhenSuccessfull() throws Exception {
		PermissionResponse response = new PermissionResponse(id,"ADD_ADVERTISEMENT_PERM",fixedTime,fixedTime);

		when(this.permissionService.add(any(AddPermissionRequest.class))).thenReturn(response);

		String jsonData = """
                    {
                    "name": "ADD_ADVERTISEMENT_PERM"
                    }
				""";

		this.mockMvc.perform(post("/api/admin/permissions/add").contentType(MediaType.APPLICATION_JSON).content(jsonData))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.response").value(true))
				.andExpect(jsonPath("$.message").value("Permission saved successfully!"))
				.andExpect(jsonPath("$.data.id").value(id.toString()))
				.andExpect(jsonPath("$.data.name").value("ADD_ADVERTISEMENT_PERM"))
				.andExpect(jsonPath("$.data.createdAt").value("2025-01-01 12:00"))
				.andExpect(jsonPath("$.data.lastUpdatedAt").value("2025-01-01 12:00"));
	}

	@Test
	void add_method_ThrowMethodArgumentNotValidExceptionException_WhenEmptyString() throws Exception {
		String jsonData = """
                    {
                    "name": ""
                    }
				""";

		this.mockMvc.perform(post("/api/admin/permissions/add").contentType(MediaType.APPLICATION_JSON).content(jsonData))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
				.andExpect(jsonPath("$.message").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.code").value("PATH_VARIABLE_INVALID"))
				.andExpect(jsonPath("$.error.name").value("must not be blank"));
	}

	@Test
	void add_methodthrowMethodArgumentNotValidExceptionException_WhenWhiteSpace() throws Exception {
		String jsonData = """
                    {
                    "name": " "
                    }
				""";

		this.mockMvc.perform(post("/api/admin/permissions/add").contentType(MediaType.APPLICATION_JSON).content(jsonData))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
				.andExpect(jsonPath("$.message").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.code").value("PATH_VARIABLE_INVALID"))
				.andExpect(jsonPath("$.error.name").value("must not be blank"));
	}

	@Test
	void add_method_ThrowMethodArgumentNotValidExceptionException_WhenNull() throws Exception {
		String jsonData = """
                    {
                    "name": null
                    }
				""";

		this.mockMvc.perform(post("/api/admin/permissions/add").contentType(MediaType.APPLICATION_JSON).content(jsonData))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
				.andExpect(jsonPath("$.message").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.code").value("PATH_VARIABLE_INVALID"))
				.andExpect(jsonPath("$.error.name").value("must not be blank"));
	}

	@Test
	void add_method_ThrowHttpMessageNotReadableException_WhenNoRequestBody() throws Exception {
		this.mockMvc.perform(post("/api/admin/permissions/add"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
				.andExpect(jsonPath("$.message").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.code").value("EMPTY_REQUEST_BODY"));
	}



	@Test
	void updateMethod_ReturnResponse_WhenSuccessfull() throws Exception {
		UUID id = UUID.fromString("79e784ec-b22d-456c-807f-300a21bffc2f");
		PermissionResponse response = new PermissionResponse(id,"ADD_ADVERTISEMENT_PERM_UPDATED",fixedTime,fixedTime);

		when(this.permissionService.update(any(UpdatePermissionRequest.class))).thenReturn(response);

		String jsonData = """
                    {
                    "id": "79e784ec-b22d-456c-807f-300a21bffc2f",
                    "name": "ADD_ADVERTISEMENT_PERM_UPDATED"
                    }
				""";

		this.mockMvc.perform(put("/api/admin/permissions/update").contentType(MediaType.APPLICATION_JSON).content(jsonData))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.response").value(true))
				.andExpect(jsonPath("$.message").value("Permission updated successfully!"))
				.andExpect(jsonPath("$.data.id").value(id.toString()))
				.andExpect(jsonPath("$.data.name").value("ADD_ADVERTISEMENT_PERM_UPDATED"))
				.andExpect(jsonPath("$.data.createdAt").value("2025-01-01 12:00"))
				.andExpect(jsonPath("$.data.lastUpdatedAt").value("2025-01-01 12:00"));

	}

	@Test
	void update_method_ThrowMethodArgumentNotValidExceptionException_WhenIdIsEmptyString() throws Exception {
		String jsonData = """
                    {
                    "id": "",
                    "name": "ADD_ADVERTISEMENT_PERM_UPDATED"
                    }
				""";

		this.mockMvc.perform(put("/api/admin/permissions/update").contentType(MediaType.APPLICATION_JSON).content(jsonData))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
				.andExpect(jsonPath("$.message").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.code").value("EMPTY_REQUEST_BODY"));
	}

	@Test
	void update_method_ThrowMethodArgumentNotValidExceptionException_WhenIsIdWhiteSpace() throws Exception {
		String jsonData = """
                    {
                    "id": " ",
                    "name": "ADD_ADVERTISEMENT_PERM_UPDATED"
                    }
				""";

		this.mockMvc.perform(put("/api/admin/permissions/update").contentType(MediaType.APPLICATION_JSON).content(jsonData))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
				.andExpect(jsonPath("$.message").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.code").value("EMPTY_REQUEST_BODY"));
	}

	@Test
	void update_method_ThrowMethodArgumentNotValidExceptionException_WhenIdIsNull() throws Exception {
		String jsonData = """
                    {
                    "id": null,
                    "name": "ADD_ADVERTISEMENT_PERM_UPDATED"
                    }
				""";

		this.mockMvc.perform(put("/api/admin/permissions/update").contentType(MediaType.APPLICATION_JSON).content(jsonData))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
				.andExpect(jsonPath("$.message").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.code").value("PATH_VARIABLE_INVALID"))
				.andExpect(jsonPath("$.error.id").value("must not be null"));
	}

	@Test
	void update_method_ThrowMethodArgumentNotValidExceptionException_WhenNoId() throws Exception {
		String jsonData = """
                    {
                    "name": "ADD_ADVERTISEMENT_PERM_UPDATED"
                    }
				""";

		this.mockMvc.perform(put("/api/admin/permissions/update").contentType(MediaType.APPLICATION_JSON).content(jsonData))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
				.andExpect(jsonPath("$.message").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.code").value("PATH_VARIABLE_INVALID"))
				.andExpect(jsonPath("$.error.id").value("must not be null"));
	}

	@Test
	void update_method_ThrowMethodArgumentNotValidExceptionException_WhenNameIsEmpty() throws Exception {
		String jsonData = """
                    {
                    "id": "79e784ec-b22d-456c-807f-300a21bffc2f",
                    "name": ""
                    }
				""";

		this.mockMvc.perform(put("/api/admin/permissions/update").contentType(MediaType.APPLICATION_JSON).content(jsonData))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
				.andExpect(jsonPath("$.message").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.code").value("PATH_VARIABLE_INVALID"))
				.andExpect(jsonPath("$.error.name").value("must not be blank"));
	}

	@Test
	void update_method_ThrowMethodArgumentNotValidExceptionException_WhenNameIsWhiteSpace() throws Exception {
		String jsonData = """
                    {
                    "id": "79e784ec-b22d-456c-807f-300a21bffc2f",
                    "name": " "
                    }
				""";

		this.mockMvc.perform(put("/api/admin/permissions/update").contentType(MediaType.APPLICATION_JSON).content(jsonData))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
				.andExpect(jsonPath("$.message").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.code").value("PATH_VARIABLE_INVALID"))
				.andExpect(jsonPath("$.error.name").value("must not be blank"));
	}

	@Test
	void update_method_ThrowMethodArgumentNotValidExceptionException_WhenNameIsNull() throws Exception {
		String jsonData = """
                    {
                    "id": "79e784ec-b22d-456c-807f-300a21bffc2f",
                    "name": null
                    }
				""";

		this.mockMvc.perform(put("/api/admin/permissions/update").contentType(MediaType.APPLICATION_JSON).content(jsonData))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
				.andExpect(jsonPath("$.message").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.code").value("PATH_VARIABLE_INVALID"))
				.andExpect(jsonPath("$.error.name").value("must not be blank"));
	}

	@Test
	void update_method_ThrowMethodArgumentNotValidExceptionException_WhenNoName() throws Exception {
		String jsonData = """
                    {
                    "id": "79e784ec-b22d-456c-807f-300a21bffc2f"
                    }
				""";

		this.mockMvc.perform(put("/api/admin/permissions/update").contentType(MediaType.APPLICATION_JSON).content(jsonData))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
				.andExpect(jsonPath("$.message").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.code").value("PATH_VARIABLE_INVALID"))
				.andExpect(jsonPath("$.error.name").value("must not be blank"));
	}

	@Test
	void update_method_ThrowMethodArgumentNotValidExceptionException_WhenNoRequestBody() throws Exception {

		this.mockMvc.perform(put("/api/admin/permissions/update"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
				.andExpect(jsonPath("$.message").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.code").value("EMPTY_REQUEST_BODY"));
	}




	@Test
    void delete_method_ReturnResponse_WhenSuccessfull() throws Exception {

		 doNothing().when(this.permissionService).delete(id);

		this.mockMvc.perform(delete("/api/admin/permissions/{id}",id))
				.andDo(print())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.response").value(true))
				.andExpect(jsonPath("$.message").value("Permission deleted successfully!"));
    }

	@Test
	void delete_method_ThrowMethodArgumentTypeMismatchException_WhenWrongType_integer() throws Exception {
		this.mockMvc.perform(delete("/api/admin/permissions/{id}",123))
				.andDo(print())
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("Bad Request"))
				.andExpect(jsonPath("$.message").value("Invalid path variable 'id': 123"));
	}

	@Test
	void delete_method_ThrowMethodArgumentTypeMismatchException_WhenWrongType_String() throws Exception {
		this.mockMvc.perform(delete("/api/admin/permissions/{id}","xxxx"))
				.andDo(print())
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("Bad Request"))
				.andExpect(jsonPath("$.message").value("Invalid path variable 'id': xxxx"));
	}

	@Test
	void delete_method_MethodArgumentTypeMismatchException_WhenWhiteSpace_1() throws Exception {
		this.mockMvc.perform(delete("/api/admin/permissions/{id}"," "))
				.andDo(print())
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
				.andExpect(jsonPath("$.message").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.code").value("MISSING_PATH_VARIABLE"));
	}

	@Test
	void delete_method_ThrowMissingPathVariableException_WhenWhiteSpace_2() throws Exception {
		this.mockMvc.perform(delete("/api/admin/permissions/ "))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
				.andExpect(jsonPath("$.message").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.code").value("MISSING_PATH_VARIABLE"));
	}

	@Test
	void delete_method_ThrowMissingPathVariableException_WhenNoPathVariable() throws Exception {
		this.mockMvc.perform(delete("/api/admin/permissions/")).andExpect(status().isNotFound());
	}

	@Test
	void delete_method_ThrowMMethodNotSupportedException_WhenWrongURI() throws Exception {
		this.mockMvc.perform(delete("/api/admin/permissions"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
				.andExpect(jsonPath("$.message").value("HTTP_METHOD_NOT_SUPPORTED"))
				.andExpect(jsonPath("$.code").value("HTTP_METHOD_NOT_SUPPORTED"));
	}




	@Test
	void findAllPaged_method_ReturnResponse_WhenSuccessfull() throws Exception {

		PermissionResponse permissionResponse1 = new PermissionResponse(id,"ADD_ADVERTISEMENT_PERM",fixedTime,fixedTime);
		PermissionResponse permissionResponse2 = new PermissionResponse(id,"UPDATE_ADVERTISEMENT_PERM",fixedTime,fixedTime);
		PermissionResponse permissionResponse3 = new PermissionResponse(id,"DELETE_ADVERTISEMENT_PERM",fixedTime,fixedTime);

		PermissionPageResponse<PermissionResponse> permissionPageResponse = new PermissionPageResponse<>(
				List.of(permissionResponse1, permissionResponse2, permissionResponse3), 0, 6, 3, 2);

		PageRequest expected = PageRequest.of(0, 6);

		when(this.permissionService.findAllPaged(expected)).thenReturn(permissionPageResponse);

		this.mockMvc.perform(get("/api/admin/permissions/paged").param("page", "0").param("size", "6"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").isArray())
				.andExpect(jsonPath("$.content[0].name").value("ADD_ADVERTISEMENT_PERM"))
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(6))
				.andExpect(jsonPath("$.totalElements").value(3))
				.andExpect(jsonPath("$.totalPage").value(2));
	}

	@Test
	void fetchById_method_ReturnResponse_WhenSuccessfull() throws Exception {

		PermissionResponse response = new PermissionResponse(id,"ADD_ADVERTISEMENT_PERM",fixedTime,fixedTime);

		when(this.permissionService.fetchById(id)).thenReturn(response);

		this.mockMvc.perform(get("/api/admin/permissions/{id}",id))
				.andDo(print())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.response").value(true))
				.andExpect(jsonPath("$.message").value("Permission found successfully!"))
				.andExpect(jsonPath("$.data.name").value("ADD_ADVERTISEMENT_PERM"));
	}

	@Test
	void findByName_method_ReturnResponse_WhenSuccessfull() throws Exception {

		PermissionResponse response = new PermissionResponse(id,"ADD_ADVERTISEMENT_PERM",fixedTime,fixedTime);

		when(this.permissionService.findByName("ADD_ADVERTISEMENT_PERM")).thenReturn(response);

		this.mockMvc.perform(get("/api/admin/permissions").param("name","ADD_ADVERTISEMENT_PERM"))
				.andDo(print())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.response").value(true))
				.andExpect(jsonPath("$.message").value("Permission found successfully!"))
				.andExpect(jsonPath("$.data.name").value("ADD_ADVERTISEMENT_PERM"));
	}

	@Test
	void fetchAllMapToSet_method_ReturnResponse_WhenSuccessfull() throws Exception {

		PermissionResponse p1 = new PermissionResponse(id,"ADD_ADVERTISEMENT_PERM",fixedTime,fixedTime);
		PermissionResponse p2 = new PermissionResponse(id,"UPDATE_ADVERTISEMENT_PERM",fixedTime,fixedTime);
		PermissionResponse p3 = new PermissionResponse(id,"DELETE_ADVERTISEMENT_PERM",fixedTime,fixedTime);
		Set<PermissionResponse> responses = Set.of(p1,p2,p3);

		when(this.permissionService.fetchAllMapToSet()).thenReturn(responses);

		this.mockMvc.perform(get("/api/admin/permissions"))
				.andDo(print())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.response").value(true))
				.andExpect(jsonPath("$.message").value("Permissions found successfully!"))
				.andExpect(jsonPath("$.data").isArray())
				.andExpect(jsonPath("$.data.size()").value(3));
//				.andExpect(jsonPath("$.data[0].name").value("ADD_ADVERTISEMENT_PERM"));

	}

}
