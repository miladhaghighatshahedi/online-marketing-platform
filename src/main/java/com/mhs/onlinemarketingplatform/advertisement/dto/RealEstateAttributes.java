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
package com.mhs.onlinemarketingplatform.advertisement.dto;


import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.io.IOException;
import java.math.BigDecimal;

/**
 * @author Milad Haghighat Shahedi
 */
@JsonTypeName("realestate")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
public record RealEstateAttributes(
		double area,
		int rooms,
		int floor,
		int yearOfConstruction,
		String buildingType,
		@JsonDeserialize(using = BigDecimalPlainDeserializer.class)
		BigDecimal pricePerSquarMeter,
		String condtion,
		boolean balcony,
		boolean storageUnit,
		boolean elevator,
        boolean parking,
		String restRoomType,
		String heatingSystem,
		String coolingSystem) implements AdvertisementAttributes {}


class BigDecimalPlainDeserializer extends JsonDeserializer<BigDecimal> {
	@Override
	public BigDecimal deserialize(JsonParser p, DeserializationContext ctxt)
			throws IOException {
		return new BigDecimal(p.getText());
	}
}
