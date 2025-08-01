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

package org.springframework.boot.actuate.security;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.security.authentication.event.AbstractAuthenticationEvent;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.authentication.event.LogoutSuccessEvent;
import org.springframework.security.web.authentication.switchuser.AuthenticationSwitchUserEvent;
import org.springframework.util.ClassUtils;

/**
 * Default implementation of {@link AbstractAuthenticationAuditListener}.
 *
 * @author Dave Syer
 * @author Vedran Pavic
 * @since 1.0.0
 */
public class AuthenticationAuditListener extends AbstractAuthenticationAuditListener {

	/**
	 * Authentication success event type.
	 */
	public static final String AUTHENTICATION_SUCCESS = "AUTHENTICATION_SUCCESS";

	/**
	 * Authentication failure event type.
	 */
	public static final String AUTHENTICATION_FAILURE = "AUTHENTICATION_FAILURE";

	/**
	 * Authentication switch event type.
	 */
	public static final String AUTHENTICATION_SWITCH = "AUTHENTICATION_SWITCH";

	/**
	 * Logout success event type.
	 *
	 * @since 3.4.0
	 */
	public static final String LOGOUT_SUCCESS = "LOGOUT_SUCCESS";

	private static final String WEB_LISTENER_CHECK_CLASS = "org.springframework.security.web.authentication.switchuser.AuthenticationSwitchUserEvent";

	private final @Nullable WebAuditListener webListener = maybeCreateWebListener();

	private static @Nullable WebAuditListener maybeCreateWebListener() {
		if (ClassUtils.isPresent(WEB_LISTENER_CHECK_CLASS, null)) {
			return new WebAuditListener();
		}
		return null;
	}

	@Override
	public void onApplicationEvent(AbstractAuthenticationEvent event) {
		if (event instanceof AbstractAuthenticationFailureEvent failureEvent) {
			onAuthenticationFailureEvent(failureEvent);
		}
		else if (this.webListener != null && this.webListener.accepts(event)) {
			this.webListener.process(this, event);
		}
		else if (event instanceof AuthenticationSuccessEvent successEvent) {
			onAuthenticationSuccessEvent(successEvent);
		}
		else if (event instanceof LogoutSuccessEvent logoutSuccessEvent) {
			onLogoutSuccessEvent(logoutSuccessEvent);
		}
	}

	private void onAuthenticationFailureEvent(AbstractAuthenticationFailureEvent event) {
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("type", event.getException().getClass().getName());
		data.put("message", event.getException().getMessage());
		if (event.getAuthentication().getDetails() != null) {
			data.put("details", event.getAuthentication().getDetails());
		}
		publish(new AuditEvent(event.getAuthentication().getName(), AUTHENTICATION_FAILURE, data));
	}

	private void onAuthenticationSuccessEvent(AuthenticationSuccessEvent event) {
		Map<String, Object> data = new LinkedHashMap<>();
		if (event.getAuthentication().getDetails() != null) {
			data.put("details", event.getAuthentication().getDetails());
		}
		publish(new AuditEvent(event.getAuthentication().getName(), AUTHENTICATION_SUCCESS, data));
	}

	private void onLogoutSuccessEvent(LogoutSuccessEvent event) {
		Map<String, Object> data = new LinkedHashMap<>();
		if (event.getAuthentication().getDetails() != null) {
			data.put("details", event.getAuthentication().getDetails());
		}
		publish(new AuditEvent(event.getAuthentication().getName(), LOGOUT_SUCCESS, data));
	}

	private static final class WebAuditListener {

		void process(@Nullable AuthenticationAuditListener listener, AbstractAuthenticationEvent input) {
			if (listener != null) {
				AuthenticationSwitchUserEvent event = (AuthenticationSwitchUserEvent) input;
				Map<String, Object> data = new HashMap<>();
				if (event.getAuthentication().getDetails() != null) {
					data.put("details", event.getAuthentication().getDetails());
				}
				if (event.getTargetUser() != null) {
					data.put("target", event.getTargetUser().getUsername());
				}
				listener.publish(new AuditEvent(event.getAuthentication().getName(), AUTHENTICATION_SWITCH, data));
			}

		}

		boolean accepts(AbstractAuthenticationEvent event) {
			return event instanceof AuthenticationSwitchUserEvent;
		}

	}

}
