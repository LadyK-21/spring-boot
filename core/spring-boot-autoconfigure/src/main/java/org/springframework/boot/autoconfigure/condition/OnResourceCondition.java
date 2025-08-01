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

package org.springframework.boot.autoconfigure.condition;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import org.springframework.boot.autoconfigure.condition.ConditionMessage.Style;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.Assert;
import org.springframework.util.MultiValueMap;

/**
 * {@link Condition} that checks for specific resources.
 *
 * @author Dave Syer
 * @see ConditionalOnResource
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
class OnResourceCondition extends SpringBootCondition {

	@Override
	public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
		MultiValueMap<String, @Nullable Object> attributes = metadata
			.getAllAnnotationAttributes(ConditionalOnResource.class.getName(), true);
		Assert.state(attributes != null, "'attributes' must not be null");
		ResourceLoader loader = context.getResourceLoader();
		List<String> locations = new ArrayList<>();
		List<@Nullable Object> resources = attributes.get("resources");
		Assert.state(resources != null, "'resources' must not be null");
		collectValues(locations, resources);
		Assert.state(!locations.isEmpty(),
				"@ConditionalOnResource annotations must specify at least one resource location");
		List<String> missing = new ArrayList<>();
		for (String location : locations) {
			String resource = context.getEnvironment().resolvePlaceholders(location);
			if (!loader.getResource(resource).exists()) {
				missing.add(location);
			}
		}
		if (!missing.isEmpty()) {
			return ConditionOutcome.noMatch(ConditionMessage.forCondition(ConditionalOnResource.class)
				.didNotFind("resource", "resources")
				.items(Style.QUOTE, missing));
		}
		return ConditionOutcome.match(ConditionMessage.forCondition(ConditionalOnResource.class)
			.found("location", "locations")
			.items(locations));
	}

	private void collectValues(List<String> names, List<@Nullable Object> resources) {
		for (Object resource : resources) {
			Object[] items = (Object[]) resource;
			if (items != null) {
				for (Object item : items) {
					names.add((String) item);
				}
			}
		}
	}

}
