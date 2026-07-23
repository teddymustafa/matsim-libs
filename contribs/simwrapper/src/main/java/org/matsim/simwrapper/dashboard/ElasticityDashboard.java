package org.matsim.simwrapper.dashboard;

import org.matsim.api.core.v01.TransportMode;
import org.matsim.application.analysis.LogFileAnalysis;
import org.matsim.application.analysis.population.ElasticityAnalysis;
import org.matsim.application.analysis.population.StuckAgentAnalysis;
import org.matsim.application.analysis.population.TripAnalysis;
import org.matsim.application.analysis.traffic.TrafficAnalysis;
import org.matsim.application.prepare.network.CreateAvroNetwork;
import org.matsim.simwrapper.*;
import org.matsim.simwrapper.viz.*;
import tech.tablesaw.plotly.components.Axis;
import tech.tablesaw.plotly.traces.BarTrace;
import tech.tablesaw.plotly.traces.PieTrace;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Dashboard with general overview.
 */
public class ElasticityDashboard implements Dashboard {

	private final Set<String> modes;

	public ElasticityDashboard(Set<String> modes) {
		this.modes = modes;
	}
	@Override
	public void configure(Header header, Layout layout, SimWrapperConfigGroup configGroup) {

		String[] argsForTrafficAnalysis = new String[]{"--transport-modes", String.join(",", this.modes)};

		header.title = "Elasticity";
		header.description = "Die Wirkung von Preisänderung auf Berliner Verkehrsmittelwahlverhalten";

		layout.row("Third")
			.el(Bar.class, (viz, data) -> {
				viz.title = "Modeshare by mode";
				viz.dataset = data.compute(ElasticityAnalysis.class, "elasticity_stats.csv",
					"--modes-filter", "car,ride");
				viz.x = "mode";
				viz.columns = List.of("elasticity");
			});

	}

	@Override
	public double priority() {
		return 1;
	}
}
