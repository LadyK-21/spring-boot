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

package org.springframework.boot.jdbc.metadata;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.sql.DataSource;

import org.jspecify.annotations.Nullable;

/**
 * A {@link DataSourcePoolMetadataProvider} implementation that returns the first
 * {@link DataSourcePoolMetadata} that is found by one of its delegate.
 *
 * @author Stephane Nicoll
 * @since 2.0.0
 */
public class CompositeDataSourcePoolMetadataProvider implements DataSourcePoolMetadataProvider {

	private final List<DataSourcePoolMetadataProvider> providers;

	/**
	 * Create a {@link CompositeDataSourcePoolMetadataProvider} instance with an initial
	 * collection of delegates to use.
	 * @param providers the data source pool metadata providers
	 */
	public CompositeDataSourcePoolMetadataProvider(
			@Nullable Collection<? extends DataSourcePoolMetadataProvider> providers) {
		this.providers = (providers != null) ? List.copyOf(providers) : Collections.emptyList();
	}

	@Override
	public @Nullable DataSourcePoolMetadata getDataSourcePoolMetadata(DataSource dataSource) {
		for (DataSourcePoolMetadataProvider provider : this.providers) {
			DataSourcePoolMetadata metadata = provider.getDataSourcePoolMetadata(dataSource);
			if (metadata != null) {
				return metadata;
			}
		}
		return null;
	}

}
