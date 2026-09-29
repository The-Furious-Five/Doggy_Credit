package com.doggycredit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class DoggycreditApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(DoggycreditApiApplication.class, args);
	}

}
