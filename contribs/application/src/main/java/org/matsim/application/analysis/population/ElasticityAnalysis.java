package org.matsim.application.analysis.population;

import it.unimi.dsi.fastutil.objects.*;
import jakarta.annotation.Nullable;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.matsim.application.CommandSpec;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.analysis.AnalysisUtils;
import org.matsim.application.options.CsvOptions;
import org.matsim.application.options.InputOptions;
import org.matsim.application.options.OutputOptions;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.core.utils.io.IOUtils;
import picocli.CommandLine;
import tech.tablesaw.api.*;
import tech.tablesaw.columns.Column;
import tech.tablesaw.io.csv.CsvReadOptions;

import static java.lang.IO.print;


@CommandLine.Command (
	name = "elasticity",
	description = "Generates statistics for Price Elasticity of Demand."
)

@CommandSpec(
	requires = {"trips.csv", "config.xml", "persons.csv"},
	produces = {"elasticity_stats.csv", "elasticity_per_income.csv","elasticity_per_age.csv", "elasticity_per_%s.csv", "mode_share_per_%s.csv"}
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

//	private Map<String, List<String>> groupsOfSubpopulationsForPersonAnalysis = new HashMap<>();


	@CommandLine.Mixin
	private final InputOptions input = InputOptions.ofCommand(ElasticityAnalysis.class);
	@CommandLine.Mixin
	private final OutputOptions output = OutputOptions.ofCommand(ElasticityAnalysis.class);
	// For Filtering Modes

	// This is never used?!
	@CommandLine.Option(names = "--modes-filter", split = ",", description = "Define which modes should be included into elasticity analysis.")
	private final List<String> modes = List.of("car", "ride");
	// For Filtering Attributes, such as Age and Income
	@CommandLine.Option(names = "--attribute-filter", description = "Define which groups should be included into elasticity analysis.")
	private final List<String> attribute = List.of("age", "income");
	@CommandLine.Option(names = "--dist-groups", split = ",", description = "List of distances for binning", defaultValue = "0,1000,2000,5000,10000,20000")
	private List<Long> distGroups;
	@CommandLine.Option(names = "--input-ref-data", description = "Optional path to reference data", required = false)
	private String refData;

//	// HARDCODING FOR TESTING PURPOSE
//	private static final String TRIPS_PATH = "/home/teddymustafa/Desktop/FG-VSP/elasticity/berlin-v7.1-1pct.output_trips.csv.gz";
//	private static final String STATS_OUT_PATH = "/home/teddymustafa/Desktop/FG-VSP/elasticity/analysis/elasticity/elasticity_stats.csv";
//	private final Set<String> modes = Set.of("car","ride");
//	private final String groupBy = null;

	// Table to store the calculated numbers
	// this is never used //DR20260917
//	private static Table trips;
	private Config config;
	private double betaMoney;


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

		DoubleColumn ageColumn = persons.doubleColumn("age");
		StringColumn ageGroups = StringColumn.create("ageGroup");

		for(double age : ageColumn) {
			ageGroups.append(getAgeGroup(age));
		}

		persons = persons.addColumns(ageGroups);

		DoubleColumn incomeColumn = persons.doubleColumn("income");
		StringColumn incomeGroups = StringColumn.create("incomeGroup");

		for(double income : incomeColumn) {
			incomeGroups.append(getIncomeGroup(income));
		}

		persons = persons.addColumns(incomeGroups);

		print(persons);

		Table personsFiltered = persons.selectColumns(
			"person", "ageGroup", "incomeGroup", "subpopulation");

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
//		List<String> distanceLabels = AnalysisUtils.createGroupLabels(distGroups);

//		StringColumn dist_group = trips.longColumn("traveled_distance")
//			.map(dist -> AnalysisUtils.getLabelForValue(dist, distGroups, distanceLabels), ColumnType.STRING::create).setName("dist_group");
//
//		trips.addColumns(dist_group);

//		TripBySociodemographicGroupsAnalysis sociodemographicGroups = null;
//		if (refData != null) {
//			sociodemographicGroups = new TripBySociodemographicGroupsAnalysis(refData);
//			sociodemographicGroups.groupPersons(persons);
//		}
//
//		if (sociodemographicGroups != null) {
//			// filters for all subpopulations that are used for person analysis
//			if (!groupsOfSubpopulationsForPersonAnalysis.isEmpty()) {
//				Table filteredForPersons = trips.where(
//					trips.stringColumn("subpopulation").isIn(groupsOfSubpopulationsForPersonAnalysis.values().stream()
//						.flatMap(Collection::stream)
//						.collect(Collectors.toSet())));
//				sociodemographicGroups.writeModeShare(filteredForPersons, distanceLabels, modes, (g) -> output.getPath("mode_share_per_%s.csv", g));
//			}
//			else
//				sociodemographicGroups.writeModeShare(trips, distanceLabels, modes, (g) -> output.getPath("mode_share_per_%s.csv", g));
//		}
//		withGroupedRefData("C:/Users/teddy/Downloads/berlin-v7.1-1pct.output_trips.csv/berlin-v7.1-1pct.output_persons.csv", "age", "income");


		writeElasticityStatsPerMode(trips);

		for(String a: attribute){
			writeElasticityStatsPerGroup(a,trips);
		}

		return 0;
	}

//	@Nullable
//	private String[] categories;
//	@Nullable
//	private String groupedRefCsv;
//
//	public String withGroupedRefData(String groupedRefCsv, String... categories) {
//		this.groupedRefCsv = groupedRefCsv;
//		if (categories.length == 0) {
//			categories = detectCategories(groupedRefCsv);
//			log.info("Detected categories from reference data: {}", Arrays.toString(categories));
//		}
//		this.categories = categories;
//		return Arrays.toString(categories);
//	}
//
//	private static String[] detectCategories(String groupedRefCsv) {
//		try {
//			Character c = CsvOptions.detectDelimiter(groupedRefCsv);
//			try (BufferedReader reader = IOUtils.getBufferedReader(groupedRefCsv)) {
//				String header = reader.readLine();
//				return Arrays.stream(header.split(String.valueOf(c)))
//					.filter(s -> !s.equals("main_mode") && !s.equals("share") && !s.equals("dist_group"))
//					.toArray(String[]::new);
//			}
//
//		} catch (IOException e) {
//			throw new UncheckedIOException(e);
//		}
//	}

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

	public String getAgeGroup(Double age) {
		if (age < 0) return "Invalid";
		if (age <= 12) return "0-12";
		if (age <= 18) return "13-18";
		if (age <= 25) return "19-25";
		if (age <= 35) return "26-35";
		if (age <= 65) return "36-65";
		return "66+";
	}

	public String getIncomeGroup(Double income) {
		if (income < 0) return "Invalid";
		if (income <= 250) return "0-250";
		if (income <= 500) return "251-500";
		if (income <= 750) return "501-750";
		if (income <= 1000) return "751-1000";
		if (income <= 1250) return "1001-1250";
		if (income <= 1500) return "1251-1500";
		if (income <= 1750) return "1501-1750";
		if (income <= 2000) return "1751-2000";
		if (income <= 2500) return "2001-2500";
		if (income <= 3000) return "2501-3000";
		if (income <= 3500) return "3001-3500";
		return "3500+";
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
//		Object2IntMap<String> nPersons = new Object2IntOpenHashMap<>();
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
	// Statt table, Map<status, List<Trips>>
	private void writeElasticityStatsPerGroup(String attribute, Table trips) throws IOException{



			StringColumn subpopulation = trips.stringColumn(SUBPOPULATION);
			StringColumn mainMode = trips.stringColumn(MAIN_MODE);
			StringColumn incomestat = trips.stringColumn("incomeGroup");
			StringColumn agestat = trips.stringColumn("ageGroup");
			Table tripsGroup = trips.where(subpopulation.isEqualTo(PERSON));

			// do not use == but .equals() to compare strings
			if(attribute.equals("income")){
				final Object2IntMap<String> nTripsIncome = new Object2IntOpenHashMap<>();
				final Object2DoubleMap<String> modeShareIncome = new Object2DoubleOpenHashMap<>();
				final Object2DoubleMap<String> avgDistanceIncome = new Object2DoubleOpenHashMap<>();
				final Object2DoubleMap<String> monetaryCostIncome = new Object2DoubleOpenHashMap<>();
				List<String> income = tripsGroup.stringColumn("incomeGroup")
					.asSet()
					.stream()
					.sorted(Comparator.comparingInt(label -> {
						java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d+").matcher(label);
						return m.find() ? Integer.parseInt(m.group()) : Integer.MAX_VALUE;
					}))
					.toList();
				try(CSVPrinter printer = new CSVPrinter(Files.newBufferedWriter(output.getPath("elasticity_per_income.csv")), CSVFormat.DEFAULT)){

					printer.print("mode");
					printer.print("Info");
					for(String e:income){
						printer.print(e);
					}

					printer.println();

					for(String mode:modes){
						printer.print(mode);
						printer.print("nTrips");

						for(String e:income){
							int n = trips.where(
								subpopulation.isEqualTo(PERSON)
									.and(mainMode.isEqualTo(mode))
									.and(incomestat.isEqualTo(e))
							).rowCount();
							log.info("nTrips for {} = {}", e, n);
							nTripsIncome.put( e, n);
							printer.print(n);
						}

						printer.println();

						printer.print(" ");
						printer.print("AvgDistPerIncome");

						// Average Distance per Mode
						// Trips nach Statusgruppe
						for(String e : income){
							double avgDist = trips.doubleColumn(TRAVELED_DISTANCE)
								.where(
									trips.stringColumn(MAIN_MODE).isEqualTo(mode)
										.and(incomestat.isEqualTo(e))
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
						for(String e : income){
							double price =
								getMonDistRateByMode(mode) * avgDistanceIncome.getDouble(e);
							monetaryCostIncome.put(e, price);
							log.info("monetary cost per trip per income for {} = {}", e, price);
							printer.print(price);
						}

						printer.println();
						printer.print(" ");
						printer.print("modeshare per income");

						for(String e: income ){
							int tripsOfMode = nTripsIncome.getInt(e);
							double share = (double) tripsOfMode / trips.where(subpopulation.isEqualTo(PERSON)).rowCount();
							log.info("modeshare for {} = {}", e, share);
							modeShareIncome.put( e, share);
							printer.print(share);
						}

						printer.println();
						printer.print(" ");
						printer.print("elasticity per income");

						for (String ec: income){

							double e = -betaMoney * (monetaryCostIncome.getDouble(ec) * (1-modeShareIncome.getDouble(ec)));
							log.info("elasticity for {} = {}", ec, e);
							printer.print(e);

						}

						printer.println();

						nTripsIncome.clear();
					}
				}
			}

			// a few things:
			// - check for attribute age here, but use employment for grouping ?! This is misleading
			// - this is actually almost the same code as for income. Can we not reuse the income-part and make it more generic X
			// - also the code for  car- and ride- has a lot of duplication X
			// - income- and ageblock are very long and thus hard to read, maybe we could extract some code to methods X
			// - Wiederverwendbar
				if(attribute.equals("age")){

					final Object2IntMap<String> nTripsAge = new Object2IntOpenHashMap<>();
					final Object2DoubleMap<String> modeShareAge = new Object2DoubleOpenHashMap<>();
					final Object2DoubleMap<String> avgDistanceAge = new Object2DoubleOpenHashMap<>();
					final Object2DoubleMap<String> monetaryCostAge = new Object2DoubleOpenHashMap<>();
					List<String> age = tripsGroup.stringColumn("ageGroup").asSet().stream()
						.sorted(Comparator.comparingInt(label -> {
							java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d+").matcher(label);
							return m.find() ? Integer.parseInt(m.group()) : Integer.MAX_VALUE;
						}))
						.toList();;
					try(CSVPrinter printer = new CSVPrinter(Files.newBufferedWriter(output.getPath("elasticity_per_age.csv")), CSVFormat.DEFAULT)){
						printer.print("mode");
						printer.print("Info");
						for(String e:age){
							printer.print(e);
						}

						printer.println();
						for(String mode:modes){
							printer.print(mode);
							printer.print("nTrips");

							for(String e:age){
								int n = trips.where(
									subpopulation.isEqualTo(PERSON)
										.and(mainMode.isEqualTo(mode))
										.and(agestat.isEqualTo(e))
								).rowCount();
								log.info("nTrips for {} = {}", e, n);
								nTripsAge.put( e, n);
								printer.print(n);
							}

							printer.println();

							printer.print(" ");
							printer.print("AvgDistPerAge");

							// Average Distance per Mode
							for(String e : age){
								double avgDist = trips.doubleColumn(TRAVELED_DISTANCE)
									.where(
										trips.stringColumn(MAIN_MODE).isEqualTo(mode)
											.and(agestat.isEqualTo(e))
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
							for(String e : age){
								double price =
									getMonDistRateByMode(mode) * avgDistanceAge.getDouble(e);
								monetaryCostAge.put(e, price);
								log.info("monetary cost per trip per income for {} = {}", e, price);
								printer.print(price);
							}

							printer.println();
							printer.print(" ");
							printer.print("modeshare per age");

							for(String e: age ){
								int tripsOfMode = nTripsAge.getInt(e);
								double share = (double) tripsOfMode / trips.where(subpopulation.isEqualTo(PERSON)).rowCount();
								log.info("modeshare for {} = {}", e, share);
								modeShareAge.put( e, share);
								printer.print(share);
							}

							printer.println();
							printer.print(" ");
							printer.print("elasticity per age");

							for (String ec: age){

								double e = -betaMoney * (monetaryCostAge.getDouble(ec) * (1-modeShareAge.getDouble(ec)));
								log.info("elasticity for {} = {}", ec, e);
								printer.print(e);

							}

							printer.println();

							nTripsAge.clear();
						}
					}


				}

	}

}
