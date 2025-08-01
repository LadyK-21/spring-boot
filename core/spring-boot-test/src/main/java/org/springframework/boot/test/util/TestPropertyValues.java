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

package org.springframework.boot.test.util;

import java.io.Closeable;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.jspecify.annotations.Nullable;

import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.lang.Contract;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Test utilities for adding properties. Properties can be applied to a Spring
 * {@link Environment} or to the {@link System#getProperties() system environment}.
 *
 * @author Madhura Bhave
 * @author Phillip Webb
 * @author Stephane Nicoll
 * @since 2.0.0
 */
public final class TestPropertyValues {

	private static final TestPropertyValues EMPTY = new TestPropertyValues(Collections.emptyMap());

	private final Map<String, Object> properties;

	private TestPropertyValues(Map<String, Object> properties) {
		this.properties = Collections.unmodifiableMap(properties);
	}

	/**
	 * Return a new {@link TestPropertyValues} instance with additional entries.
	 * Name-value pairs can be specified with colon (":") or equals ("=") separators.
	 * @param pairs the property pairs to add
	 * @return a new {@link TestPropertyValues} instance
	 */
	public TestPropertyValues and(String... pairs) {
		return and(Arrays.stream(pairs), Pair::parse);
	}

	/**
	 * Return a new {@link TestPropertyValues} instance with additional entries.
	 * Name-value pairs can be specified with colon (":") or equals ("=") separators.
	 * @param pairs the property pairs to add
	 * @return a new {@link TestPropertyValues} instance
	 * @since 2.4.0
	 */
	public TestPropertyValues and(@Nullable Iterable<String> pairs) {
		return (pairs != null) ? and(StreamSupport.stream(pairs.spliterator(), false)) : this;
	}

	/**
	 * Return a new {@link TestPropertyValues} instance with additional entries.
	 * Name-value pairs can be specified with colon (":") or equals ("=") separators.
	 * @param pairs the property pairs to add
	 * @return a new {@link TestPropertyValues} instance
	 * @since 2.4.0
	 */
	public TestPropertyValues and(@Nullable Stream<String> pairs) {
		return (pairs != null) ? and(pairs, Pair::parse) : this;
	}

	/**
	 * Return a new {@link TestPropertyValues} instance with additional entries.
	 * @param map the map of properties that need to be added to the environment
	 * @return a new {@link TestPropertyValues} instance
	 * @since 2.4.0
	 */
	public TestPropertyValues and(@Nullable Map<String, String> map) {
		return (map != null) ? and(map.entrySet().stream(), Pair::fromMapEntry) : this;
	}

	/**
	 * Return a new {@link TestPropertyValues} instance with additional entries.
	 * @param <T> the stream element type
	 * @param stream the elements that need to be added to the environment
	 * @param mapper a mapper function to convert an element from the stream into a
	 * {@link Pair}
	 * @return a new {@link TestPropertyValues} instance
	 * @since 2.4.0
	 */
	public <T> TestPropertyValues and(@Nullable Stream<T> stream, Function<T, @Nullable Pair> mapper) {
		if (stream == null) {
			return this;
		}
		Map<String, Object> properties = new LinkedHashMap<>(this.properties);
		stream.map(mapper).filter(Objects::nonNull).forEach((pair) -> pair.addTo(properties));
		return new TestPropertyValues(properties);
	}

	/**
	 * Add the properties from the underlying map to the environment owned by an
	 * {@link ApplicationContext}.
	 * @param context the context with an environment to modify
	 */
	public void applyTo(ConfigurableApplicationContext context) {
		applyTo(context.getEnvironment());
	}

	/**
	 * Add the properties from the underlying map to the environment. The default property
	 * source used is {@link MapPropertySource}.
	 * @param environment the environment that needs to be modified
	 */
	public void applyTo(ConfigurableEnvironment environment) {
		applyTo(environment, Type.MAP);
	}

	/**
	 * Add the properties from the underlying map to the environment using the specified
	 * property source type.
	 * @param environment the environment that needs to be modified
	 * @param type the type of {@link PropertySource} to be added. See {@link Type}
	 */
	public void applyTo(ConfigurableEnvironment environment, Type type) {
		applyTo(environment, type, type.applySuffix("test"));
	}

	/**
	 * Add the properties from the underlying map to the environment using the specified
	 * property source type and name.
	 * @param environment the environment that needs to be modified
	 * @param type the type of {@link PropertySource} to be added. See {@link Type}
	 * @param name the name for the property source
	 */
	public void applyTo(ConfigurableEnvironment environment, Type type, String name) {
		Assert.notNull(environment, "'environment' must not be null");
		Assert.notNull(type, "'type' must not be null");
		Assert.notNull(name, "'name' must not be null");
		MutablePropertySources sources = environment.getPropertySources();
		addToSources(sources, type, name);
		ConfigurationPropertySources.attach(environment);
	}

	/**
	 * Add the properties to the {@link System#getProperties() system properties} for the
	 * duration of the {@code action}, restoring previous values when it completes.
	 * @param action the action to take
	 * @since 3.0.0
	 */
	public void applyToSystemProperties(Runnable action) {
		applyToSystemProperties(() -> {
			action.run();
			return null;
		});
	}

	/**
	 * Add the properties to the {@link System#getProperties() system properties} for the
	 * duration of the {@code call}, restoring previous values when it completes.
	 * @param <T> the result type
	 * @param call the call to make
	 * @return the result of the call
	 */
	public <T> T applyToSystemProperties(Callable<T> call) {
		try (SystemPropertiesHandler handler = new SystemPropertiesHandler()) {
			return call.call();
		}
		catch (Exception ex) {
			rethrow(ex);
			throw new IllegalStateException("Original cause not rethrown", ex);
		}
	}

	@SuppressWarnings("unchecked")
	private <E extends Throwable> void rethrow(Throwable e) throws E {
		throw (E) e;
	}

	@SuppressWarnings("unchecked")
	private void addToSources(MutablePropertySources sources, Type type, String name) {
		PropertySource<?> propertySource = sources.get(name);
		if (propertySource != null) {
			if (propertySource.getClass() == type.getSourceClass()) {
				((Map<String, Object>) propertySource.getSource()).putAll(this.properties);
				return;
			}
		}
		Map<String, Object> source = new LinkedHashMap<>(this.properties);
		sources.addFirst((type.equals(Type.MAP) ? new MapPropertySource(name, source)
				: new SystemEnvironmentPropertySource(name, source)));
	}

	/**
	 * Return a new {@link TestPropertyValues} with the underlying map populated with the
	 * given property pairs. Name-value pairs can be specified with colon (":") or equals
	 * ("=") separators.
	 * @param pairs the name-value pairs for properties that need to be added to the
	 * environment
	 * @return the new instance
	 */
	public static TestPropertyValues of(String... pairs) {
		return of(Stream.of(pairs));
	}

	/**
	 * Return a new {@link TestPropertyValues} with the underlying map populated with the
	 * given property pairs. Name-value pairs can be specified with colon (":") or equals
	 * ("=") separators.
	 * @param pairs the name-value pairs for properties that need to be added to the
	 * environment
	 * @return the new instance
	 */
	public static TestPropertyValues of(@Nullable Iterable<String> pairs) {
		return (pairs != null) ? of(StreamSupport.stream(pairs.spliterator(), false)) : empty();
	}

	/**
	 * Return a new {@link TestPropertyValues} with the underlying map populated with the
	 * given property pairs. Name-value pairs can be specified with colon (":") or equals
	 * ("=") separators.
	 * @param pairs the name-value pairs for properties that need to be added to the
	 * environment
	 * @return the new instance
	 */
	public static TestPropertyValues of(@Nullable Stream<String> pairs) {
		return (pairs != null) ? of(pairs, Pair::parse) : empty();
	}

	/**
	 * Return a new {@link TestPropertyValues} with the underlying map populated with the
	 * given map entries.
	 * @param map the map of properties that need to be added to the environment
	 * @return the new instance
	 */
	public static TestPropertyValues of(@Nullable Map<String, String> map) {
		return (map != null) ? of(map.entrySet().stream(), Pair::fromMapEntry) : empty();
	}

	/**
	 * Return a new {@link TestPropertyValues} with the underlying map populated with the
	 * given stream.
	 * @param <T> the stream element type
	 * @param stream the elements that need to be added to the environment
	 * @param mapper a mapper function to convert an element from the stream into a
	 * {@link Pair}
	 * @return the new instance
	 */
	public static <T> TestPropertyValues of(@Nullable Stream<T> stream, Function<T, @Nullable Pair> mapper) {
		return (stream != null) ? empty().and(stream, mapper) : empty();
	}

	/**
	 * Return an empty {@link TestPropertyValues} instance.
	 * @return an empty instance
	 */
	public static TestPropertyValues empty() {
		return EMPTY;
	}

	/**
	 * The type of property source.
	 */
	public enum Type {

		/**
		 * Used for {@link SystemEnvironmentPropertySource}.
		 */
		SYSTEM_ENVIRONMENT(SystemEnvironmentPropertySource.class,
				StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME),

		/**
		 * Used for {@link MapPropertySource}.
		 */
		MAP(MapPropertySource.class, null);

		private final Class<? extends MapPropertySource> sourceClass;

		private final @Nullable String suffix;

		Type(Class<? extends MapPropertySource> sourceClass, @Nullable String suffix) {
			this.sourceClass = sourceClass;
			this.suffix = suffix;
		}

		public Class<? extends MapPropertySource> getSourceClass() {
			return this.sourceClass;
		}

		protected String applySuffix(String name) {
			return (this.suffix != null) ? name + "-" + this.suffix : name;
		}

	}

	/**
	 * A single name value pair.
	 */
	public static final class Pair {

		private final String name;

		private final @Nullable String value;

		private Pair(String name, @Nullable String value) {
			Assert.hasLength(name, "'name' must not be empty");
			this.name = name;
			this.value = value;
		}

		public void addTo(Map<String, Object> properties) {
			properties.put(this.name, this.value);
		}

		public static @Nullable Pair parse(String pair) {
			int index = getSeparatorIndex(pair);
			String name = (index > 0) ? pair.substring(0, index) : pair;
			String value = (index > 0) ? pair.substring(index + 1) : "";
			return of(name.trim(), value.trim());
		}

		private static int getSeparatorIndex(String pair) {
			int colonIndex = pair.indexOf(':');
			int equalIndex = pair.indexOf('=');
			if (colonIndex == -1) {
				return equalIndex;
			}
			if (equalIndex == -1) {
				return colonIndex;
			}
			return Math.min(colonIndex, equalIndex);
		}

		/**
		 * Factory method to create a {@link Pair} from a {@code Map.Entry}.
		 * @param entry the map entry
		 * @return the {@link Pair} instance or {@code null}
		 * @since 2.4.0
		 */
		@Contract("!null -> !null")
		public static @Nullable Pair fromMapEntry(Map.@Nullable Entry<String, String> entry) {
			return (entry != null) ? of(entry.getKey(), entry.getValue()) : null;
		}

		/**
		 * Factory method to create a {@link Pair} from a name and value.
		 * @param name the name
		 * @param value the value
		 * @return the {@link Pair} instance or {@code null}
		 * @since 2.4.0
		 */
		public static @Nullable Pair of(@Nullable String name, @Nullable String value) {
			if (StringUtils.hasLength(name)) {
				return new Pair(name, value);
			}
			return null;
		}

	}

	/**
	 * Handler to apply and restore system properties.
	 */
	private class SystemPropertiesHandler implements Closeable {

		private final Map<String, String> previous;

		SystemPropertiesHandler() {
			this.previous = apply(TestPropertyValues.this.properties);
		}

		private Map<String, String> apply(Map<String, ?> properties) {
			Map<String, String> previous = new LinkedHashMap<>();
			properties.forEach((name, value) -> previous.put(name, setOrClear(name, (String) value)));
			return previous;
		}

		@Override
		public void close() {
			this.previous.forEach(this::setOrClear);
		}

		private String setOrClear(String name, String value) {
			Assert.notNull(name, "'name' must not be null");
			if (!StringUtils.hasLength(value)) {
				return (String) System.getProperties().remove(name);
			}
			return (String) System.getProperties().setProperty(name, value);
		}

	}

}
