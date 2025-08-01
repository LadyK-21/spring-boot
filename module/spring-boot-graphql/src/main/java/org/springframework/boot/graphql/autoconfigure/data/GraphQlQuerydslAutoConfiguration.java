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

package org.springframework.boot.graphql.autoconfigure.data;

import java.util.Collections;
import java.util.List;

import com.querydsl.core.Query;
import graphql.GraphQL;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.graphql.autoconfigure.GraphQlAutoConfiguration;
import org.springframework.boot.graphql.autoconfigure.GraphQlSourceBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.graphql.data.query.QuerydslDataFetcher;
import org.springframework.graphql.execution.GraphQlSource;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

/**
 * {@link EnableAutoConfiguration Auto-configuration} that creates a
 * {@link GraphQlSourceBuilderCustomizer}s to detect Spring Data repositories with
 * Querydsl support and register them as {@code DataFetcher}s for any queries with a
 * matching return type.
 *
 * @author Rossen Stoyanchev
 * @author Brian Clozel
 * @since 4.0.0
 * @see QuerydslDataFetcher#autoRegistrationConfigurer(List, List)
 */
@AutoConfiguration(after = GraphQlAutoConfiguration.class)
@ConditionalOnClass({ GraphQL.class, Query.class, QuerydslDataFetcher.class, QuerydslPredicateExecutor.class })
@ConditionalOnBean(GraphQlSource.class)
public final class GraphQlQuerydslAutoConfiguration {

	@Bean
	GraphQlSourceBuilderCustomizer querydslRegistrar(ObjectProvider<QuerydslPredicateExecutor<?>> executors) {
		RuntimeWiringConfigurer configurer = QuerydslDataFetcher
			.autoRegistrationConfigurer(executors.orderedStream().toList(), Collections.emptyList());
		return (builder) -> builder.configureRuntimeWiring(configurer);
	}

}
