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
	requires = {"trips.csv"},
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
	@CommandLine.Option(names = "--modes-filter", split = ",", description = "Define which modes should be included into elasticity analysis.")
	private Set<String> modes;

//	// HARDCODING FOR TESTING PURPOSE
//	private static final String TRIPS_PATH = "/home/teddymustafa/Desktop/FG-VSP/elasticity/berlin-v7.1-1pct.output_trips.csv.gz";
//	private static final String STATS_OUT_PATH = "/home/teddymustafa/Desktop/FG-VSP/elasticity/analysis/elasticity/elasticity_stats.csv";
//	private final Set<String> modes = Set.of("car","ride");
//	private final String groupBy = null;

	// Shared state across stages -> fields
	private static final double BETA_MONEY = config.scoring().getScoringParameters(null).getMarginalUtilityOfMoney();
	private Table tripsCurated;
	private Table tripsMode;

	// ERGEBNIS
	private final Object2DoubleMap<String> elasticity = new Object2DoubleOpenHashMap<>();
	private final Object2IntMap<String> nPersons = new Object2IntOpenHashMap<>();


	public static void main(String[] args) throws Exception {
//		new ElasticityAnalysis().call();
		new ElasticityAnalysis().execute(args);
	}

	@Override
	public Integer call() throws Exception {

		tripsCurated = Table.read().csv(
			CsvReadOptions.builder(IOUtils.getBufferedReader(input.getPath("trips.csv")))
				.columnTypesPartial(getColumnTypes())
				.sample(false)
				.separator(CsvOptions.detectDelimiter(input.getPath("trips.csv")))
				.build());

		if (modes == null || modes.isEmpty()){
			tripsMode = tripsCurated;
		} else {
		tripsMode = tripsCurated.where(
			tripsCurated.stringColumn("main_mode").isIn(modes));
		}

		calculatenInfo();
		log.info("nPersonsTotal successfully calculated");
		calculatenPersons(modes);
		log.info("nPersons successfully calculated");
		calculateElasticityByMode(modes);
		log.info("calculated elasticity");
		writeElasticityStats();

		return 0;
	}

	private static Map<String, ColumnType> getColumnTypes() {
		Map<String, ColumnType> columnTypes = new HashMap<>(Map.of(
			"person", ColumnType.STRING,
			"main_mode", ColumnType.STRING,
			"trip_number", ColumnType.INTEGER));

		columnTypes.put("traveled_distance", ColumnType.DOUBLE);

		return columnTypes;
	}

	private void calculatenInfo(){
			int nPerson = tripsCurated.stringColumn("person").countUnique();
			int trips = tripsCurated.rowCount();
			int tripsCar = 	tripsCurated.where(tripsCurated.stringColumn("main_mode").isEqualTo("car")).rowCount();
			int tripsRide = tripsCurated.where(tripsCurated.stringColumn("main_mode").isEqualTo("ride")).rowCount();
			log.info("nPersonsTotal is "+ nPerson);
			log.info("nTripsTotal is "+ trips);
			log.info("nTripsCar is "+ tripsCar);
			log.info("nTripsRide is "+ tripsRide);
	}

	private void calculatenPersons(Set<String> modes){
		for (String mode: modes){

			int nPerson = tripsMode.where(tripsMode.stringColumn("main_mode").isEqualTo(mode))
				.stringColumn("person")
				.countUnique();
			nPersons.put(mode, nPerson);
			log.info("nPersons for "+ mode +" = "+ nPerson);

		}
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
		double tripsOfMode = tripsCurated.stringColumn("main_mode").countOccurrences(mode);
		double modeShare = tripsOfMode / tripsCurated.rowCount();
		log.info("modeShare for "+ mode +" = "+ modeShare);
		return modeShare;
	}
	/**
	 * calculate Monetary cost per trip by mode
	 * */
	private double calculateMonetaryCostPerTripPerMode(String mode) throws IOException{
		double avgDist = tripsMode.doubleColumn("traveled_distance")
			.where(tripsMode.stringColumn("main_mode").isEqualTo(mode))
			.mean();
		double price = calculateMonetaryDistanceRateByMode(mode) * avgDist;
		log.info("monetaryCostPerTrip for "+ mode +" = "+ price);
		return price;
	}

	/**
	* calculate elasticity by mode.
	 */
	private void calculateElasticityByMode(Set<String> modes) throws IOException {

		 for (String mode: modes){

			 double modeShare = calculateModeSharePerMode(mode); // personsByMode.get(mode).size();
			 double price = calculateMonetaryCostPerTripPerMode(mode);
			 double e = -BETA_MONEY * (price * (1-modeShare));
			 log.info("elasticity for {} = {}", mode, e);
			 elasticity.put(mode, e);

		 }
	}

	private void writeElasticityStats() throws IOException{
		try (BufferedWriter writer = IOUtils.getBufferedWriter(output.getPath("elasticity_stats.csv").toString())){
			writer.write("mode;nPersons;elasticity");
			writer.newLine();
			for (String mode:modes){
				int trips = tripsMode.stringColumn("main_mode").countOccurrences(mode);
				writer.write(mode + ";" + nPersons.getInt(mode) + ";" + elasticity.getDouble(mode));
				writer.newLine();
			}
		}
		log.info("write complete!");
	}

}
