package org.matsim.simwrapper.dashboard;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.matsim.application.analysis.population.ElasticityAnalysis;

import org.matsim.simwrapper.*;
import org.matsim.simwrapper.viz.*;
import tech.tablesaw.plotly.components.Axis;
import tech.tablesaw.plotly.traces.BarTrace;


import java.util.*;

/**
 * Dashboard with general overview.
 */
public class ElasticityDashboard implements Dashboard {

	private static final String TAB_ELASTICITY = "Elasticity";
	private static final String TAB_GROUPS = "By Groups";

	/** Group columns written by ElasticityAnalysis (placeholder %s in elasticity_stats_%s.csv). */
	private static final List<String> CATEGORIES = List.of("age_group", "income_group", "economic_status", "employment");

	/** dist_group value of the rows covering the whole group (all distances). */
	private static final String ALL_DIST = "all";

	private static final Logger log = LogManager.getLogger(ElasticityDashboard.class);

	private final List<String> args = new ArrayList<>();

	/**
	 * Default constructor: all default settings (like {@code new TripDashboard()}).
	 * Needed for SimWrapper / Guice / DashboardProvider, which create the dashboard without arguments.
	 */
	public ElasticityDashboard() {
	}

	@Override
	public double priority() {
		return 1;
	}


	@Override
	public void configure(Header header, Layout layout, SimWrapperConfigGroup configGroup) {

		header.title = "Price Elasticity of Demand";
		header.description = "How travel demand changes in response to a change in price";

		String[] a = args.toArray(new String[0]);

		createElasticityTab(layout, a);
		createGroupedTab(layout, a);
	}

	/**
	 * Tab "Elasticity": general information for all persons (elasticity_stats.csv).
	 */
	private void createElasticityTab(Layout layout, String[] a) {

		layout.row("info", TAB_ELASTICITY)
			.el(TextBlock.class, (viz, data) -> {
				viz.title = "About";
				viz.content = """
					Price Elasticity of Demand (PED) measures changes in travel demand in response to changes in price (tariffs) with the help of multinomial logit model:

					**E = β_money · (monetaryDistanceRate · avg. distance) · (1 − mode share)**

					- β_money and monetaryDistanceRate are taken from the scoring parameters of the run.
					- Mode share and average distance are taken from the simulated trips of the subpopulation *person*.
					- A value of −0.5 means: +1 % cost of this mode → −0.5 % demand for this mode.
					""";
			});

		layout.row("table", TAB_ELASTICITY)
			.el(Table.class, (viz, data) -> {
				viz.title = "Elasticity stats per mode";
				viz.dataset = data.compute(ElasticityAnalysis.class, "elasticity_stats.csv", a);
				viz.showAllRows = true;
			});

		layout.row("overview", TAB_ELASTICITY)
			.el(Plotly.class, (viz, data) -> {
				viz.title = "Mode share";
				viz.description = "basis of the (1 − share) term";
				viz.layout = tech.tablesaw.plotly.components.Layout.builder()
					.barMode(tech.tablesaw.plotly.components.Layout.BarMode.STACK)
					.build();

				Plotly.DataMapping ds = viz.addDataset(data.compute(ElasticityAnalysis.class, "elasticity_stats.csv", a))
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
					.yAxis(Axis.builder().title("Elasticity").build())
					.build();

				Plotly.DataMapping ds = viz.addDataset(data.compute(ElasticityAnalysis.class, "elasticity_stats.csv", a))
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

			// Elasticity share: one stacked bar per group (all distances), like "Mode share" in TripDashboard
			layout.row("category_1_" + cat, TAB_GROUPS)
				.el(Plotly.class, (viz, data) -> {

					viz.title = "PED share";
					viz.description = "by " + label.toLowerCase();
					viz.height = 6d;
					viz.layout = tech.tablesaw.plotly.components.Layout.builder()
						.barMode(tech.tablesaw.plotly.components.Layout.BarMode.STACK)
						.yAxis(Axis.builder().title("Elasticity").build())
						.build();

					Plotly.DataMapping ds = viz.addDataset(
							data.computeWithPlaceholder(ElasticityAnalysis.class, "elasticity_stats_%s.csv", cat, a))
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

			// Elasticity distance distribution: like "Modal distance distribution" in TripDashboard
			layout.row("category_2_" + cat, TAB_GROUPS)
				.el(Plotly.class, (viz, data) -> {

					viz.title = "PED distance distribution";
					viz.description = "by " + label.toLowerCase();
					viz.height = 6d;
					viz.layout = tech.tablesaw.plotly.components.Layout.builder()
						.barMode(tech.tablesaw.plotly.components.Layout.BarMode.STACK)
						.xAxis(Axis.builder().title("Distance group").build())
						.yAxis(Axis.builder().title("Elasticity").build())
						.build();

					viz.interactive = Plotly.Interactive.dropdown;



					Plotly.DataMapping ds = viz.addDataset(
							data.computeWithPlaceholder(ElasticityAnalysis.class, "elasticity_stats_%s.csv", cat, a))
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

