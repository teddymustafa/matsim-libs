package org.matsim.simwrapper.dashboard;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.matsim.application.analysis.population.ScoringAnalysis;

import org.matsim.simwrapper.*;
import org.matsim.simwrapper.viz.*;
import tech.tablesaw.plotly.components.Axis;
import tech.tablesaw.plotly.traces.BarTrace;


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

		layout.row("info", "scoring")
			.el(TextBlock.class, (viz, data) -> {
				viz.title = "About";
				viz.content = "Overview of Scoring";
			});

		layout.row("table", "scoring")
			.el(Table.class, (viz, data) -> {
				viz.title = "Scoring stats per mode";
				viz.dataset = data.compute(ScoringAnalysis.class, "person_scorestats.csv", a);
				viz.showAllRows = true;
			});

		layout.row("overview", "scoring")
			.el(Plotly.class, (viz, data) -> {
				viz.title = "Mode share";
				viz.description = "basis of the (1 − share) term";
				viz.layout = tech.tablesaw.plotly.components.Layout.builder()
					.barMode(tech.tablesaw.plotly.components.Layout.BarMode.STACK)
					.build();

				Plotly.DataMapping ds = viz.addDataset(data.compute(ScoringAnalysis.class, "person_scorestats.csv", a))
					.constant("source", "Sim")
					.mapping()
					.name("main_mode")
					.y("source")
					.x("mode_share");

				viz.addTrace(BarTrace.builder(Plotly.OBJ_INPUT, Plotly.INPUT)
					.orientation(BarTrace.Orientation.HORIZONTAL)
					.build(), ds);
			})
			.el(Plotly.class, (viz, data) -> {
				viz.title = "PED per Mode";
				viz.layout = tech.tablesaw.plotly.components.Layout.builder()
					.xAxis(Axis.builder().title("Mode").build())
					.yAxis(Axis.builder().title("Scoring").build())
					.build();

				Plotly.DataMapping ds = viz.addDataset(data.compute(ScoringAnalysis.class, "person_scorestats.csv", a))
					.mapping()
					.name("main_mode")
					.x("main_mode")
					.y("elasticity_cost");

				viz.addTrace(BarTrace.builder(Plotly.OBJ_INPUT, Plotly.INPUT).build(), ds);
			});
	}

	/**
	 * Tab "By Groups": same pattern as TripDashboard#createGroupedTab.
	 * For each category (age, income): header, elasticity share, elasticity distance distribution.
	 */
	private void createGroupedTab(Layout layout, String[] a) {

		for (String cat : CATEGORIES) {

			String label = StringUtils.capitalize(cat.replace("_group", "").replace("_"," "));   // "Age", "Income"

			layout.row("category_header_" + cat, TAB_GROUPS)
				.el(TextBlock.class, (viz, data) -> {
					viz.content = "## **" + label + "**";
					viz.backgroundColor = "transparent";
				});

			// Scoring share: one stacked bar per group (all distances), like "Mode share" in TripDashboard
			layout.row("category_1_" + cat, TAB_GROUPS)
				.el(Plotly.class, (viz, data) -> {

					viz.title = "PED share";
					viz.description = "by " + label.toLowerCase();
					viz.height = 6d;
					viz.layout = tech.tablesaw.plotly.components.Layout.builder()
						.barMode(tech.tablesaw.plotly.components.Layout.BarMode.STACK)
						.yAxis(Axis.builder().title("Scoring").build())
						.build();

					Plotly.DataMapping ds = viz.addDataset(
							data.computeWithPlaceholder(ScoringAnalysis.class, "person_scorestats_%s.csv", cat, a))
						.filter("dist_group", ALL_DIST)          // whole group, all distances
						.constant("source", "Sim")
						.mapping()// one panel per group
						.name("main_mode")                        // one colour per mode
						.x(cat)
						.y("elasticity_cost");

					viz.addTrace(BarTrace.builder(Plotly.OBJ_INPUT, Plotly.INPUT)
						.orientation(BarTrace.Orientation.VERTICAL)
						.build(), ds);
				});

			// Scoring distance distribution: like "Modal distance distribution" in TripDashboard
			layout.row("category_2_" + cat, TAB_GROUPS)
				.el(Plotly.class, (viz, data) -> {

					viz.title = "PED distance distribution";
					viz.description = "by " + label.toLowerCase();
					viz.height = 6d;
					viz.layout = tech.tablesaw.plotly.components.Layout.builder()
						.barMode(tech.tablesaw.plotly.components.Layout.BarMode.STACK)
						.xAxis(Axis.builder().title("Distance group").build())
						.yAxis(Axis.builder().title("Scoring").build())
						.build();

					viz.interactive = Plotly.Interactive.dropdown;



					Plotly.DataMapping ds = viz.addDataset(
							data.computeWithPlaceholder(ScoringAnalysis.class, "person_scorestats_%s.csv", cat, a))
						.filterNotIn("dist_group", "all")
						.mapping()
						.name("main_mode")
						.facetCol(cat)
						.x("dist_group")
						.y("elasticity_cost");

					viz.addTrace(BarTrace.builder(Plotly.OBJ_INPUT, Plotly.INPUT)
						.orientation(BarTrace.Orientation.VERTICAL)
						.build(), ds);
				});
		}
	}
}
