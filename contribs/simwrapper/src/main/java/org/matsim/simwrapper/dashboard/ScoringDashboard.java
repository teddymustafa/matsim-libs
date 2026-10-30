package org.matsim.simwrapper.dashboard;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.matsim.application.analysis.LogFileAnalysis;
import org.matsim.application.analysis.population.ScoringAnalysis;

import org.matsim.application.analysis.population.TripAnalysis;
import org.matsim.simwrapper.*;
import org.matsim.simwrapper.viz.*;
import tech.tablesaw.plotly.components.Axis;
import tech.tablesaw.plotly.traces.BarTrace;
import tech.tablesaw.plotly.traces.ScatterTrace;


import java.util.*;

public class ScoringDashboard implements Dashboard {

	private static final String TAB_GROUPS = "By Groups";

	/** Group columns written by ScoringAnalysis (placeholder %s in person_scorestats_%s.csv). */
	private static final List<String> CATEGORIES = List.of("age_group", "income_group", "economic_status", "employment");

	/** dist_group value of the rows covering the whole group (all distances). */
	private static final String ALL_DIST = "all";

	private static final Logger log = LogManager.getLogger(ScoringDashboard.class);

	private final List<String> args = new ArrayList<>();


	@Override
	public void configure(Header header, Layout layout, SimWrapperConfigGroup configGroup) {
		header.title =  "Scoring";
		header.description = "An overview of Agent's scoring based on subpopulations and its groups";

		String[] a = args.toArray(new String[0]);

		createOverviewTab(layout, a);
		createGroupedTab(layout, a);
	}

	private void createOverviewTab(Layout layout, String[] a) {

		layout.row("first", "Overview").el(Bar.class, (viz, data) -> {
			viz.title = "Average Score";
			viz.x = "subpopulation";
			viz.xAxisName = "Subpopulation";
			viz.yAxisName = "Avg. Score";
			viz.columns = List.of("avg_plan_score");
			viz.dataset = data.compute(ScoringAnalysis.class, "avgscore_stats.csv");

		});
	}

	/**
	 * Tab "By Groups": same pattern as TripDashboard#createGroupedTab.
	 * For each category (age, income): header, elasticity share, elasticity distance distribution.
	 */
	private void createGroupedTab(Layout layout, String[] a) {

		for (String cat : CATEGORIES) {

			String label = StringUtils.capitalize(cat.replace("_group", "").replace("_", " "));   // "Age", "Income"

			layout.row("category_header_" + cat, TAB_GROUPS)
				.el(TextBlock.class, (viz, data) -> {
					viz.content = "## **" + label + "**";
					viz.backgroundColor = "transparent";
				});

			// Elasticity share: one stacked bar per group (all distances), like "Mode share" in TripDashboard
			layout.row("category_1_" + cat, TAB_GROUPS)
				.el(Plotly.class, (viz, data) -> {

					viz.title = "Average Score";
					viz.description = "by " + label.toLowerCase();
					viz.height = 6d;
					viz.layout = tech.tablesaw.plotly.components.Layout.builder()
						.yAxis(Axis.builder().title("Scoring").build())
						.build();

					Plotly.DataMapping ds = viz.addDataset(
							data.computeWithPlaceholder(ScoringAnalysis.class, "person_avgscore_%s.csv", cat, a))
						.mapping()// one panel per group
						.x(cat)
						.y("avg_plan_score");

					viz.addTrace(ScatterTrace.builder(Plotly.INPUT, Plotly.INPUT)
						.mode(ScatterTrace.Mode.LINE)
						.build(), ds);
				})
				// still figuring out what to do with this...
				.el(Plotly.class, (viz, data) -> {

					viz.title = "Executed Score";
					viz.description = "by " + label.toLowerCase();
					viz.height = 6d;
					viz.layout = tech.tablesaw.plotly.components.Layout.builder()
						.yAxis(Axis.builder().title("Scoring").build())
						.build();

					Plotly.DataMapping ds = viz.addDataset(
							data.computeWithPlaceholder(ScoringAnalysis.class, "person_scorestats_%s.csv", cat, a))
						.mapping()// one panel per group
						.x("executed_score")
						.y(cat);

					viz.addTrace(ScatterTrace.builder(Plotly.INPUT, Plotly.INPUT)
						.mode(ScatterTrace.Mode.LINE)
						.build(), ds);
				});
		}
	}
}
