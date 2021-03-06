package org.ironrhino.core.security.verfication.impl;

import org.apache.commons.lang3.StringUtils;
import org.ironrhino.core.security.verfication.ReceiverNotFoundException;
import org.ironrhino.core.security.verfication.VerificationManager;
import org.ironrhino.core.security.verfication.VerificationService;
import org.ironrhino.core.spring.configuration.ApplicationContextPropertiesConditional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@ApplicationContextPropertiesConditional(key = "verification.code.enabled", value = "true")
@Component("verificationManager")
@Slf4j
public class DefaultVerificationManager implements VerificationManager {

	@Autowired
	private VerificationService verificationService;

	@Autowired
	private UserDetailsService userDetailsService;

	@Override
	public boolean isVerificationRequired(String username) {
		try {
			return isVerificationRequired(userDetailsService.loadUserByUsername(username));
		} catch (UsernameNotFoundException e) {
			return false;
		}
	}

	@Override
	public boolean isPasswordRequired(String username) {
		try {
			return isPasswordRequired(userDetailsService.loadUserByUsername(username));
		} catch (UsernameNotFoundException e) {
			return true;
		}
	}

	@Override
	public void send(String username) {
		String receiver = getReceiver(userDetailsService.loadUserByUsername(username));
		if (StringUtils.isBlank(receiver)) {
			log.warn("user {} receiver is blank", username);
		} else {
			verificationService.send(receiver);
		}
	}

	@Override
	public boolean verify(UserDetails user, String verificationCode) {
		String username = user.getUsername();
		String receiver = getReceiver(user);
		if (StringUtils.isBlank(receiver))
			throw new ReceiverNotFoundException("No receiver found for: " + username);
		return verificationService.verify(receiver, verificationCode);
	}

}
