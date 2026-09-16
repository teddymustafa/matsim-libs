package org.matsim.application.analysis.population;

import it.unimi.dsi.fastutil.objects.*;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.matsim.application.CommandSpec;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.options.CsvOptions;
import org.matsim.application.options.InputOptions;
import org.matsim.application.options.OutputOptions;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import java.io.*;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.core.utils.io.IOUtils;
import picocli.CommandLine;
import tech.tablesaw.api.*;
import tech.tablesaw.io.csv.CsvReadOptions;
import tech.tablesaw.io.csv.CsvWriteOptions;
import tech.tablesaw.selection.Selection;


@CommandLine.Command (
	name = "elasticity",
	description = "Generates statistics for Price Elasticity of Demand."
)

@CommandSpec(
	requires = {"trips.csv", "config.xml", "persons.csv"},
	produces = {"elasticity_stats.csv", "elasticity_per_income.csv","elasticity_per_age.csv"}
)
public class ElasticityAnalysis implements MATSimAppCommand {

	// Creating Log
	private static final Logger log = LogManager.getLogger(ElasticityAnalysis.class);

	// Static Final Variables
	private static final String MAIN_MODE = "main_mode";
	private static final String PERSON = "person";
	private static final String TRIP_NUMBER = "trip_number";
	private static final String TRAVELED_DISTANCE = "traveled_distance";
	private static final String AGE = "age";
	private static final String INCOME = "income";
	private static final String SUBPOPULATION = "subpopulation";
	private static final Set<String> INCOME_GROUP = Set.of("low", "middle", "high");

	// Setting the input file
	@CommandLine.Mixin
	private final InputOptions input = InputOptions.ofCommand(ElasticityAnalysis.class);
	// Setting the output file
	@CommandLine.Mixin
	private final OutputOptions output = OutputOptions.ofCommand(ElasticityAnalysis.class);
	// For Filtering Modes
	@CommandLine.Option(names = "--modes-filter", split = ",", description = "Define which modes should be included into elasticity analysis.")
	private Set<String> modes = Set.of("car", "ride");
	// For Filtering Attributes, such as Age and Income
	@CommandLine.Option(names = "--attribute-filter", description = "Define which groups should be included into elasticity analysis.")
	private String attribute;

//	// HARDCODING FOR TESTING PURPOSE
//	private static final String TRIPS_PATH = "/home/teddymustafa/Desktop/FG-VSP/elasticity/berlin-v7.1-1pct.output_trips.csv.gz";
//	private static final String STATS_OUT_PATH = "/home/teddymustafa/Desktop/FG-VSP/elasticity/analysis/elasticity/elasticity_stats.csv";
//	private final Set<String> modes = Set.of("car","ride");
//	private final String groupBy = null;

	// Table to store the calculated numbers
	private static Table trips;
	private Config config;
	private double betaMoney;

	// RESULTS, NEEDED FOR OUTPUT, Factors and Variables of Preis Elasticity of Demand
	private final Object2IntMap<String> nPersons = new Object2IntOpenHashMap<>();
	private final Object2IntMap<String> nTrips = new Object2IntOpenHashMap<>();
	private final Object2IntMap<String> nTripsIncome = new Object2IntOpenHashMap<>();
	private final Object2IntMap<String> nTripsAge = new Object2IntOpenHashMap<>();
	private final Object2DoubleMap<String> modeShare = new Object2DoubleOpenHashMap<>();
	public static record ModeAttributeKey(String mode, String incomeGroup){}
	private final Object2DoubleMap<String> modeShareIncome = new Object2DoubleOpenHashMap<>();
	private final Object2DoubleMap<String> modeShareAge = new Object2DoubleOpenHashMap<>();
	private final Object2DoubleMap<String> incomeShare = new Object2DoubleOpenHashMap<>();
	private final Object2DoubleMap<String> ageShare = new Object2DoubleOpenHashMap<>();
	private final Object2DoubleMap<String> avgDistance = new Object2DoubleOpenHashMap<>();
	private final Object2DoubleMap<String> avgDistanceIncome = new Object2DoubleOpenHashMap<>();
	private final Object2DoubleMap<String> avgDistanceAge = new Object2DoubleOpenHashMap<>();
	private final Object2DoubleMap<String> monetaryCost = new Object2DoubleOpenHashMap<>();
	private final Object2DoubleMap<String> monetaryCostIncome = new Object2DoubleOpenHashMap<>();
	private final Object2DoubleMap<String> monetaryCostAge = new Object2DoubleOpenHashMap<>();
	private final Object2DoubleMap<String> elasticity = new Object2DoubleOpenHashMap<>();
	private final Object2DoubleMap<String> elasticityIncome = new Object2DoubleOpenHashMap<>();
	private final Object2DoubleMap<String> elasticityAge = new Object2DoubleOpenHashMap<>();

	public static void main(String[] args) throws Exception {
//		new ElasticityAnalysis().call(); | FOR HARDCODING
		new ElasticityAnalysis().execute(args);
	}

	@Override
	public Integer call() throws Exception {

		config = ConfigUtils.loadConfig(input.getPath("config.xml"));
		betaMoney = config.scoring().getScoringParameters(null).getMarginalUtilityOfMoney();

		Table persons = Table.read().csv(
			CsvReadOptions.builder(IOUtils.getBufferedReader(input.getPath("persons.csv")))
				.columnTypesPartial(getPersonsColumnTypes())
				.sample(false)
				.separator(CsvOptions.detectDelimiter(input.getPath("persons.csv")))
				.build());

		Table personsFiltered = persons.selectColumns(
			"person", "employment", "economic_status", "subpopulation");

		Table trips = Table.read().csv(
			CsvReadOptions.builder(IOUtils.getBufferedReader(input.getPath("trips.csv")))
				.columnTypesPartial(getTripsColumnTypes())
				.sample(false)
				.separator(CsvOptions.detectDelimiter(input.getPath("trips.csv")))
				.build());

		Table tripsFiltered = trips.selectColumns(
			"person", "trip_number","main_mode", "longest_distance_mode", "traveled_distance");

		trips = tripsFiltered.joinOn("person").inner(personsFiltered);

		System.out.println(trips);



		writeElasticityStatsPerMode(trips);
		writeElasticityStatsPerGroup("income",trips);
		writeElasticityStatsPerGroup("age",trips);


		return 0;
	}

	private Map<String, ColumnType> getPersonsColumnTypes() {
		return new HashMap<>(Map.of(
			PERSON, ColumnType.STRING,
			AGE, ColumnType.DOUBLE,
			INCOME, ColumnType.DOUBLE,
			SUBPOPULATION, ColumnType.STRING));
	}

	private static Map<String, ColumnType> getTripsColumnTypes() {
		return new HashMap<>(Map.of(
			PERSON, ColumnType.STRING,
			MAIN_MODE, ColumnType.STRING,
			TRIP_NUMBER, ColumnType.INTEGER,
			TRAVELED_DISTANCE, ColumnType.DOUBLE));
	}

	/**
	 * get monetary distance rate by mode.
	 */
	private double getMonDistRateByMode(String mode) throws IOException {

		return config.scoring()
			.getScoringParameters(null)
			.getModes()
			.get(mode)
			.getMonetaryDistanceRate();

	}

	/**
	 * writes elasticity_stats.csv
	 */

	private void writeElasticityStatsPerMode (Table trips) throws IOException{
		Object2IntMap<String> nPersons = new Object2IntOpenHashMap<>();
		Object2IntMap<String> nTrips = new Object2IntOpenHashMap<>();
		Object2DoubleMap<String> modeShare = new Object2DoubleOpenHashMap<>();
		Object2DoubleMap<String> avgDistance = new Object2DoubleOpenHashMap<>();
		Object2DoubleMap<String> monetaryCost = new Object2DoubleOpenHashMap<>();

		StringColumn subpopulation = trips.stringColumn(SUBPOPULATION);
		StringColumn mainMode = trips.stringColumn(MAIN_MODE);

		try (CSVPrinter printer = new CSVPrinter(Files.newBufferedWriter(output.getPath("elasticity_stats.csv")),
			CSVFormat.DEFAULT)) {
			printer.print("Info");

			for(String m : modes){
				printer.print(m);
			}

			printer.println();

			printer.print("nTrips");

			// nTrips per Mode
			for (String mode : modes) {

				int n = trips.where(
					subpopulation.isEqualTo(PERSON)
						.and(mainMode.isEqualTo(mode))
				).rowCount();
				log.info("nTrips for {} = {}", mode, n);
				nTrips.put(mode, n);
				printer.print(n);
			}

			printer.println();

			printer.print("Monetary Cost");

			// Monetary Distance Rate per Mode
			for (String mode: modes){
				double mdr = config.scoring()
					.getScoringParameters(null)
					.getModes()
					.get(mode)
					.getMonetaryDistanceRate();
				printer.print(mdr);
			}

			printer.println();

			printer.print("AvgDistPerMode");

			// Average Distance per Mode
			for(String mode : modes){
				double avgDist = trips.doubleColumn(TRAVELED_DISTANCE)
					.where(trips.stringColumn(MAIN_MODE).isEqualTo(mode))
					.mean();
				avgDistance.put(mode, avgDist);
				printer.print(avgDist);
			}

			printer.println();

			printer.print("monetary cost per trip per mode");

			// Monetary Cost per Trip per Mode
			for(String mode : modes){
				double price =
					getMonDistRateByMode(mode) * avgDistance.getDouble(mode);
				monetaryCost.put(mode, price);
				printer.print(price);
			}

			printer.println();

			printer.print("modeshare");
			for(String mode: modes ){
				int tripsOfMode = nTrips.getInt(mode);
				double share = (double) tripsOfMode / trips.where(subpopulation.isEqualTo(PERSON)).rowCount();
				log.info("modeshare for {} = {}", mode, share);
				modeShare.put(mode, share);
				printer.print(share);
			}

			printer.println();

			printer.print("Elasticity");
			for (String mode: modes){

				double e = -betaMoney * (monetaryCost.getDouble(mode) * (1-modeShare.getDouble(mode)));
				log.info("elasticity for {} = {}", mode, e);
				printer.print(e);

			}

		}
	}

	private void writeElasticityStatsPerGroup(String attribute, Table trips) throws IOException{

			StringColumn subpopulation = trips.stringColumn(SUBPOPULATION);
			StringColumn mainMode = trips.stringColumn(MAIN_MODE);
			StringColumn econStatus = trips.stringColumn("economic_status");
			StringColumn employStatus = trips.stringColumn("employment");
			Table tripsGroup = trips.where(subpopulation.isEqualTo(PERSON));

			if(attribute == "income"){
				Set<String> econstat = tripsGroup.stringColumn("economic_status").asSet();
				try(CSVPrinter printer = new CSVPrinter(Files.newBufferedWriter(output.getPath("elasticity_per_income.csv")), CSVFormat.DEFAULT)){

					printer.print("mode");
					printer.print("Info");
					for(String e:econstat){
						printer.print(e);
					}

					printer.println();

					printer.print("car");
					printer.print("nTrips");

					for(String e:econstat){
						int n = trips.where(
							subpopulation.isEqualTo(PERSON)
								.and(mainMode.isEqualTo("car"))
								.and(econStatus.isEqualTo(e))
						).rowCount();
						log.info("nTrips for {} = {}", e, n);
						nTripsIncome.put( e, n);
						printer.print(n);
					}

					printer.println();

					printer.print(" ");
					printer.print("AvgDistPerIncome");

					// Average Distance per Mode
					for(String e : econstat){
						double avgDist = trips.doubleColumn(TRAVELED_DISTANCE)
							.where(
								trips.stringColumn(MAIN_MODE).isEqualTo("car")
									.and(econStatus.isEqualTo(e))
							)
							.mean();
						log.info("avgDist for {} = {}", e, avgDist);
						avgDistanceIncome.put(e, avgDist);
						printer.print(avgDist);
					}

					printer.println();
					printer.print(" ");
					printer.print("monetary cost per trip per income");

					// Monetary Cost per Trip per Mode
					for(String e : econstat){
						double price =
							getMonDistRateByMode("car") * avgDistanceIncome.getDouble(e);
						monetaryCostIncome.put(e, price);
						log.info("monetary cost per trip per income for {} = {}", e, price);
						printer.print(price);
					}

					printer.println();
					printer.print(" ");
					printer.print("modeshare per income");

					for(String e: econstat ){
						int tripsOfMode = nTripsIncome.getInt(e);
						double share = (double) tripsOfMode / trips.where(subpopulation.isEqualTo(PERSON)).rowCount();
						log.info("modeshare for {} = {}", e, share);
						modeShareIncome.put( e, share);
						printer.print(share);
					}

					printer.println();
					printer.print(" ");
					printer.print("elasticity per income");

					for (String ec: econstat){

						double e = -betaMoney * (monetaryCostIncome.getDouble(ec) * (1-modeShareIncome.getDouble(ec)));
						log.info("elasticity for {} = {}", ec, e);
						elasticityIncome.put( ec, e);
						printer.print(e);

					}

					printer.println();

					nTripsIncome.clear();

					printer.print("ride");
					printer.print("nTrips");

					for(String e:econstat){
						int n = trips.where(
							subpopulation.isEqualTo(PERSON)
								.and(mainMode.isEqualTo("ride"))
								.and(econStatus.isEqualTo(e))
						).rowCount();
						log.info("nTrips for {} = {}", e, n);
						nTripsIncome.put( e, n);
						printer.print(n);
					}

					printer.println();

					printer.print(" ");
					printer.print("AvgDistPerIncome");

					// Average Distance per Mode
					for(String e : econstat){
						double avgDist = trips.doubleColumn(TRAVELED_DISTANCE)
							.where(
								trips.stringColumn(MAIN_MODE).isEqualTo("ride")
									.and(econStatus.isEqualTo(e))
							)
							.mean();
						log.info("avgDist for {} = {}", e, avgDist);
						avgDistanceIncome.put(e, avgDist);
						printer.print(avgDist);
					}

					printer.println();
					printer.print(" ");
					printer.print("monetary cost per trip per income");

					// Monetary Cost per Trip per Mode
					for(String e : econstat){
						double price =
							getMonDistRateByMode("ride") * avgDistanceIncome.getDouble(e);
						monetaryCostIncome.put(e, price);
						log.info("monetary cost per trip per income for {} = {}", e, price);
						printer.print(price);
					}

					printer.println();
					printer.print(" ");
					printer.print("modeshare per income");

					for(String e: econstat ){
						int tripsOfMode = nTripsIncome.getInt(e);
						double share = (double) tripsOfMode / trips.where(subpopulation.isEqualTo(PERSON)).rowCount();
						log.info("modeshare for {} = {}", e, share);
						modeShareIncome.put( e, share);
						printer.print(share);
					}

					printer.println();
					printer.print(" ");
					printer.print("elasticity per income");

					for (String ec: econstat){

						double e = -betaMoney * (monetaryCostIncome.getDouble(ec) * (1-modeShareIncome.getDouble(ec)));
						log.info("elasticity for {} = {}", ec, e);
						elasticityIncome.put( ec, e);
						printer.print(e);

					}


				}
			}

				if(attribute == "age"){
					Set<String> employment = tripsGroup.stringColumn("employment").asSet();
					try(CSVPrinter printer = new CSVPrinter(Files.newBufferedWriter(output.getPath("elasticity_per_age.csv")), CSVFormat.DEFAULT)){
						printer.print("mode");
						printer.print("Info");
						for(String e:employment){
							printer.print(e);
						}

						printer.println();

						printer.print("car");
						printer.print("nTrips");

						for(String e:employment){
							int n = trips.where(
								subpopulation.isEqualTo(PERSON)
									.and(mainMode.isEqualTo("car"))
									.and(employStatus.isEqualTo(e))
							).rowCount();
							log.info("nTrips for {} = {}", e, n);
							nTripsAge.put( e, n);
							printer.print(n);
						}

						printer.println();

						printer.print(" ");
						printer.print("AvgDistPerAge");

						// Average Distance per Mode
						for(String e : employment){
							double avgDist = trips.doubleColumn(TRAVELED_DISTANCE)
								.where(
									trips.stringColumn(MAIN_MODE).isEqualTo("car")
										.and(employStatus.isEqualTo(e))
								)
								.mean();
							log.info("avgDist for {} = {}", e, avgDist);
							avgDistanceAge.put(e, avgDist);
							printer.print(avgDist);
						}

						printer.println();
						printer.print(" ");
						printer.print("monetary cost per trip per age");

						// Monetary Cost per Trip per Mode
						for(String e : employment){
							double price =
								getMonDistRateByMode("car") * avgDistanceAge.getDouble(e);
							monetaryCostAge.put(e, price);
							log.info("monetary cost per trip per income for {} = {}", e, price);
							printer.print(price);
						}

						printer.println();
						printer.print(" ");
						printer.print("modeshare per age");

						for(String e: employment ){
							int tripsOfMode = nTripsAge.getInt(e);
							double share = (double) tripsOfMode / trips.where(subpopulation.isEqualTo(PERSON)).rowCount();
							log.info("modeshare for {} = {}", e, share);
							modeShareAge.put( e, share);
							printer.print(share);
						}

						printer.println();
						printer.print(" ");
						printer.print("elasticity per age");

						for (String ec: employment){

							double e = -betaMoney * (monetaryCostAge.getDouble(ec) * (1-modeShareAge.getDouble(ec)));
							log.info("elasticity for {} = {}", ec, e);
							elasticityAge.put( ec, e);
							printer.print(e);

						}

						printer.println();

						nTripsAge.clear();

						printer.print("ride");
						printer.print("nTrips");

						for(String e:employment){
							int n = trips.where(
								subpopulation.isEqualTo(PERSON)
									.and(mainMode.isEqualTo("ride"))
									.and(employStatus.isEqualTo(e))
							).rowCount();
							log.info("nTrips for {} = {}", e, n);
							nTripsAge.put( e, n);
							printer.print(n);
						}

						printer.println();

						printer.print(" ");
						printer.print("AvgDistPerAge");

						// Average Distance per Mode
						for(String e : employment){
							double avgDist = trips.doubleColumn(TRAVELED_DISTANCE)
								.where(
									trips.stringColumn(MAIN_MODE).isEqualTo("ride")
										.and(employStatus.isEqualTo(e))
								)
								.mean();
							log.info("avgDist for {} = {}", e, avgDist);
							avgDistanceAge.put(e, avgDist);
							printer.print(avgDist);
						}

						printer.println();
						printer.print(" ");
						printer.print("monetary cost per trip per age");

						// Monetary Cost per Trip per Mode
						for(String e : employment){
							double price =
								getMonDistRateByMode("ride") * avgDistanceAge.getDouble(e);
							monetaryCostAge.put(e, price);
							log.info("monetary cost per trip per income for {} = {}", e, price);
							printer.print(price);
						}

						printer.println();
						printer.print(" ");
						printer.print("modeshare per income");

						for(String e: employment ){
							int tripsOfMode = nTripsAge.getInt(e);
							double share = (double) tripsOfMode / trips.where(subpopulation.isEqualTo(PERSON)).rowCount();
							log.info("modeshare for {} = {}", e, share);
							modeShareAge.put( e, share);
							printer.print(share);
						}

						printer.println();
						printer.print(" ");
						printer.print("elasticity per age");

						for (String ec: employment){

							double e = -betaMoney * (monetaryCostAge.getDouble(ec) * (1-modeShareAge.getDouble(ec)));
							log.info("elasticity for {} = {}", ec, e);
							elasticityAge.put( ec, e);
							printer.print(e);

						}
					}


				}

	}

}
