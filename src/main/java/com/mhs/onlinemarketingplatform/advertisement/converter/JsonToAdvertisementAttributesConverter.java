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
package com.mhs.onlinemarketingplatform.advertisement.converter;

import com.mhs.onlinemarketingplatform.advertisement.dto.AdvertisementAttributes;
import org.postgresql.util.PGobject;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * @author Milad Haghighat Shahedi
 */
@ReadingConverter
@Component
public class JsonToAdvertisementAttributesConverter implements Converter<Object, AdvertisementAttributes> {

	private final ObjectMapper mapper;

	public JsonToAdvertisementAttributesConverter(ObjectMapper mapper) {
		this.mapper = mapper;
	}

	@Override
	public AdvertisementAttributes convert(Object source) {
		try {

			String json;

			if (source instanceof PGobject pg) {
				json = pg.getValue();
			} else {
				json = source.toString();
			}

			return mapper.readValue(json, AdvertisementAttributes.class);
		} catch (IOException e) {
			throw new IllegalArgumentException("Failed to read attributes", e);
		}
	}

}
