package org.matsim.application.analysis.population;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.NonNull;
import org.matsim.api.core.v01.population.Person;
import org.matsim.application.CommandSpec;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.options.CsvOptions;
import org.matsim.application.options.InputOptions;
import org.matsim.application.options.OutputOptions;
import org.matsim.core.utils.io.IOUtils;
import picocli.CommandLine;

import tech.tablesaw.api.*;
import tech.tablesaw.io.csv.CsvReadOptions;
import tech.tablesaw.joining.DataFrameJoiner;

import static java.lang.IO.print;
import static org.matsim.contrib.common.conventions.vsp.SubpopulationDefaultNames.SUBPOP_FREIGHT;
import static org.matsim.contrib.common.conventions.vsp.SubpopulationDefaultNames.SUBPOP_PERSON;
import static tech.tablesaw.aggregate.AggregateFunctions.mean;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

@CommandLine.Command(name = "scoring", description = "Calculates various scoring related metrics.")
@CommandSpec(
	requires = {"persons.csv"},
	produces = {
		"avgscorestats.csv", "person_avgscorestats_mode.csv", "person_avgscorestats_per_%s.csv", "person_scorestats_%s.csv"
	}
)

public class ScoringAnalysis implements MATSimAppCommand {

	private static final Logger log = LogManager.getLogger(ScoringAnalysis.class);

	// how could we make this more generic? We might have scenarios with different
	// attributes and then this will fail. Maybe look into "TripBySociodemographicGroupsAnalysis"
	// Although we do not have a ref-file here the concept might be applicable.
	// Something like --group-by age=0-12,12-18,18-25,...;employment and then use the class
	// "Category" DR20261103
	//
	// Gleiche Logik wie TripBySociodemographicGroupsAnalysis.java
	//
	// Static Final Variables
	private static final String AGE = "age";
	private static final String INCOME = "income";
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

	@CommandLine.Option(names = "--group-by", description = "Define the subpopulations for the analysis of the person traffic and defines if the have different groups. If a group is defined by several subpopulations," +
		"split them by ','. and different groups are separated by ';'. The analysis output will be for the given groups.",
		defaultValue =
			"age=0-12,12-25,25-35,35-66,66+;" +
			"income=0-1000,1000-1250,1250-1500,1500-1750,1750-2000,2000-2500,2500-3000,3000-3500,3500+;" +
			"employment=trainee,child,homemaker,retiree,unemployed,school,student,job_full_time,job_part_time,other;" +
			"economic-status=very_low,low,medium,high,very_high")
	private String groupBy;



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

		ScoringBySociodemographicGroupsAnalysis sociodemographicGroups = null;
		if(groupBy != null){
			sociodemographicGroups = new ScoringBySociodemographicGroupsAnalysis(groupBy);
			sociodemographicGroups.groupPersons(persons);
		}

		// WRITE GROUPED AVGSCORESTATS PER PERSONGROUPS
		if (sociodemographicGroups != null) {
			Table filteredForPersons = persons.where(
				persons.stringColumn(SUBPOPULATION).isEqualTo(SUBPOP_PERSON));
			sociodemographicGroups.writeScoreStats(filteredForPersons,(g) -> output.getPath("person_avgscorestats_per_%s.csv", g));
		}

		// WRITE AVGSCORESTATS FOR ALL
		writeAvgScoreStats(persons);

		return 0;
	}

	private void writeAvgScoreStats(Table persons) throws IOException{
		Path out = output.getPath("avgscorestats.csv");

		StringColumn groupColResult = StringColumn.create("subpopulation");
		DoubleColumn scoreColResult = DoubleColumn.create("average_score");


		for (String s:subpopulations){
			Table subset = persons.where(persons.stringColumn(SUBPOPULATION).isEqualTo(s));
			double mean = subset.numberColumn("executed_score").mean();

			groupColResult.append(s);
			scoreColResult.append(mean);
		}

		Table result = Table.create(groupColResult, scoreColResult);
		if (out.getParent() != null) {
			Files.createDirectories(out.getParent());
		}
		result.write().csv(out.toString());
	}
}


