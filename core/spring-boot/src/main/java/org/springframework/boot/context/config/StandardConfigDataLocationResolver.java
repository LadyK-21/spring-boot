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

package org.springframework.boot.context.config;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.jspecify.annotations.Nullable;

import org.springframework.boot.context.config.LocationResourceLoader.ResourceType;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.PropertySourceLoader;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.core.log.LogMessage;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ResourceUtils;
import org.springframework.util.StringUtils;

/**
 * {@link ConfigDataLocationResolver} for standard locations.
 *
 * @author Madhura Bhave
 * @author Phillip Webb
 * @author Scott Frederick
 * @author Sijun Yang
 * @since 2.4.0
 */
public class StandardConfigDataLocationResolver
		implements ConfigDataLocationResolver<StandardConfigDataResource>, Ordered {

	private static final String PREFIX = "resource:";

	static final String CONFIG_NAME_PROPERTY = "spring.config.name";

	static final String[] DEFAULT_CONFIG_NAMES = { "application" };

	private static final Pattern URL_PREFIX = Pattern.compile("^([a-zA-Z][a-zA-Z0-9*]*?:)(.*$)");

	private static final @Nullable String NO_PROFILE = null;

	private final Log logger;

	private final List<PropertySourceLoader> propertySourceLoaders;

	private final String[] configNames;

	private final LocationResourceLoader resourceLoader;

	/**
	 * Create a new {@link StandardConfigDataLocationResolver} instance.
	 * @param logFactory the factory for loggers to use
	 * @param binder a binder backed by the initial {@link Environment}
	 * @param resourceLoader a {@link ResourceLoader} used to load resources
	 */
	public StandardConfigDataLocationResolver(DeferredLogFactory logFactory, Binder binder,
			ResourceLoader resourceLoader) {
		this.logger = logFactory.getLog(StandardConfigDataLocationResolver.class);
		this.propertySourceLoaders = SpringFactoriesLoader.loadFactories(PropertySourceLoader.class,
				resourceLoader.getClassLoader());
		this.configNames = getConfigNames(binder);
		this.resourceLoader = new LocationResourceLoader(resourceLoader);
	}

	private String[] getConfigNames(Binder binder) {
		String[] configNames = binder.bind(CONFIG_NAME_PROPERTY, String[].class).orElse(DEFAULT_CONFIG_NAMES);
		for (String configName : configNames) {
			validateConfigName(configName);
		}
		return configNames;
	}

	private void validateConfigName(String name) {
		Assert.state(!name.contains("*"), () -> "Config name '" + name + "' cannot contain '*'");
	}

	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE;
	}

	@Override
	public boolean isResolvable(ConfigDataLocationResolverContext context, ConfigDataLocation location) {
		return true;
	}

	@Override
	public List<StandardConfigDataResource> resolve(ConfigDataLocationResolverContext context,
			ConfigDataLocation location) throws ConfigDataNotFoundException {
		return resolve(getReferences(context, location.split()));
	}

	private Set<StandardConfigDataReference> getReferences(ConfigDataLocationResolverContext context,
			ConfigDataLocation[] configDataLocations) {
		Set<StandardConfigDataReference> references = new LinkedHashSet<>();
		for (ConfigDataLocation configDataLocation : configDataLocations) {
			references.addAll(getReferences(context, configDataLocation));
		}
		return references;
	}

	private Set<StandardConfigDataReference> getReferences(ConfigDataLocationResolverContext context,
			ConfigDataLocation configDataLocation) {
		String resourceLocation = getResourceLocation(context, configDataLocation);
		try {
			if (isDirectory(resourceLocation)) {
				return getReferencesForDirectory(configDataLocation, resourceLocation, NO_PROFILE);
			}
			return getReferencesForFile(configDataLocation, resourceLocation, NO_PROFILE);
		}
		catch (RuntimeException ex) {
			throw new IllegalStateException("Unable to load config data from '" + configDataLocation + "'", ex);
		}
	}

	@Override
	public List<StandardConfigDataResource> resolveProfileSpecific(ConfigDataLocationResolverContext context,
			ConfigDataLocation location, Profiles profiles) {
		return resolve(getProfileSpecificReferences(context, location.split(), profiles));
	}

	private Set<StandardConfigDataReference> getProfileSpecificReferences(ConfigDataLocationResolverContext context,
			ConfigDataLocation[] configDataLocations, Profiles profiles) {
		Set<StandardConfigDataReference> references = new LinkedHashSet<>();
		for (String profile : profiles) {
			for (ConfigDataLocation configDataLocation : configDataLocations) {
				String resourceLocation = getResourceLocation(context, configDataLocation);
				references.addAll(getReferences(configDataLocation, resourceLocation, profile));
			}
		}
		return references;
	}

	private String getResourceLocation(ConfigDataLocationResolverContext context,
			ConfigDataLocation configDataLocation) {
		String resourceLocation = configDataLocation.getNonPrefixedValue(PREFIX);
		boolean isFixedPath = resourceLocation.startsWith("/") || URL_PREFIX.matcher(resourceLocation).matches();
		if (isFixedPath) {
			return resourceLocation;
		}
		ConfigDataResource parent = context.getParent();
		if (parent instanceof StandardConfigDataResource resource) {
			String parentResourceLocation = resource.getReference().getResourceLocation();
			String parentDirectory = parentResourceLocation.substring(0, parentResourceLocation.lastIndexOf("/") + 1);
			return parentDirectory + resourceLocation;
		}
		return resourceLocation;
	}

	private Set<StandardConfigDataReference> getReferences(ConfigDataLocation configDataLocation,
			String resourceLocation, String profile) {
		if (isDirectory(resourceLocation)) {
			return getReferencesForDirectory(configDataLocation, resourceLocation, profile);
		}
		return getReferencesForFile(configDataLocation, resourceLocation, profile);
	}

	private Set<StandardConfigDataReference> getReferencesForDirectory(ConfigDataLocation configDataLocation,
			String directory, @Nullable String profile) {
		Set<StandardConfigDataReference> references = new LinkedHashSet<>();
		for (String name : this.configNames) {
			Deque<StandardConfigDataReference> referencesForName = getReferencesForConfigName(name, configDataLocation,
					directory, profile);
			references.addAll(referencesForName);
		}
		return references;
	}

	private Deque<StandardConfigDataReference> getReferencesForConfigName(String name,
			ConfigDataLocation configDataLocation, String directory, @Nullable String profile) {
		Deque<StandardConfigDataReference> references = new ArrayDeque<>();
		for (PropertySourceLoader propertySourceLoader : this.propertySourceLoaders) {
			for (String extension : propertySourceLoader.getFileExtensions()) {
				StandardConfigDataReference reference = new StandardConfigDataReference(configDataLocation, directory,
						directory + name, profile, extension, propertySourceLoader);
				if (!references.contains(reference)) {
					references.addFirst(reference);
				}
			}
		}
		return references;
	}

	private Set<StandardConfigDataReference> getReferencesForFile(ConfigDataLocation configDataLocation, String file,
			@Nullable String profile) {
		FileExtensionHint fileExtensionHint = FileExtensionHint.from(file);
		if (fileExtensionHint.isPresent()) {
			file = FileExtensionHint.removeFrom(file) + fileExtensionHint;
		}
		for (PropertySourceLoader propertySourceLoader : this.propertySourceLoaders) {
			String fileExtension = getLoadableFileExtension(propertySourceLoader, file);
			if (fileExtension != null) {
				String root = file.substring(0, file.length() - fileExtension.length() - 1);
				StandardConfigDataReference reference = new StandardConfigDataReference(configDataLocation, null, root,
						profile, (!fileExtensionHint.isPresent()) ? fileExtension : null, propertySourceLoader);
				return Collections.singleton(reference);
			}
		}
		if (configDataLocation.isOptional()) {
			return Collections.emptySet();
		}
		if (configDataLocation.hasPrefix(PREFIX) || configDataLocation.hasPrefix(ResourceUtils.FILE_URL_PREFIX)
				|| configDataLocation.hasPrefix(ResourceUtils.CLASSPATH_URL_PREFIX)
				|| configDataLocation.toString().indexOf(':') == -1) {
			throw new IllegalStateException("File extension is not known to any PropertySourceLoader. "
					+ "If the location is meant to reference a directory, it must end in '/' or File.separator");
		}
		throw new IllegalStateException(
				"Incorrect ConfigDataLocationResolver chosen or file extension is not known to any PropertySourceLoader. "
						+ "If the location is meant to reference a directory, it must end in '/' or File.separator. "
						+ "The location is being resolved using the StandardConfigDataLocationResolver, "
						+ "check the location prefix if a different resolver is expected");
	}

	private @Nullable String getLoadableFileExtension(PropertySourceLoader loader, String file) {
		for (String fileExtension : loader.getFileExtensions()) {
			if (StringUtils.endsWithIgnoreCase(file, fileExtension)) {
				return fileExtension;
			}
		}
		return null;
	}

	private boolean isDirectory(String resourceLocation) {
		return resourceLocation.endsWith("/") || resourceLocation.endsWith(File.separator);
	}

	private List<StandardConfigDataResource> resolve(Set<StandardConfigDataReference> references) {
		List<StandardConfigDataResource> resolved = new ArrayList<>();
		for (StandardConfigDataReference reference : references) {
			resolved.addAll(resolve(reference));
		}
		if (resolved.isEmpty()) {
			resolved.addAll(resolveEmptyDirectories(references));
		}
		return resolved;
	}

	private Collection<StandardConfigDataResource> resolveEmptyDirectories(
			Set<StandardConfigDataReference> references) {
		Set<StandardConfigDataResource> empty = new LinkedHashSet<>();
		for (StandardConfigDataReference reference : references) {
			if (reference.getDirectory() != null) {
				empty.addAll(resolveEmptyDirectories(reference));
			}
		}
		return empty;
	}

	private Set<StandardConfigDataResource> resolveEmptyDirectories(StandardConfigDataReference reference) {
		if (!this.resourceLoader.isPattern(reference.getResourceLocation())) {
			return resolveNonPatternEmptyDirectories(reference);
		}
		return resolvePatternEmptyDirectories(reference);
	}

	private Set<StandardConfigDataResource> resolveNonPatternEmptyDirectories(StandardConfigDataReference reference) {
		String directory = reference.getDirectory();
		Assert.state(directory != null, "'directory' must not be null");
		Resource resource = this.resourceLoader.getResource(directory);
		return (resource instanceof ClassPathResource || !resource.exists()) ? Collections.emptySet()
				: Collections.singleton(new StandardConfigDataResource(reference, resource, true));
	}

	private Set<StandardConfigDataResource> resolvePatternEmptyDirectories(StandardConfigDataReference reference) {
		String directory = reference.getDirectory();
		Assert.state(directory != null, "'directory' must not be null");
		Resource[] subdirectories = this.resourceLoader.getResources(directory, ResourceType.DIRECTORY);
		ConfigDataLocation location = reference.getConfigDataLocation();
		if (!location.isOptional() && ObjectUtils.isEmpty(subdirectories)) {
			String message = String.format("Config data location '%s' contains no subdirectories", location);
			throw new ConfigDataLocationNotFoundException(location, message, null);
		}
		return Arrays.stream(subdirectories)
			.filter(Resource::exists)
			.map((resource) -> new StandardConfigDataResource(reference, resource, true))
			.collect(Collectors.toCollection(LinkedHashSet::new));
	}

	private List<StandardConfigDataResource> resolve(StandardConfigDataReference reference) {
		if (!this.resourceLoader.isPattern(reference.getResourceLocation())) {
			return resolveNonPattern(reference);
		}
		return resolvePattern(reference);
	}

	private List<StandardConfigDataResource> resolveNonPattern(StandardConfigDataReference reference) {
		Resource resource = this.resourceLoader.getResource(reference.getResourceLocation());
		if (!resource.exists() && reference.isSkippable()) {
			logSkippingResource(reference);
			return Collections.emptyList();
		}
		return Collections.singletonList(createConfigResourceLocation(reference, resource));
	}

	private List<StandardConfigDataResource> resolvePattern(StandardConfigDataReference reference) {
		List<StandardConfigDataResource> resolved = new ArrayList<>();
		for (Resource resource : this.resourceLoader.getResources(reference.getResourceLocation(), ResourceType.FILE)) {
			if (!resource.exists() && reference.isSkippable()) {
				logSkippingResource(reference);
			}
			else {
				resolved.add(createConfigResourceLocation(reference, resource));
			}
		}
		return resolved;
	}

	private void logSkippingResource(StandardConfigDataReference reference) {
		this.logger.trace(LogMessage.format("Skipping missing resource %s", reference));
	}

	private StandardConfigDataResource createConfigResourceLocation(StandardConfigDataReference reference,
			Resource resource) {
		return new StandardConfigDataResource(reference, resource);
	}

}
