package com.handyai.build;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

// Users sign in through AuthService and JWTs, so Boot's generated in-memory user is not wanted.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class HandyAIApplication {

	public static void main(String[] args) {
		SpringApplication.run(HandyAIApplication.class, args);
	}

}
