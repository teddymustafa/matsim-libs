package org.matsim.application.analysis.population;

import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.matsim.application.CommandSpec;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.options.CsvOptions;
import org.matsim.application.options.InputOptions;
import org.matsim.application.options.OutputOptions;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import java.io.*;
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
	requires = {"trips.csv", "config.xml"},
	produces = {"elasticity_stats.csv"}
)
public class ElasticityAnalysis implements MATSimAppCommand {

	// Creating Log
	private static final Logger log = LogManager.getLogger(ElasticityAnalysis.class);
	private static final String MAIN_MODE = "main_mode";
	public static final String PERSON = "person";
	public static final String TRIP_NUMBER = "trip_number";
	public static final String TRAVELED_DISTANCE = "traveled_distance";

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
	private Table tripsCurated;
	private Config config;
	private double betaMoney;

	// RESULTS, NEEDED FOR OUTPUT
	private final Object2IntMap<String> nPersons = new Object2IntOpenHashMap<>();
	private final Object2IntMap<String> nTrips = new Object2IntOpenHashMap<>();
	private final Object2DoubleMap<String> modeShare = new Object2DoubleOpenHashMap<>();
	private final Object2DoubleMap<String> avgDistance = new Object2DoubleOpenHashMap<>();
	private final Object2DoubleMap<String> monetaryCost = new Object2DoubleOpenHashMap<>();
	private final Object2DoubleMap<String> elasticity = new Object2DoubleOpenHashMap<>();

	public static void main(String[] args) throws Exception {
//		new ElasticityAnalysis().call(); | FOR HARDCODING
		new ElasticityAnalysis().execute(args);
	}

	@Override
	public Integer call() throws Exception {

		config = ConfigUtils.loadConfig(input.getPath("config.xml"));
		betaMoney = config.scoring().getScoringParameters(null).getMarginalUtilityOfMoney();

		tripsCurated = Table.read().csv(
			CsvReadOptions.builder(IOUtils.getBufferedReader(input.getPath("trips.csv")))
				.columnTypesPartial(getColumnTypes())
				.sample(false)
				.separator(CsvOptions.detectDelimiter(input.getPath("trips.csv")))
				.build());

		calcnTrips(modes);
		log.info("nTrips successfully calculated");
		calcnPersons(modes);
		log.info("nPersons successfully calculated");
		calcModeSharePerMode(modes);
		log.info("modeShare successfully calculated");
		calcAvgDistPerMode(modes);
		log.info("Avg Distance successfully calculated");
		calcMonCostPerTripPerMode(modes);
		log.info("Monetary Cost per Trip successfully calculated");
		calcElasticityByMode(modes);
		log.info("Elasticity succesfully calculated");
		writeElasticityStats();

		return 0;
	}

	private static Map<String, ColumnType> getColumnTypes() {
		Map<String, ColumnType> columnTypes = new HashMap<>(Map.of(
			PERSON, ColumnType.STRING,
			MAIN_MODE, ColumnType.STRING,
			TRIP_NUMBER, ColumnType.INTEGER,
			TRAVELED_DISTANCE, ColumnType.DOUBLE));

		return columnTypes;
	}

	private void calcnTrips(Set<String> modes){
		int nTripsTotal = tripsCurated.rowCount();
		log.info("nTripsTotal is "+ nTripsTotal);
		for (String mode: modes){

			int trip = tripsCurated.where(tripsCurated.stringColumn(MAIN_MODE).isEqualTo(mode)).rowCount();
			nTrips.put(mode, trip);
			log.info("nTrips for "+ mode +" = "+ trip);
		}
	}

	private void calcnPersons(Set<String> modes){
		int nPersonTotal = tripsCurated.stringColumn(PERSON).countUnique();
		log.info("nPersonTotal is "+ nPersonTotal);
		for (String mode: modes){

			int person = tripsCurated.where(tripsCurated.stringColumn(MAIN_MODE).isEqualTo(mode))
				.stringColumn(PERSON)
				.countUnique();
			nPersons.put(mode, person);
			log.info("nPersons for "+ mode +" = "+ person);
		}
	}

	/**
	 * calculate monetary distance rate by mode.
	 */
	private double getMonDistRateByMode(String mode) throws IOException {

		return config.scoring()
			.getScoringParameters(null)
			.getModes()
			.get(mode)
			.getMonetaryDistanceRate();

	}

	/**
	 * calculate ModeShare
	 * */
	private void calcModeSharePerMode(Set<String> modes) throws IOException{

		for(String mode: modes ){
			int tripsOfMode = nTrips.getInt(mode);
			double share = (double) tripsOfMode / tripsCurated.rowCount(); // nur Persons in tripsCurated filtrieren
			modeShare.put(mode, share);
			log.info("modeShare for "+ mode +" = "+ share);
		}
	}

	/**
	 * calculate Average Distance by mode
	 * */
	private void calcAvgDistPerMode(Set<String> modes) throws IOException{

		for(String mode : modes){
			double avgDist = tripsCurated.doubleColumn(TRAVELED_DISTANCE)
				.where(tripsCurated.stringColumn(MAIN_MODE).isEqualTo(mode))
				.mean();
			avgDistance.put(mode, avgDist);
			log.info("avgDistance for "+ mode +" = "+ avgDist);
		}
	}

	/**
	 * calculate Monetary cost per trip by mode
	 * */
	private void calcMonCostPerTripPerMode(Set<String> modes) throws IOException{

		for(String mode : modes){
			double price = getMonDistRateByMode(mode) * avgDistance.getDouble(mode);
			monetaryCost.put(mode, price);
			log.info("monetaryCostPerTrip for "+ mode +" = "+ price);
		}
	}

	/**
	* calculate elasticity by mode.
	 */
	private void calcElasticityByMode(Set<String> modes) throws IOException {

		 for (String mode: modes){

			 double e = -betaMoney * (monetaryCost.getDouble(mode) * (1-modeShare.getDouble(mode)));
			 log.info("elasticity for {} = {}", mode, e);
			 elasticity.put(mode, e);

		 }
	}

	/**
	 * write elasticity_stats.csv
	 */
	private void writeElasticityStats() throws IOException{
		try (BufferedWriter writer = IOUtils.getBufferedWriter(output.getPath("elasticity_stats.csv").toString())){

			writer.write("#Total persons: " + tripsCurated.stringColumn(PERSON).countUnique());
			writer.newLine();
			writer.write("#Total trips: " + tripsCurated.rowCount());
			writer.newLine();
			writer.write("mode;nPersons;nTrips;modeshare;monetarycost;avg_distance;elasticity");
			writer.newLine();
			for (String mode:modes){
				writer.write(mode + ";" + nPersons.getInt(mode) + ";" + nTrips.getInt(mode) + ";" + modeShare.getDouble(mode) + ";" + monetaryCost.getDouble(mode) + ";" + avgDistance.getDouble(mode)+ ";" +elasticity.getDouble(mode));
				writer.newLine();
			}
		}
		log.info("write complete!");
	}

}
