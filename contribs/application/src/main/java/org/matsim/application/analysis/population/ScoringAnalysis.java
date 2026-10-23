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

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import org.w3c.dom.Element;
import tech.tablesaw.api.DoubleColumn;
import tech.tablesaw.api.StringColumn;
import tech.tablesaw.api.Table;
import tech.tablesaw.joining.DataFrameJoiner;

import java.io.File;

import java.util.List;

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

		Config config = ConfigUtils.loadConfig(input.getPath("config.xml"));

		Scenario scenario = ScenarioUtils.createScenario(config);

		PopulationReader reader = new PopulationReader(scenario);
		reader.readFile(input.getPath("experienced_plans.xml.gz"));

		parseXML(input.getPath("experienced_plans.xml"));

		log.info("Loaded " + scenario.getPopulation().getPersons().size() + " experienced plans.");

		return 0;
	}

	private void parseXML(String experienced_plans) {
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

			System.out.println("Root element :" + doc.getDocumentElement().getNodeName());
			NodeList personList = doc.getElementsByTagName("person");
			System.out.println("----------------------------");

			for (int i = 0; i < personList.getLength(); i++) {
				Node nNode = personList.item(i);
				System.out.println("\nCurrent Element :" + nNode.getNodeName());
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

		} catch (Exception e) {
			e.printStackTrace();
		}
		log.info("Parsing XML file");
	}
}


