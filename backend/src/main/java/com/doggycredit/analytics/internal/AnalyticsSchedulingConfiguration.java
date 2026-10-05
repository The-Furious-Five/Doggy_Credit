package com.doggycredit.analytics.internal;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** D-08: habilita el job semanal de deriva ({@link ModelDriftService#runWeekly()}). */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class AnalyticsSchedulingConfiguration {
}
