package com.doggycredit.assessment.internal;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class AssessmentConfiguration {

	@Bean(destroyMethod = "close")
	ExecutorService assessmentCollectionExecutor() {
		return Executors.newVirtualThreadPerTaskExecutor();
	}

}
