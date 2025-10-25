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
package com.mhs.onlinemarketingplatform.catalog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @author Milad Haghighat Shahedi
 */

@Component
@ConfigurationProperties(prefix = "app.api")
public class ApiProperties {

	private String catalogImagePath;
	private String categoryImagePath;
	private String advertisementImagePath;

	public String getCatalogImagePath() {
		return catalogImagePath;
	}

	public void setCatalogImagePath(String catalogImagePath) {
		this.catalogImagePath = catalogImagePath;
	}

	public String getCategoryImagePath() {
		return categoryImagePath;
	}

	public void setCategoryImagePath(String categoryImagePath) {
		this.categoryImagePath = categoryImagePath;
	}

	public String getAdvertisementImagePath() { return advertisementImagePath; }

	public void setAdvertisementImagePath(String advertisementImagePath) {
		this.advertisementImagePath = advertisementImagePath;
	}
}
