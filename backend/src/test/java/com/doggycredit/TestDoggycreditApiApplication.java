package com.doggycredit;

import org.springframework.boot.SpringApplication;

public class TestDoggycreditApiApplication {

	public static void main(String[] args) {
		SpringApplication.from(DoggycreditApiApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
