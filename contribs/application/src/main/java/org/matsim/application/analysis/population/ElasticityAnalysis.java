package org.matsim.application.analysis.population;

import it.unimi.dsi.fastutil.ints.Int2DoubleMap;
import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
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
import org.matsim.application.options.CsvOptions;
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
import tech.tablesaw.api.ColumnType;
import tech.tablesaw.api.Table;
import tech.tablesaw.io.csv.CsvReadOptions;


@CommandLine.Command (
	name = "elasticity",
	description = "Generates statistics for elasticity."
)
@CommandSpec(
	requires = {"trips.csv", "mode_share.csv"},
	produces = {"elasticity_stats.csv"}
	// trips_curated.csv is just a Zwischenschritt tbh
)
public class ElasticityAnalysis implements MATSimAppCommand {

	// Creating Log
	private static final Logger log = LogManager.getLogger(ElasticityAnalysis.class);
	private static final File configFile = new File("/home/teddymustafa/Desktop/FG-VSP/elasticity/berlin-v7.1-1pct.output_config.xml");
	private static final Config config = ConfigUtils.loadConfig(configFile.getPath());
	//CommandLine Options

	@CommandLine.Mixin
	private final InputOptions input = InputOptions.ofCommand(ElasticityAnalysis.class);
	@CommandLine.Mixin
	private final OutputOptions output = OutputOptions.ofCommand(ElasticityAnalysis.class);

	@CommandLine.Option(
		names = "--modes-filter",
		split = ",",
		description = "Define which modes should be included into elasticity analysis."
	)
	private Set<String> modes;

	@CommandLine.Option(
		names = "--group-by",
		split = ",",
		description = "Define which Group of Population should be included into elasticity analysis."
	)
	private String groupBy;

	private static final double BETA_MONEY = config.scoring().getScoringParameters(null).getMarginalUtilityOfMoney();
	private int tripCount;


	// TRIPS PER MODE
	private final Object2IntMap<String> tripsPerMode = new Object2IntOpenHashMap<>();
	// DISTANCE PER MODE
	private final Object2DoubleMap<String> distancePerMode = new Object2DoubleOpenHashMap<>();
	// Group trips
	private final Map<String, Object2IntOpenHashMap<String>> tripsPerGroup = new HashMap<>();
	// Group traveled_distance
	private final Map<String, Object2IntOpenHashMap<String>> distancePerGroup = new HashMap<>();
	private final Map<String, Set<String>> personsByMode = new HashMap<>();

	// ERGEBNIS
	private final Object2DoubleMap<String> elasticity = new Object2DoubleOpenHashMap<>();

	private Table tripsMode;

	public static void main() {
		new ElasticityAnalysis().execute();
	}

	@Override
	public Integer call() throws Exception {



		Table tripsCurated = Table.read().csv(CsvReadOptions.builder(IOUtils.getBufferedReader(input.getPath("trips.csv"))).columnTypesPartial(getColumnTypes()).sample(false).separator(CsvOptions.detectDelimiter(input.getPath("trips.csv"))).build());
		double sumDist = tripsCurated.longColumn("traveled_distance").sum();
		int nPersons = tripsCurated.stringColumn("person").countUnique();

		this.tripsMode = tripsCurated.where(
			tripsCurated.stringColumn("main_mode").isIn(modes)
		);

		if(groupBy  != null && groupBy.isBlank())
			throw new IllegalArgumentException("argument was given without a usable value.");

		return 0;
	}

	private static Map<String, ColumnType> getColumnTypes() {
		Map<String, ColumnType> columnTypes = new HashMap<>(Map.of("person", ColumnType.STRING,"main_mode", ColumnType.STRING));

		columnTypes.put("traveled_distance", ColumnType.LONG);

		return columnTypes;
	}



	/**
	 * calculate monetary distance rate by mode.
	 */
	private double calculateMonetaryDistanceRateByMode(String mode) throws IOException {

		return config.scoring()
			.getScoringParameters(null)
			.getModes()
			.get(mode)
			.getMonetaryDistanceRate();

	}
	/**
	 * calculate ModeShare
	 * */
	private double calculateModeSharePerMode(String mode) throws IOException{
		// nPersonsMode
	}
	/**
	 * calculate Monetary cost per trip by mode
	 * */
	private double calculateMonetaryCostPerTripPerMode(String mode) throws IOException{
		double avgDist = tripsMode.longColumn("traveled_distance").where(tripsMode.stringColumn("main_mode").isEqualTo(mode)).mean();
		return calculateMonetaryDistanceRateByMode(mode) * avgDist;
	}

	/**
	* calculate elasticity by mode.
	 */
	private void calculateElasticityByMode(Set<String> relevantModes) throws IOException {

		 for (String mode: modes){

			 double modeShare = calculateModeShare(mode); // personsByMode.get(mode).size();
			 double pricePerMeter = calculatePricePerMeterByMode(mode);
			 double price = calculatePrice(mode);
			 double elasticity = -BETA_MONEY * (price * (1-modeShare));

		 }
	}

	private void writeElasticityStats() throws IOException{}
	private void analyseAndWriteElasticityStatsPerGroup() throws IOException{}

	private Map<String, List<String>> getGroupsOfSubpopulations(Map<String, String> groupsOfSubpopulationsRaw) {
		Map<String, List<String>> groupsOfSubpopulations = new HashMap<>();
		for (Map.Entry<String, String> entry : groupsOfSubpopulationsRaw.entrySet()) {
			List<String> subpops = Arrays.asList(entry.getValue().split(","));
			groupsOfSubpopulations.put(entry.getKey(), subpops);
		}
		return groupsOfSubpopulations;
	}



}
