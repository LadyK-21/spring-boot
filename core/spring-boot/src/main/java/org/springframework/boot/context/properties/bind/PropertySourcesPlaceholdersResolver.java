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

package org.springframework.boot.context.properties.bind;

import org.jspecify.annotations.Nullable;

import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.PropertySources;
import org.springframework.util.Assert;
import org.springframework.util.PropertyPlaceholderHelper;
import org.springframework.util.SystemPropertyUtils;

/**
 * {@link PlaceholdersResolver} to resolve placeholders from {@link PropertySources}.
 *
 * @author Phillip Webb
 * @author Madhura Bhave
 * @since 2.0.0
 */
public class PropertySourcesPlaceholdersResolver implements PlaceholdersResolver {

	private final @Nullable Iterable<PropertySource<?>> sources;

	private final PropertyPlaceholderHelper helper;

	public PropertySourcesPlaceholdersResolver(Environment environment) {
		this(getSources(environment), null);
	}

	public PropertySourcesPlaceholdersResolver(@Nullable Iterable<PropertySource<?>> sources) {
		this(sources, null);
	}

	public PropertySourcesPlaceholdersResolver(@Nullable Iterable<PropertySource<?>> sources,
			@Nullable PropertyPlaceholderHelper helper) {
		this.sources = sources;
		this.helper = (helper != null) ? helper
				: new PropertyPlaceholderHelper(SystemPropertyUtils.PLACEHOLDER_PREFIX,
						SystemPropertyUtils.PLACEHOLDER_SUFFIX, SystemPropertyUtils.VALUE_SEPARATOR,
						SystemPropertyUtils.ESCAPE_CHARACTER, true);
	}

	@Override
	public @Nullable Object resolvePlaceholders(@Nullable Object value) {
		if (value instanceof String string) {
			return this.helper.replacePlaceholders(string, this::resolvePlaceholder);
		}
		return value;
	}

	protected @Nullable String resolvePlaceholder(String placeholder) {
		if (this.sources != null) {
			for (PropertySource<?> source : this.sources) {
				Object value = source.getProperty(placeholder);
				if (value != null) {
					return String.valueOf(value);
				}
			}
		}
		return null;
	}

	private static PropertySources getSources(Environment environment) {
		Assert.notNull(environment, "'environment' must not be null");
		Assert.isInstanceOf(ConfigurableEnvironment.class, environment,
				"'environment' must be a ConfigurableEnvironment");
		return ((ConfigurableEnvironment) environment).getPropertySources();
	}

}
