package com.doggycredit.customer;

import java.util.List;

/** Una página del listado de solicitantes y el total que cumple el filtro. */
public record ApplicantPage(List<ApplicantProfile> items, long total) {

	public ApplicantPage {
		items = List.copyOf(items);
	}

}
