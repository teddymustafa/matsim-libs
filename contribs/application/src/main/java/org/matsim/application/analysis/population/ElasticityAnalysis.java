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
import java.math.BigDecimal;
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
	description = "Generates statistics for elasticity."
)
@CommandSpec(
	requires = {"trips.csv", "config.xml", "persons.csv"},
	produces = {"elasticity_stats.csv", "elasticity_stats_income.csv"}
)
public class ElasticityAnalysis implements MATSimAppCommand {

	// Creating Log
	private static final Logger log = LogManager.getLogger(ElasticityAnalysis.class);
	private static final String MAIN_MODE = "main_mode";
	private static final String PERSON = "person";
	private static final String TRIP_NUMBER = "trip_number";
	private static final String TRAVELED_DISTANCE = "traveled_distance";
	private static final String AGE = "age";
	private static final String INCOME = "income";
	private static final String SUBPOPULATION = "subpopulation";
	private static final Set<String> INCOME_GROUP = Set.of("low", "middle", "high");

	@CommandLine.Mixin
	private final InputOptions input = InputOptions.ofCommand(ElasticityAnalysis.class);
	@CommandLine.Mixin
	private final OutputOptions output = OutputOptions.ofCommand(ElasticityAnalysis.class);
	@CommandLine.Option(names = "--modes-filter", split = ",", description = "Define which modes should be included into elasticity analysis.")
	private Set<String> modes;
	@CommandLine.Option(names = "--attribute-filter", description = "Define which groups should be included into elasticity analysis.")
	private String attribute;

//	// HARDCODING FOR TESTING PURPOSE
//	private static final String TRIPS_PATH = "/home/teddymustafa/Desktop/FG-VSP/elasticity/berlin-v7.1-1pct.output_trips.csv.gz";
//	private static final String STATS_OUT_PATH = "/home/teddymustafa/Desktop/FG-VSP/elasticity/analysis/elasticity/elasticity_stats.csv";
//	private final Set<String> modes = Set.of("car","ride");
//	private final String groupBy = null;

	private static Table tableCurated;
	private Config config;
	private double betaMoney;

	// RESULTS, NEEDED FOR OUTPUT
	private final Object2IntMap<String> nPersons = new Object2IntOpenHashMap<>();
	private final Object2IntMap<String> nTrips = new Object2IntOpenHashMap<>();
	private final Object2IntMap<String> nTripsIncome = new Object2IntOpenHashMap<>();
	private final Object2IntMap<String> nTripsAge = new Object2IntOpenHashMap<>();
	private final Object2DoubleMap<String> modeShare = new Object2DoubleOpenHashMap<>();
	private final Object2DoubleMap<String> incomeShare = new Object2DoubleOpenHashMap<>();
	private final Object2DoubleMap<String> ageShare = new Object2DoubleOpenHashMap<>();
	private final Object2DoubleMap<String> avgDistance = new Object2DoubleOpenHashMap<>();
	private final Object2DoubleMap<String> avgDistanceIncome = new Object2DoubleOpenHashMap<>();
	private final Object2DoubleMap<String> avgDistanceAge = new Object2DoubleOpenHashMap<>();
	private final Object2DoubleMap<String> monetaryCost = new Object2DoubleOpenHashMap<>();
	private final Object2DoubleMap<String> elasticity = new Object2DoubleOpenHashMap<>();
	private final Object2DoubleMap<String> elasticityIncome = new Object2DoubleOpenHashMap<>();

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
			"person", "age", "income", "subpopulation");

		Table trips = Table.read().csv(
			CsvReadOptions.builder(IOUtils.getBufferedReader(input.getPath("trips.csv")))
				.columnTypesPartial(getTripsColumnTypes())
				.sample(false)
				.separator(CsvOptions.detectDelimiter(input.getPath("trips.csv")))
				.build());

		Table tripsFiltered = trips.selectColumns(
			"person", "trip_number","main_mode", "longest_distance_mode", "traveled_distance");

		tableCurated = tripsFiltered.joinOn("person").inner(personsFiltered);

		DoubleColumn income = tableCurated.doubleColumn("income");

		double median = income.median();
		log.info(
			"median income: " + median
		);
		double lowCut  = 0.6 * median;
		log.info(
			"lowcut: " + lowCut
		);
		double highCut = 1.5 * median;
		log.info(
			"highcut: " + highCut
		);

		StringColumn bracket = StringColumn.create("income_bracket", income.size());

		for (int i = 0; i < income.size(); i++) {
			if (income.isMissing(i)) {
				bracket.setMissing(i);
			} else {
				double value = income.getDouble(i);
				if (value < lowCut) {
					bracket.set(i, "low");
				}
				//if (!(value < lowCut) && value < highCut) {   // ← now the ! is literal
				//    bracket.set(i, "middle");
				//}
				else if (value < highCut) {
					bracket.set(i, "middle");
				} else {
					bracket.set(i, "high");
				}
			}
		}

		tableCurated.addColumns(bracket);

		/**
		 * THE SWITCH FOR NOW "income" or "age"
		 * */
		attribute = "income";
		// some if statement here depending on the presence of argument
		// if groups filter given
//		if(attribute.equals(INCOME)){
//
//
//			calcNTripsByAttribute(INCOME_GROUP);
//			calcAttributeShare(INCOME_GROUP);
//			calcAvgDistByAttribute(INCOME_GROUP);
//			calcMonCostPerTripByMode(modes);
//			calcElasticityByAttribute(INCOME_GROUP, modes);
//		}
//		if(attribute.equals(AGE)) {
//			calcNTripsByAttribute(agegroups);
//			calcAttributeShare(agegroups);
//			calcAvgDistByAttribute(agegroups);
//			calcMonCostPerTripByMode(modes);
//			calcElasticityByAttribute(agegroups, modes);
//		}
		// if modes filter given

//		calcNTripsByMode(modes);
//		log.info("nTrips successfully calculated");
//		calcNPersonsByMode(modes);
//		log.info("nPersons successfully calculated");
//		calcModeShareByMode(modes);
//		log.info("modeShare successfully calculated");
//		calcAvgDistPerMode(modes);
//		log.info("Avg Distance successfully calculated");
//		calcMonCostPerTripByMode(modes);
//		log.info("Monetary Cost per Trip successfully calculated");
//		calcElasticityByMode(modes);
//		log.info("Elasticity succesfully calculated");
//		writeElasticityStats();

		calcAvgDistPerMode(modes);
		calcMonCostPerTripByMode(modes);
		calcNTripsByAttribute(INCOME_GROUP, modes);
		calcAttributeShare(INCOME_GROUP);
		calcAvgDistByAttribute(INCOME_GROUP);
		calcElasticityByAttribute(INCOME_GROUP, modes);
		writeElasticityStatsByAttribute();

		tableCurated.write().csv(
			CsvWriteOptions.builder("/home/teddymustafa/Desktop/FG-VSP/elasticity/groupby_attributes.csv")
				.separator(';')
				.header(true)
				.build());

		System.out.printf("Wrote %d rows -> %s%n", tableCurated.rowCount(), "/home/teddymustafa/Desktop/FG-VSP/elasticity/groupby_attributes.csv");



		return 0;
	}

	private void calcNTripsByAttribute(Set<String> groups, Set<String> modes) {
		int nTripsTotal = tableCurated.rowCount();
		log.info("nTripsTotal is "+ nTripsTotal);
		StringColumn subpopulation = tableCurated.stringColumn(SUBPOPULATION);
//		StringColumn incomegroup = tableCurated.stringColumn("income_bracket");
		for (String group: groups){
			if(attribute.equals("income")){
				for(String mode:modes){
					int n = tableCurated.where(
					subpopulation.isEqualTo(PERSON)
						.and(tableCurated.stringColumn("income_bracket").isEqualTo(group)
							.and(tableCurated.stringColumn("main_mode").isEqualTo(mode))
						)
					)
					.rowCount();
					nTripsIncome.put(group, n);
					log.info("nTrips for "+ group +" = "+ n);
				}
			}
			if(attribute.equals("age")){int n = tableCurated.where(
					subpopulation.isEqualTo(PERSON)
						.and(tableCurated.stringColumn("age").isEqualTo(group)))
				.rowCount();
				nTripsAge.put(group, n);
				log.info("nTrips for "+ group +" = "+ n);
			}
		}
		log.info("nTripsIncome: {}", nTripsIncome);
	}

	private void calcAttributeShare(Set<String> groups) {
		for(String group: groups){
			if(attribute.equals("income")){
				int tripsOfgroup = nTripsIncome.getInt(group);
				double share = (double) tripsOfgroup / tableCurated.rowCount();
				incomeShare.put(group, share);
				log.info("groupShare for "+ group +" = "+ share);
			}
			if(attribute.equals("age")){int tripsOfgroup = nTripsAge.getInt(group);
				double share = (double) tripsOfgroup / tableCurated.rowCount();
				ageShare.put(group, share);
				log.info("groupShare for "+ group +" = "+ share);
			}
		}
	}

	private void calcAvgDistByAttribute(Set<String> groups) {
		for(String group : groups){
			if(attribute.equals("income")){
				double avgDist = tableCurated.doubleColumn(TRAVELED_DISTANCE)
				.where(tableCurated.stringColumn("income_bracket").isEqualTo(group))
				.mean();
				avgDistanceIncome.put(group, avgDist);
				log.info("avgDistance for "+ group +" = "+ avgDist);
			}
			if(attribute.equals("age")){
				double avgDist = tableCurated.doubleColumn(TRAVELED_DISTANCE)
					.where(tableCurated.stringColumn("age").isEqualTo(group))
					.mean();
				avgDistanceAge.put(group, avgDist);
				log.info("avgDistance for "+ group +" = "+ avgDist);
			}
		}
	}

	private void calcElasticityByAttribute(Set<String> groups, Set<String> modes) {
		for (String group: groups){
			if(attribute.equals("income")){
				for(String mode:modes){
					double e = -betaMoney * (monetaryCost.getDouble(mode) * (1-incomeShare.getDouble(group)));
					log.info("elasticity for {} in {} = {}", group, mode, e);
					elasticityIncome.put(group, e);
				}
			}
			if(attribute.equals("age")){
				for(String mode:modes){
					double e = -betaMoney * (monetaryCost.getDouble(mode) * (1-ageShare.getDouble(group)));
					log.info("elasticity for {} in {} = {}", group, mode, e);
					elasticityIncome.put(group, e);
				}
			}

		}
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

	private void calcNTripsByMode(Set<String> modes){
		int nTripsTotal = tableCurated.rowCount();
		log.info("nTripsTotal is "+ nTripsTotal);
		StringColumn subpopulation = tableCurated.stringColumn(SUBPOPULATION);
		StringColumn mainMode = tableCurated.stringColumn(MAIN_MODE);
		for (String mode: modes){

			int n = tableCurated.where(
				subpopulation.isEqualTo(PERSON)
					.and(mainMode.isEqualTo(mode))
			).rowCount();
			nTrips.put(mode, n);
			log.info("nTrips for "+ mode +" = "+ n);
		}
	}

	private void calcNPersonsByMode(Set<String> modes){
		int nPersonTotal = tableCurated.stringColumn(PERSON).countUnique();
		log.info("nPersonTotal is "+ nPersonTotal);
		StringColumn subpopulation = tableCurated.stringColumn(SUBPOPULATION);
		StringColumn mainMode = tableCurated.stringColumn(MAIN_MODE);
		for (String mode: modes){

			int n = tableCurated.where(
				subpopulation.isEqualTo(PERSON)
					.and(mainMode.isEqualTo(mode))
			).stringColumn(PERSON).countUnique();
			nPersons.put(mode, n);
			log.info("nPersons for "+ mode +" = "+ n);
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
	private void calcModeShareByMode(Set<String> modes) throws IOException{

		for(String mode: modes ){
			int tripsOfMode = nTrips.getInt(mode);
			double share = (double) tripsOfMode / tableCurated.rowCount();
			modeShare.put(mode, share);
			log.info("modeShare for "+ mode +" = "+ share);
		}
	}

	/**
	 * calculate Average Distance by mode
	 * */
	private void calcAvgDistPerMode(Set<String> modes) throws IOException{

		for(String mode : modes){
			double avgDist = tableCurated.doubleColumn(TRAVELED_DISTANCE)
				.where(tableCurated.stringColumn(MAIN_MODE).isEqualTo(mode))
				.mean();
			avgDistance.put(mode, avgDist);
			log.info("avgDistance for "+ mode +" = "+ avgDist);
		}
	}

	/**
	 * calculate Monetary cost per trip by mode
	 * */
	private void calcMonCostPerTripByMode(Set<String> modes) throws IOException{

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

			writer.write("#Total persons: " + tableCurated.stringColumn(PERSON).countUnique());
			writer.newLine();
			writer.write("#Total trips: " + tableCurated.rowCount());
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

	private void writeElasticityStatsByAttribute() throws IOException{
		try (BufferedWriter writer = IOUtils.getBufferedWriter("/home/teddymustafa/Desktop/FG-VSP/elasticity/analysis/elasticity/elasticity_stats_income.csv")) {
			if(attribute.equals("income")){
				writer.write("#Total persons: " + tableCurated.stringColumn(PERSON).countUnique());
				writer.newLine();
				writer.write("#Total trips: " + tableCurated.rowCount());
				writer.newLine();
				writer.write("income;mode;nTrips;modeshare;avg_distance;elasticity");
				writer.newLine();
				for (String group : INCOME_GROUP) {
					for (String mode : modes) {
						writer.write(group + ";" + mode + ";" + nTripsIncome.getInt(group) + ";" + incomeShare.getDouble(group) + ";" + avgDistanceIncome.getDouble(group) + ";" + elasticityIncome.getDouble(group));
						writer.newLine();
					}
				}
			}
		}
		log.info("write complete!");
	}

}
