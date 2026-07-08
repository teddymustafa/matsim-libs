package org.matsim.simwrapper.dashboard;

import org.matsim.api.core.v01.TransportMode;
import org.matsim.application.analysis.LogFileAnalysis;
import org.matsim.application.analysis.population.TripAnalysis;
import org.matsim.application.analysis.traffic.TrafficAnalysis;
import org.matsim.application.prepare.network.CreateAvroNetwork;
import org.matsim.simwrapper.*;
import org.matsim.simwrapper.viz.*;
import tech.tablesaw.plotly.components.Axis;
import tech.tablesaw.plotly.traces.BarTrace;

import java.util.List;
import java.util.Set;

/**
 * Dashboard with general overview.
 */
public class TeddysDashboard implements Dashboard {

	private final Set<String> modes;

	public TeddysDashboard() {
		this(Set.of(TransportMode.car));
	}

	public TeddysDashboard(Set<String> modes) {
		this.modes = modes;
	}
	@Override
	public void configure(Header header, Layout layout, SimWrapperConfigGroup configGroup) {

		String[] argsForTrafficAnalysis = new String[]{"--transport-modes", String.join(",", this.modes)};

		header.title = "Ted's Custom Dashboard";
		header.description = "My version's of overview of the MATSim Berlin v7.1 run :)";

		// Info about the status of the run
		layout.row("warnings").el(TextBlock.class, (viz, data) -> {
			viz.file = data.compute(LogFileAnalysis.class, "status.md");
		});

		layout.row("first").el(Table.class, (viz, data) -> {
			viz.title = "Run Info";
			viz.showAllRows = true;
			viz.dataset = data.compute(LogFileAnalysis.class, "run_info.csv");
			viz.width = 1d;
		}).el(PieChart.class, (viz, data) -> {
			viz.title = "Mode Share";
			viz.description = "at final Iteration; result of the complete population and without filtering by area or person attributes";
			viz.dataset = data.output("(*.)?modestats.csv");
			viz.ignoreColumns = List.of("iteration");
			viz.useLastRow = true;
		});

		layout.row("second").el(Line.class, (viz, data) -> {

			viz.title = "Score";
			viz.dataset = data.output("(*.)?scorestats.csv");
			viz.description = "per Iteration; result of the complete population and without filtering by area or person attributes";
			viz.x = "iteration";
			viz.columns = List.of("avg_executed", "avg_worst", "avg_best");
			viz.xAxisName = "Iteration";
			viz.yAxisName = "Score";

		});

		layout.row("third")
			.el(Area.class, (viz, data) -> {
				viz.title = "Mode Share Progression";
				viz.description = "per Iteration; result of the complete population and without filtering by area or person attributes";
				viz.dataset = data.output("(*.)?modestats.csv");
				viz.x = "iteration";
				viz.xAxisName = "Iteration";
				viz.yAxisName = "Share";
				viz.width = 2d;
			});

		layout.row("perf").el(Bar.class, (viz, data) -> {
			viz.title = "Runtime";
			viz.x = "Iteration";
			viz.xAxisName = "Iteration";
			viz.yAxisName = "Runtime [s]";
			viz.columns = List.of("seconds");
			viz.dataset = data.compute(LogFileAnalysis.class, "runtime_stats.csv");

		});

		layout.row("fourth").el(Table.class, (viz, data) -> {
			viz.title = "Mode Chains";
			viz.description = "Distribution of Multi-modal mode chains, where a chain is the ordered sequence of more than 2 unique transport modes used within a single trip, different ordering of the same combination are not considered.";
			viz.showAllRows = true;
			viz.dataset = data.output("mode_chains.csv");;
			viz.width = 1d;
		});
	}

	@Override
	public double priority() {
		return 1;
	}
}
