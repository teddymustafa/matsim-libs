package org.matsim.application.analysis.population;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.NonNull;
import org.matsim.application.CommandSpec;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.options.CsvOptions;
import org.matsim.application.options.InputOptions;
import org.matsim.application.options.OutputOptions;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.ScoringConfigGroup;
import org.matsim.core.utils.io.IOUtils;
import picocli.CommandLine;
import tech.tablesaw.api.ColumnType;
import tech.tablesaw.api.StringColumn;
import tech.tablesaw.api.Table;
import tech.tablesaw.io.csv.CsvReadOptions;

import java.io.IOException;
import java.nio.file.Files;
import java.util.*;


@CommandLine.Command (
	name = "elasticity",
	description = "Generates statistics for Price Elasticity of Demand."
)

@CommandSpec(
	requires = {"trips.csv", "config.xml", "persons.csv"},
	produces = {"elasticity_stats.csv","elasticity_stats_%s.csv"}
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
	private static final String UNKNOWN = "unknown";

	@CommandLine.Mixin
	private final InputOptions input = InputOptions.ofCommand(ElasticityAnalysis.class);
	@CommandLine.Mixin
	private final OutputOptions output = OutputOptions.ofCommand(ElasticityAnalysis.class);

	@CommandLine.Option(names = "--modes", split = ",", description = "Define which modes should be included into elasticity analysis.")
	private List<String> modes = List.of("car", "ride");
	@CommandLine.Option(names = "--subpopulation", defaultValue = "person",
		description = "Subpopulation to analyse; its scoring parameters are used")
	private String subpopulation;
	@CommandLine.Option(names = "--dist-groups", split = ",", description = "List of distances for binning", defaultValue = "0,1000,2000,5000,10000,20000")
	private List<Integer> distGroups;
	// description and default does not match
	@CommandLine.Option(names = "--age-groups", split = ",", defaultValue = "0,12,18,25,35,66",
		description = "Age bin edges -> 0-18, 18-30, 30-65, 65+")
	private List<Integer> ageGroups;
	// description and default does not match
	@CommandLine.Option(names = "--income-groups", split = ",", defaultValue = "0,250,500,750,1000,1250,1500,1750,2000,2500,3000,3500",
		description = "Income bin edges -> 0-1000, ..., 3500+")
	private List<Integer> incomeGroups;
	@CommandLine.Option(names = "--economic-status", split = ",",
		defaultValue = "very_low,low,medium,high,very_high",
		description = "Order of the economic_status groups")
	private List<String> economicStatus;
	@CommandLine.Option(names = "--employment-status", split = ",",
		defaultValue = "child,homemaker,retiree,unemployed,school,student,job_full_time,job_part_time,other",
		description = "Order of the employment groups")
	private List<String> employStatus;



	private Config config;
	private double betaMoney;


	public static void main(String[] args) throws Exception {
		new ElasticityAnalysis().execute(args);
	}

	@Override
	public Integer call() throws Exception {

		config = ConfigUtils.loadConfig(input.getPath("config.xml"));
		ScoringConfigGroup.ScoringParameterSet scoringParameters = config.scoring().getScoringParameters(subpopulation);
		// removed this again, getScoringParameters has a fallback by default
//		if (scoringParameters == null ) {
//			// --subpopulation is promising that subpopulation scoring-params are used, but they never we're used.
//			// currently this is ok, since our config has no sub-pop-specific params, but this wil chnange in the future
//			log.warn("found no scoring-params for subpop {}", subpopulation);
//			scoringParameters = config.scoring().getScoringParameters(null);
//		}
		betaMoney = scoringParameters.getMarginalUtilityOfMoney();

		Table persons = Table.read().csv(
			CsvReadOptions.builder(IOUtils.getBufferedReader(input.getPath("persons.csv")))
				.columnTypesPartial(getPersonsColumnTypes())
				.sample(false)
				.separator(CsvOptions.detectDelimiter(input.getPath("persons.csv")))
				.build());

		// there is a field SUBPOPULATION, use it! Same for person/age/...
		persons = persons.where(persons.stringColumn("subpopulation").isEqualTo(subpopulation));


		List<String> chosen = new ArrayList<>(List.of("person", SUBPOPULATION));

		if (isSet(ageGroups)) {
			addRangeGroup(persons, "age", ageGroups, "age_group");
			chosen.add("age_group");
		}
		if (isSet(incomeGroups)) {
			addRangeGroup(persons, "income", incomeGroups, "income_group");
			chosen.add("income_group");
		}

		// This should be configurable like ageGroups and incomeGroups, might be that there is a scenario without
		// do not change for the moment. For Berlin it is ok.
		chosen.add("economic_status");
		chosen.add("employment");
		persons = persons.selectColumns(chosen.toArray(String[]::new)); //select the column needed for group analysis

		Table trips = Table.read().csv(
			CsvReadOptions.builder(IOUtils.getBufferedReader(input.getPath("trips.csv")))
				.columnTypesPartial(getTripsColumnTypes())
				.sample(false)
				.separator(CsvOptions.detectDelimiter(input.getPath("trips.csv")))
				.build());

		// can it really happen, that there is no main-mode?
		trips.stringColumn(MAIN_MODE).set(trips.stringColumn(MAIN_MODE).isMissing(),
			trips.stringColumn("longest_distance_mode"));

		trips = trips.selectColumns(
			PERSON,MAIN_MODE, TRAVELED_DISTANCE);

		// FINAL Table here.
		trips = trips.joinOn(PERSON).inner(persons);

		// distance class per trip, combined with age/income in the grouped case
		addRangeGroup(trips, TRAVELED_DISTANCE, distGroups, "dist_group");

		// Derived from TripAnalysis
		if (modes == null || modes.isEmpty())
			modes = new ArrayList<>(new TreeSet<>(trips.stringColumn("main_mode").unique().asList()));

		// age: labels from the numeric edges
		List<String> ageLabels = new ArrayList<>(createRangeLabels(ageGroups));
		ageLabels.add(UNKNOWN);
		// income
		List<String> incomeLabels = new ArrayList<>(createRangeLabels(incomeGroups));
		incomeLabels.add(UNKNOWN);

		writeElasticityStatsPerMode(trips);
		writeElasticityStatsPerModeGrouped(trips, "age_group", ageLabels);
		writeElasticityStatsPerModeGrouped(trips, "income_group", incomeLabels);
		writeElasticityStatsPerModeGrouped(trips, "economic_status", economicStatus);
		writeElasticityStatsPerModeGrouped(trips, "employment", employStatus);

		return 0;
	}


	// Helpers

	private static boolean isSet(List<?> list) {
		return list != null && !list.isEmpty();
	}

	/**
	 * Adds a group column based on a numeric column (age, income, traveled_distance).
	 * Every row gets a group; rows without a value end up in "unknown".
	 *
	 * Table, attribute, brackets, groupColumn
	 */
	private static void addRangeGroup(@NonNull Table table, String attribute, List<Integer> brackets, String groupColumn) {
		if (!table.containsColumn(attribute))
			throw new IllegalStateException("Column '" + attribute + "' not found");

		List<String> labels = createRangeLabels(brackets); // Labels range
		StringColumn group = StringColumn.create(groupColumn);
		for (double v : table.numberColumn(attribute).asDoubleColumn())   // works for int, long and double columns, problem when filtering string
			group.append(rangeLabel(v, brackets, labels)); // Assign person to a certain attribute group
		table.addColumns(group); //add labelling to the group column
	}

	/**
	 * Labels like
	 * "0-18", "18-30", ..., "65+".
	 **/

	private static List<String> createRangeLabels(List<Integer> brackets) {
		List<Integer> sorted = new ArrayList<>(brackets);
		Collections.sort(sorted);
		List<String> labels = new ArrayList<>();
		for (int i = 0; i < sorted.size() - 1; i++)
			labels.add(sorted.get(i) + "-" + sorted.get(i + 1));
		labels.add(sorted.getLast() + "+");
		return labels;
	}

	/**
	* assign person into the predefined label from createRangeLabels()
	*
	**/

	private static String rangeLabel(double value, List<Integer> brackets, List<String> labels) {
		if (Double.isNaN(value)) return UNKNOWN;
		List<Integer> sorted = new ArrayList<>(brackets);
		Collections.sort(sorted);
		for (int i = sorted.size() - 1; i >= 0; i--)
			if (value >= sorted.get(i)) return labels.get(i);
		return UNKNOWN;
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
	 * get monetary distance rate by mode from config.xml
	 */
	private double getMonDistRateByMode(String mode) throws IOException {
		// this also ignores subpopulation
		return config.scoring()
			.getScoringParameters(null)
			.getModes()
			.get(mode)
			.getMonetaryDistanceRate();

	}

	/**
	 * writes elasticity_stats.csv
	 */

	private void writeElasticityStatsPerMode(Table trips) throws IOException {

		int total = trips.rowCount();

		log.info("Default case: {} persons analysed ({} trips)",
			trips.stringColumn("person").countUnique(), trips.rowCount());

		try (CSVPrinter printer = new CSVPrinter(
			Files.newBufferedWriter(output.getPath("elasticity_stats.csv")), CSVFormat.DEFAULT)) {

			// Headers
			printer.printRecord("main_mode", "n_trips", "mode_share", "avg_distance",
				"monetary_distance_rate", "monetary_cost_per_trip", "elasticity_cost");

			for (String mode : modes) {

				Table m = trips.where(trips.stringColumn(MAIN_MODE).isEqualTo(mode));
				int n = m.rowCount();
				if (n == 0) continue;

				double share = (double) n / total;
				double avgDist = m.numberColumn(TRAVELED_DISTANCE).mean();

				double mdr = getMonDistRateByMode(mode);          // negative in MATSim
				double cost = mdr * avgDist;                      // negative = money spent
				double e = betaMoney * cost * (1 - share);        // negative elasticity

				printer.printRecord(mode, n, share, avgDist, mdr, cost, e);
			}
		}
	}

	private void writeElasticityStatsPerModeGrouped(Table trips, String groupColumn, List<String> groups) throws IOException {

		// there is a cli-flag --subpopulation, but PERSON is hardcoded here. Why?
		Table personTrips = trips.where(trips.stringColumn(SUBPOPULATION).isEqualTo(PERSON));

		groups.add(UNKNOWN);

		List<String> dists = new ArrayList<>();
		dists.add("all");                              // whole group, across all distances
		dists.addAll(createRangeLabels(distGroups));
		log.info("dists = {}", dists);

		try (CSVPrinter printer = new CSVPrinter(
			Files.newBufferedWriter(output.getPath("elasticity_stats_%s.csv", groupColumn)), CSVFormat.DEFAULT)) {

			printer.printRecord(groupColumn, "dist_group", "main_mode", "n_trips", "mode_share", "avg_distance",
				"monetary_distance_rate", "monetary_cost_per_trip", "elasticity_cost");

			for (String g : groups) {

				Table groupTrips = personTrips.where(personTrips.stringColumn(groupColumn).isEqualTo(g));
				if (groupTrips.isEmpty()) continue;

				log.info("{} = {}: {} persons analysed ({} trips)",
					groupColumn, g, groupTrips.stringColumn("person").countUnique(), groupTrips.rowCount());

				for (String d : dists) {

					Table cell;
					if (d.equals("all")) {
						cell = groupTrips;
					} else {
						cell = groupTrips.where(groupTrips.stringColumn("dist_group").isEqualTo(d));
					}

					int total = cell.rowCount();

					for (String mode : modes) {

						Table m = cell.where(cell.stringColumn(MAIN_MODE).isEqualTo(mode));
						int n = m.rowCount();
						if (n == 0) continue;

						double share = (double) n / total;
						double avgDist = m.numberColumn(TRAVELED_DISTANCE).mean();

						double mdr = getMonDistRateByMode(mode);
						double cost = mdr * avgDist;
						double e = betaMoney * cost * (1 - share);

						log.info("[{} | {}] {}: nTrips={}, share={}, avgDist={}, cost={}, elasticity={}",
							g, d, mode, n, share, avgDist, cost, e);

						printer.printRecord(g, d, mode, n, share, avgDist, mdr, cost, e);
					}
				}
			}
		}
		log.info("WROTE {}", output.getPath("elasticity_stats_%s.csv", groupColumn).toAbsolutePath());
	}
}
