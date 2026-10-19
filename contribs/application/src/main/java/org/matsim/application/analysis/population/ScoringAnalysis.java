package org.matsim.application.analysis.population;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.application.CommandSpec;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.options.InputOptions;
import org.matsim.application.options.OutputOptions;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.scenario.ScenarioUtils;
import picocli.CommandLine;

@CommandLine.Command(name = "scoring", description = "Calculates various scoring related metrics.")
@CommandSpec(
	requires = {"persons.csv", "experienced_plans.xml"},
	produces = {
		"person_avgscore.csv","person_avgscore_%s.csv"
	}
)

public class ScoringAnalysis implements MATSimAppCommand {

	private static final Logger log = LogManager.getLogger(ScoringAnalysis.class);

	@CommandLine.Mixin
	private InputOptions input = InputOptions.ofCommand(ScoringAnalysis.class);

	@CommandLine.Mixin
	private OutputOptions output = OutputOptions.ofCommand(ScoringAnalysis.class);

	public static void main(String[] args) {
		new ScoringAnalysis().execute(args);
	}

	@Override
	public Integer call() throws Exception {

		Config config = ConfigUtils.loadConfig(input.getPath("config.xml"));

		Scenario scenario = ScenarioUtils.createScenario(config);

		PopulationReader reader = new PopulationReader(scenario);
		reader.readFile(input.getPath("experienced_plans.xml.gz"));

		log.info("Loaded " + scenario.getPopulation().getPersons().size() + " experienced plans.");

		return 0;
	}
}


