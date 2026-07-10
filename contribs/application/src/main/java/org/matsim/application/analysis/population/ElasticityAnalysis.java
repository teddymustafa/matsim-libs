package org.matsim.application.analysis.population;

import it.unimi.dsi.fastutil.ints.Int2DoubleMap;
import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.ActivityStartEvent;
import org.matsim.api.core.v01.events.PersonStuckEvent;
import org.matsim.api.core.v01.network.Link;
import org.matsim.application.CommandSpec;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.options.InputOptions;
import org.matsim.application.options.OutputOptions;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.core.utils.io.IOUtils;
import picocli.CommandLine;


@CommandLine.Command (name = "elasticity", description = "Generates statistics for elasticity.")
@CommandSpec(requires = {"trips.csv"}, produces = {"trips_curated.csv","elasticity.csv"})
public class ElasticityAnalysis implements MATSimAppCommand {

	// Creating Log
	private static final Logger log = LogManager.getLogger(ElasticityAnalysis.class);

	// Config and input files used
	private static final File configFile = new File("/home/teddymustafa/Desktop/FG-VSP/elasticity/berlin-v7.1-1pct.output_config.xml");
	private static final Config config = ConfigUtils.loadConfig(configFile.getPath());
	private static final File inputFile = new File("/home/teddymustafa/Desktop/FG-VSP/elasticity/berlin-v7.1-1pct.output_trips.csv.gz"); // ABSOLUTER PFAD HIER
	private static final File outputFile = new File("home/teddymustafa/Desktop/FG-VSP/elasticity/analysis/elasticity/elasticity.csv"); // ABSOLUTER PFAD HIER

	// Miscellaneous: Sets, Maps and things
	// Parsing .csv
	private static final String delimiter = ";";

	// Stores relevantModes, pricePerMeterByMode
	private static final Set<String> RELEVANT_MODES = new LinkedHashSet<>(Set.of("car", "ride"));
	private final Map<String, Double> pricePerMeterByMode = new LinkedHashMap<>();
	private static final double marginalUtilityOfMoney = config.scoring().getScoringParameters(null).getMarginalUtilityOfMoney();
	private static final Map<String, Double> sumDistMode = new LinkedHashMap<>();
	private static final Map<String, Set<String>> personsByMode = new LinkedHashMap<>();

	private final Set<String> allAgents = new HashSet<>();

	public static void main() {
		new ElasticityAnalysis().execute();
	}

	@Override
	public Integer call() throws Exception {

		Files.createDirectories(outputFile.toPath());

		try(Reader reader = IOUtils.getBufferedReader(inputFile.toString());
			CSVParser parser = CSVFormat.DEFAULT.builder()
                 .setHeader()              // reads first row as column names
                 .setSkipHeaderRecord(true)
                 .build()
                 .parse(reader);

			CSVPrinter printer = new CSVPrinter(
			IOUtils.getBufferedWriter(outputFile.toString()), CSVFormat.DEFAULT.builder()
				.setHeader("person","traveled_distance","main_mode","longest_distance_mode")
				.build())) {

			for (String m : RELEVANT_MODES) {
				sumDistMode.put(m, 0.0);
				personsByMode.put(m, new HashSet<>());
			}

			for (CSVRecord record : parser) {
				String person = record.get("person");
				String mode = record.get("main_mode");
				double distance = Double.parseDouble(record.get("traveled_distance"));

				if(RELEVANT_MODES.contains(mode)){
					sumDistMode.put(mode, sumDistMode.get(mode) + distance);
					personsByMode.get(mode).add(person);
				}
			}
		} catch (IOException ex) {
			log.error(ex);
		}
		return 0;
	}

	private Map<String, Double> computePricePerMeterByMode(Set<String> relevantModes){
		for (String mode : RELEVANT_MODES) {
			double rate = config.scoring()
				.getScoringParameters(null)
				.getModes()
				.get(mode)
				.getMonetaryDistanceRate();

			System.out.println("mode = " + mode + ", monetaryDistanceRate = " + rate);
			log.debug("mode={}, monetaryDistanceRate{}", mode, rate);
			pricePerMeterByMode.put(mode, rate);
		}
		return pricePerMeterByMode;
	}


}
