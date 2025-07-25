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

package smoketest.webflux;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Integration test for WebFlux actuator when using an isolated {@link ObjectMapper}.
 *
 * @author Phillip Webb
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "management.endpoints.jackson.isolated-object-mapper=false")
@ContextConfiguration(loader = ApplicationStartupSpringBootContextLoader.class)
class SampleWebFluxApplicationActuatorIsolatedObjectMapperFalseTests {

	@Autowired
	private WebTestClient webClient;

	@Test
	void linksEndpointShouldBeAvailable() {
		this.webClient.get()
			.uri("/actuator/startup")
			.accept(MediaType.APPLICATION_JSON)
			.exchange()
			.expectStatus()
			.is5xxServerError();
	}

}
