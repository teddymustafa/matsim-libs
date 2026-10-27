package org.matsim.application.analysis.population;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.NonNull;
import org.matsim.application.CommandSpec;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.options.CsvOptions;
import org.matsim.application.options.InputOptions;
import org.matsim.application.options.OutputOptions;
import org.matsim.core.utils.io.IOUtils;
import picocli.CommandLine;

import tech.tablesaw.api.ColumnType;
import tech.tablesaw.api.DoubleColumn;
import tech.tablesaw.api.StringColumn;
import tech.tablesaw.api.Table;
import tech.tablesaw.io.csv.CsvReadOptions;
import tech.tablesaw.joining.DataFrameJoiner;

import static java.lang.IO.print;
import static org.matsim.contrib.common.conventions.vsp.SubpopulationDefaultNames.SUBPOP_FREIGHT;
import static org.matsim.contrib.common.conventions.vsp.SubpopulationDefaultNames.SUBPOP_PERSON;
import static tech.tablesaw.aggregate.AggregateFunctions.mean;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@CommandLine.Command(name = "scoring", description = "Calculates various scoring related metrics.")
@CommandSpec(
	requires = {"persons.csv"},
	produces = {
		"person_avgscore_%s.csv", "person_avgscore_freight.csv"
	}
)

public class ScoringAnalysis implements MATSimAppCommand {

	private static final Logger log = LogManager.getLogger(ScoringAnalysis.class);

	// Static Final Variables
	private static final String AGE = "age";
	private static final String AGE_GROUP = "age_group";
	private static final String INCOME = "income";
	private static final String INCOME_GROUP = "income_group";
	private static final String SUBPOPULATION = "subpopulation";
	private static final String ECONOMIC_STATUS = "economic_status";
	private static final String EMPLOYMENT = "employment";
	private static final String EXECUTED_SCORE = "executed_score";
	private static final String UNKNOWN = "unknown";

	@CommandLine.Mixin
	private final InputOptions input = InputOptions.ofCommand(ScoringAnalysis.class);
	@CommandLine.Mixin
	private final OutputOptions output = OutputOptions.ofCommand(ScoringAnalysis.class);

	@CommandLine.Option(names = "--subpopulation", split = ",", defaultValue = "person,freight",
		description = "Subpopulation to analyse; its scoring parameters are used")
	private List<String> subpopulations;
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
				SUBPOP_PERSON, ColumnType.STRING,
				AGE, ColumnType.DOUBLE,
				INCOME, ColumnType.DOUBLE,
				SUBPOPULATION, ColumnType.STRING))
			.sample(false)
			.separator(CsvOptions.detectDelimiter(input.getPath("persons.csv"))).build());
		persons = persons.selectColumns(SUBPOP_PERSON, EXECUTED_SCORE, AGE, INCOME, SUBPOPULATION, EMPLOYMENT, ECONOMIC_STATUS);

		addRangeGroup(persons, INCOME, incomeGroups, INCOME_GROUP);
		addRangeGroup(persons, AGE, ageGroups, AGE_GROUP);

		List<String> ageLabels = new ArrayList<>(createRangeLabels(ageGroups));
		ageLabels.add(UNKNOWN);

		List<String> incomeLabels = new ArrayList<>(createRangeLabels(incomeGroups));
		incomeLabels.add(UNKNOWN);

		for (String s:subpopulations){
			Table subPersons = persons.where(persons.stringColumn(SUBPOPULATION).isEqualTo(s));

			print("subpopulation: " + s);

			if(s.equals(SUBPOP_PERSON)){

				writeScoringStatsGrouped(subPersons, AGE_GROUP, ageLabels);
				writeScoringStatsGrouped(subPersons, INCOME_GROUP,incomeLabels);
				writeScoringStatsGrouped(subPersons, EMPLOYMENT,employStatus);
				writeScoringStatsGrouped(subPersons, ECONOMIC_STATUS,economicStatus);
				print(subPersons);
				System.out.println();
			}

			if(s.equals("freight")){
				Path out = output.getPath("person_avgscore_freight.csv");

				if (out == null) {
					// Universal relative fallback for local/standalone execution
					out = Path.of("analysis/population/person_avgscore_freight.csv");
				}


				Table f = subPersons.summarize(EXECUTED_SCORE, mean).apply();
				f.column(0).setName(EXECUTED_SCORE);

				if (out.getParent() != null) {
					Files.createDirectories(out.getParent());
				}
				f.write().csv(out.toString());
				print(subPersons);
				System.out.println();
			}


		}



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


	private void writeScoringStatsGrouped(Table persons, String groupColumn, List<String> labels
	) throws IOException {

			Path out = output.getPath("person_avgscore_%s.csv", groupColumn);

		if (out == null) {
			// Universal relative fallback for local/standalone execution
			out = Path.of("analysis/population/person_avgscore_%s.csv", groupColumn);
		}

			StringColumn key = persons.column(groupColumn).asStringColumn().setName(groupColumn);
			Table slim = Table.create(persons.numberColumn(EXECUTED_SCORE), key);

			StringColumn groupColResult = StringColumn.create(groupColumn);
			DoubleColumn scoreColResult = DoubleColumn.create("avg_plan_score");

			for(String l:labels){
				Table subset = slim.where(slim.stringColumn(groupColumn).isEqualTo(l));

				if(subset.isEmpty()){
					continue;
				}

				double meanScore = subset.numberColumn(EXECUTED_SCORE).mean();

				groupColResult.append(l);
				scoreColResult.append(meanScore);

			}

			Table result = Table.create(groupColResult, scoreColResult);
			if (out.getParent() != null) {
				Files.createDirectories(out.getParent());
			}
			result.write().csv(out.toString());

	}
}


