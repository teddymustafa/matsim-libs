package org.matsim.application.analysis.population;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.NonNull;
import org.matsim.api.core.v01.Scenario;
import org.matsim.application.CommandSpec;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.options.CsvOptions;
import org.matsim.application.options.InputOptions;
import org.matsim.application.options.OutputOptions;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.io.IOUtils;
import picocli.CommandLine;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import org.w3c.dom.Element;
import tech.tablesaw.api.ColumnType;
import tech.tablesaw.api.DoubleColumn;
import tech.tablesaw.api.StringColumn;
import tech.tablesaw.api.Table;
import tech.tablesaw.io.csv.CsvReadOptions;
import tech.tablesaw.joining.DataFrameJoiner;

import java.io.File;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@CommandLine.Command(name = "scoring", description = "Calculates various scoring related metrics.")
@CommandSpec(
	requires = {"persons.csv", "experienced_plans.xml"},
	produces = {
		"person_avgscore.csv","person_avgscore_%s.csv"
	}
)

public class ScoringAnalysis implements MATSimAppCommand {

	private static final Logger log = LogManager.getLogger(ScoringAnalysis.class);

	// Static Final Variables
	private static final String MAIN_MODE = "main_mode";
	private static final String PERSON = "person";
	private static final String TRIP_NUMBER = "trip_number";
	private static final String TRAVELED_DISTANCE = "traveled_distance";
	private static final String AGE = "age";
	private static final String AGE_GROUP = "age_group";
	private static final String INCOME = "income";
	private static final String INCOME_GROUP = "income_group";
	private static final String SUBPOPULATION = "subpopulation";
	private static final String ECONOMIC_STATUS = "economic_status";
	private static final String EMPLOYMENT = "employment";
	private static final String DIST_GROUP = "dist_group";
	private static final String UNKNOWN = "unknown";

	@CommandLine.Mixin
	private final InputOptions input = InputOptions.ofCommand(ScoringAnalysis.class);
	@CommandLine.Mixin
	private final OutputOptions output = OutputOptions.ofCommand(ScoringAnalysis.class);

	@CommandLine.Option(names = "--subpopulation", defaultValue = "person",
		description = "Subpopulation to analyse; its scoring parameters are used")
	private String subpopulation;
	@CommandLine.Option(names = "--dist-groups", split = ",", description = "List of distances for binning", defaultValue = "0,1000,2000,5000,10000,20000")
	private List<Integer> distGroups;
	@CommandLine.Option(names = "--age-groups", split = ",", defaultValue = "0,12,18,25,35,66",
		description = "Age bin brackets -> 0-12, 12-25, 25-35, 35-66,66+")
	private List<Integer> ageGroups;
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

	public static void main(String[] args) {
		new ScoringAnalysis().execute(args);
	}

	@Override
	public Integer call() throws Exception {

		Table persons = Table.read().csv(CsvReadOptions.builder(IOUtils.getBufferedReader(input.getPath("persons.csv")))
			.columnTypesPartial(Map.of(
				"person", ColumnType.STRING,
				"age", ColumnType.DOUBLE,
				"income", ColumnType.DOUBLE,
				"subpopulation", ColumnType.STRING))
			.sample(false)
			.separator(CsvOptions.detectDelimiter(input.getPath("persons.csv"))).build());
		persons = persons.selectColumns("person", "age", "income", "subpopulation", "employment", "economic_status");

		Table expPlan = parseXML(input.getPath("experienced_plans.xml"));


//		writeElasticityStatsPerModeGrouped(joined, AGE_GROUP, ageLabels);

		return 0;
	}

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
	 * "0-12", "12-25", ..., "66+".
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

	private Table parseXML(String experienced_plans) {
		try {
			File fXmlFile = new File(experienced_plans);

			Table table = Table.create(
				StringColumn.create("Person ID"),
				DoubleColumn.create("Plan Score")

			);

			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			Document doc = dBuilder.parse(fXmlFile);
			doc.getDocumentElement().normalize();

			log.info("Parsing XML file");

			NodeList personList = doc.getElementsByTagName("person");

			for (int i = 0; i < personList.getLength(); i++) {
				Node nNode = personList.item(i);
				if (nNode.getNodeType() == Node.ELEMENT_NODE) {
					Element personElement = (Element) nNode;
					String personId = personElement.getAttribute("id");

					NodeList planList = personElement.getElementsByTagName("plan");
					for(int j = 0; j < planList.getLength(); j++) {
						Element planElement = (Element) planList.item(j);
						String score = planElement.getAttribute("score");

						table.stringColumn("person ID").append(personId);
						table.doubleColumn("Plan Score").append(Double.parseDouble(score));
					}
				}
			}
			System.out.println(table);
			return table;

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void writeElasticityStatsPerModeGrouped(Table joined, String groupColumn, List<String> groups, String experienced_plans) throws IOException {

		groups.add(UNKNOWN);

		try (CSVPrinter printer = new CSVPrinter(
			Files.newBufferedWriter(Path.of(String.format("C:/Users/teddy/Desktop/FG_VSP/test/person_scorestats.csv"), groupColumn)), CSVFormat.DEFAULT)) {

			printer.printRecord(groupColumn, "avg_plan_Score");

			for (String g : groups) {

				double avgScore = joined.numberColumn("plan_score")
					.where(joined.stringColumn("subpopulation").isEqualTo("person"))
					.mean();

				printer.printRecord(g, avgScore);

			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}


