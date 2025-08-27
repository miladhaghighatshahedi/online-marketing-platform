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

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Milad Haghighat Shahedi
 */
@Configuration
public class RabbitMqCategoriesIntegrationConfig {

	public static final String CATEGORY_Q = "categories";

	@Bean
	Binding categoryBinding(Queue categoryQueue, Exchange categoryExchange) {
		return BindingBuilder.bind(categoryQueue).to(categoryExchange).with(CATEGORY_Q).noargs();
	}

	@Bean
	Exchange categoryExchange() {
		return ExchangeBuilder.directExchange(CATEGORY_Q).build();
	}

	@Bean
	Queue categoryQueue() {
		return QueueBuilder.durable(CATEGORY_Q).build();
	}

}
