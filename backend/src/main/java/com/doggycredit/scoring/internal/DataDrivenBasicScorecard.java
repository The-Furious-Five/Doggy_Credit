package com.doggycredit.scoring.internal;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import com.doggycredit.scoring.BasicScoreResult;
import com.doggycredit.scoring.BasicScorecard;
import com.doggycredit.scoring.ScoreBand;
import com.doggycredit.scoring.ScoreReason;
import com.doggycredit.scoring.ScorecardInput;

/** Evalúa la tabla classpath:scorecard/v1.yaml, independiente de ONNX. */
@Component
class DataDrivenBasicScorecard implements BasicScorecard {

	private static final String DEFINITION_PATH = "scorecard/v1.yaml";

	private final Definition definition;

	DataDrivenBasicScorecard() {
		this.definition = loadDefinition();
	}

	@Override
	public BasicScoreResult score(ScorecardInput input) {
		List<ScoreReason> reasons = new ArrayList<>();
		int points = definition.baseScore();
		for (Rule rule : definition.rules()) {
			int value = valueOf(rule.variable(), input);
			Bucket bucket = rule.buckets().stream()
				.filter(candidate -> value <= candidate.max())
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("Scorecard sin bucket para " + rule.variable()));
			points += bucket.points();
			reasons.add(new ScoreReason(bucket.reason(), bucket.points()));
		}
		int score = Math.max(0, Math.min(1000, points));
		ScoreBand band = definition.bands().stream()
			.filter(candidate -> score >= candidate.min())
			.findFirst()
			.map(Band::letter)
			.orElse(ScoreBand.E);
		reasons.sort(Comparator.comparingInt((ScoreReason reason) -> Math.abs(reason.pointImpact())).reversed());
		return new BasicScoreResult(score, band, List.copyOf(reasons), definition.version());
	}

	private int valueOf(String variable, ScorecardInput input) {
		return switch (variable) {
			case "maxDaysPastDue12m" -> input.maxDaysPastDue12m();
			case "activitySeniorityMonths" -> input.activitySeniorityMonths();
			case "financialSystemSeniorityMonths" -> input.financialSystemSeniorityMonths();
			case "requestedAmountIncomeMonths" -> requestedAmountIncomeMonths(input);
			case "termMonths" -> input.termMonths();
			case "bureauRecordFound" -> input.bureauRecordFound() ? 1 : 0;
			default -> throw new IllegalStateException("Variable desconocida en scorecard: " + variable);
		};
	}

	private int requestedAmountIncomeMonths(ScorecardInput input) {
		if (input.monthlyIncome().signum() <= 0) {
			return Integer.MAX_VALUE;
		}
		var months = input.requestedAmount().divide(input.monthlyIncome(), 0, RoundingMode.CEILING);
		return months.compareTo(java.math.BigDecimal.valueOf(Integer.MAX_VALUE)) > 0
				? Integer.MAX_VALUE : months.intValue();
	}

	@SuppressWarnings("unchecked")
	private Definition loadDefinition() {
		try (InputStream input = DataDrivenBasicScorecard.class.getClassLoader()
				.getResourceAsStream(DEFINITION_PATH)) {
			if (input == null) {
				throw new IllegalStateException("No se encontró " + DEFINITION_PATH);
			}
			Map<String, Object> raw = new Yaml(new SafeConstructor(new LoaderOptions())).load(input);
			String version = String.valueOf(raw.get("version"));
			int baseScore = number(raw.get("baseScore"));
			List<Band> bands = ((List<Map<String, Object>>) raw.get("bands")).stream()
				.map(item -> new Band(ScoreBand.valueOf(String.valueOf(item.get("letter"))), number(item.get("min"))))
				.sorted(Comparator.comparingInt(Band::min).reversed())
				.toList();
			List<Rule> rules = ((List<Map<String, Object>>) raw.get("rules")).stream().map(item -> {
				List<Bucket> buckets = ((List<Map<String, Object>>) item.get("buckets")).stream()
					.map(bucket -> new Bucket(number(bucket.get("max")), number(bucket.get("points")),
							String.valueOf(bucket.get("reason"))))
					.sorted(Comparator.comparingInt(Bucket::max))
					.toList();
				return new Rule(String.valueOf(item.get("variable")), buckets);
			}).toList();
			if (bands.size() != 5 || rules.size() < 3) {
				throw new IllegalStateException("Definición de scorecard incompleta");
			}
			return new Definition(version, baseScore, bands, rules);
		}
		catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
	}

	private int number(Object value) {
		return ((Number) value).intValue();
	}

	private record Definition(String version, int baseScore, List<Band> bands, List<Rule> rules) {
	}

	private record Band(ScoreBand letter, int min) {
	}

	private record Rule(String variable, List<Bucket> buckets) {
	}

	private record Bucket(int max, int points, String reason) {
	}

}
