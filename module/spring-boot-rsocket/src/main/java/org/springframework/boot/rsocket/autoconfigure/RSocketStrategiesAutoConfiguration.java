/*
 * Copyright 2012-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.rsocket.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import io.netty.buffer.PooledByteBufAllocator;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.rsocket.messaging.RSocketStrategiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.messaging.rsocket.RSocketStrategies;
import org.springframework.util.ClassUtils;
import org.springframework.web.util.pattern.PathPatternRouteMatcher;

/**
 * {@link EnableAutoConfiguration Auto-configuration} for {@link RSocketStrategies}.
 *
 * @author Brian Clozel
 * @since 4.0.0
 */
@AutoConfiguration(afterName = "org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration")
@ConditionalOnClass({ io.rsocket.RSocket.class, RSocketStrategies.class, PooledByteBufAllocator.class })
public final class RSocketStrategiesAutoConfiguration {

	private static final String PATHPATTERN_ROUTEMATCHER_CLASS = "org.springframework.web.util.pattern.PathPatternRouteMatcher";

	@Bean
	@ConditionalOnMissingBean
	RSocketStrategies rSocketStrategies(ObjectProvider<RSocketStrategiesCustomizer> customizers) {
		RSocketStrategies.Builder builder = RSocketStrategies.builder();
		if (ClassUtils.isPresent(PATHPATTERN_ROUTEMATCHER_CLASS, null)) {
			builder.routeMatcher(new PathPatternRouteMatcher());
		}
		customizers.orderedStream().forEach((customizer) -> customizer.customize(builder));
		return builder.build();
	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass({ ObjectMapper.class, CBORFactory.class })
	@SuppressWarnings({ "removal", "deprecation" })
	protected static class JacksonCborStrategyConfiguration {

		private static final MediaType[] SUPPORTED_TYPES = { MediaType.APPLICATION_CBOR };

		@Bean
		@Order(0)
		@ConditionalOnBean(org.springframework.http.converter.json.Jackson2ObjectMapperBuilder.class)
		RSocketStrategiesCustomizer jacksonCborRSocketStrategyCustomizer(
				org.springframework.http.converter.json.Jackson2ObjectMapperBuilder builder) {
			return (strategy) -> {
				ObjectMapper objectMapper = builder.createXmlMapper(false).factory(new CBORFactory()).build();
				strategy.decoder(
						new org.springframework.http.codec.cbor.Jackson2CborDecoder(objectMapper, SUPPORTED_TYPES));
				strategy.encoder(
						new org.springframework.http.codec.cbor.Jackson2CborEncoder(objectMapper, SUPPORTED_TYPES));
			};
		}

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(ObjectMapper.class)
	@SuppressWarnings({ "removal", "deprecation" })
	protected static class JacksonJsonStrategyConfiguration {

		private static final MediaType[] SUPPORTED_TYPES = { MediaType.APPLICATION_JSON,
				new MediaType("application", "*+json") };

		@Bean
		@Order(1)
		@ConditionalOnBean(ObjectMapper.class)
		RSocketStrategiesCustomizer jacksonJsonRSocketStrategyCustomizer(ObjectMapper objectMapper) {
			return (strategy) -> {
				strategy.decoder(
						new org.springframework.http.codec.json.Jackson2JsonDecoder(objectMapper, SUPPORTED_TYPES));
				strategy.encoder(
						new org.springframework.http.codec.json.Jackson2JsonEncoder(objectMapper, SUPPORTED_TYPES));
			};
		}

	}

}
