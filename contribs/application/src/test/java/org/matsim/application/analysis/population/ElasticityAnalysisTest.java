package org.matsim.application.analysis.population;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.matsim.application.options.CsvOptions;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.testcases.MatsimTestUtils;
import tech.tablesaw.api.ColumnType;
import tech.tablesaw.api.StringColumn;
import tech.tablesaw.api.Table;
import tech.tablesaw.io.csv.CsvReadOptions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class ElasticityAnalysisTest {

	@RegisterExtension
	private final MatsimTestUtils utils = new MatsimTestUtils();
	private final CsvOptions csv = new CsvOptions(CSVFormat.Predefined.Default);

	// no @Test?
	void defaultParametersTest() throws IOException {

		writeInputCsvFiles();

		new ElasticityAnalysis().execute(
			"--input-trips", Path.of(utils.getInputDirectory(), "trips.csv").toString(),
			"--input-persons", Path.of(utils.getInputDirectory(), "persons.csv").toString(),
			"--input-config", Path.of(utils.getInputDirectory(), "config.xml").toString(),

			"--output-elasticity-stats", Path.of(utils.getOutputDirectory(), "analysis", "population", "elasticity_stats.csv").toString(),
			"--output-elasticity-stats-%s", Path.of(utils.getOutputDirectory(), "analysis", "population", "elasticity_stats_%s.csv").toString()
			);

		Path out = Path.of(utils.getOutputDirectory(), "analysis", "population");

		Assertions.assertThat(out)
			.isDirectoryContaining("glob:**elasticity_stats.csv")
			.isDirectoryContaining("glob:**elasticity_stats_%s.csv")
		;

		// delete input? Why?
		Path.of(utils.getInputDirectory()).toFile().delete();
	}

	@Test
	void personFilterTest() throws IOException {

		writeInputCsvFiles();

		new ElasticityAnalysis().execute("--subpopulation", "person",
			"--input-trips", Path.of(utils.getInputDirectory(), "trips.csv").toString(),
			"--input-persons", Path.of(utils.getInputDirectory(), "persons.csv").toString(),
			"--input-config", Path.of(utils.getInputDirectory(), "config.xml").toString(),

			"--output-elasticity-stats", Path.of(utils.getOutputDirectory(), "analysis", "population", "elasticity_stats.csv").toString(),
			"--output-elasticity-stats-%s", Path.of(utils.getOutputDirectory(), "analysis", "population", "elasticity_stats_%s.csv").toString()
			);

//		Path out = Path.of(utils.getOutputDirectory(), "analysis", "population");
//
//		Assertions.assertThat(out)
//			.isDirectoryContaining("glob:**elasticity_stats.csv")
//			.isDirectoryContaining("glob:**elasticity_stats_%s.csv")
//		;

		Path dir = Path.of(utils.getOutputDirectory(), "analysis", "population");

		Assertions.assertThat(dir.resolve("elasticity_stats.csv"))
			.exists()
			.isNotEmptyFile();

		for (String group : List.of("age_group", "economic_status", "employment", "income_group")) {
			Assertions.assertThat(dir.resolve("elasticity_stats_%s.csv".formatted(group)))
				.exists()
				.isNotEmptyFile();
		}

		Table elasticity = Table.read().csv(CsvReadOptions.builder(IOUtils.getBufferedReader(Path.of(utils.getOutputDirectory(), "analysis", "population", "elasticity_stats.csv").toString()))
			.columnTypesPartial(Map.of("person", ColumnType.STRING))
			.sample(false)
			.separator(CsvOptions.detectDelimiter(Path.of(utils.getOutputDirectory(), "analysis", "population", "elasticity_stats.csv").toString())).build());

		StringColumn mainMode = elasticity.stringColumn("main_mode");

		//		only 1 row with values
		Assertions.assertThat(elasticity.rowCount()).isEqualTo(1);
		//		only mode car, no mode goods in mode share stats
		Assertions.assertThat(mainMode.get(0)).isEqualTo("car");

		// delete input? WHY?
		Path.of(utils.getInputDirectory()).toFile().delete();
	}

	private void writeInputCsvFiles() throws IOException {
		// do not write into input-directory in tests. The input-directory is git-tracked. Write to output instead.
		Path persons = Path.of(utils.getInputDirectory()).resolve("persons.csv");
		Files.createDirectories(persons.getParent());
		CSVPrinter printer = csv.createPrinter(persons);

//		print dummy persons
		printer.printRecord("person", "executed_score", "first_act_x", "first_act_y", "first_act_type", "age", "carAvail", "home_x", "home_y", "householdIncome", "householdSize",
			"income", "sex", "sim_ptAbo", "sim_regionType", "subpopulation", "purpose", "tourStartArea", "vehicleTypes", "economic_status", "employment");
		printer.printRecord("100", "-130.69951448065348", "369956.19", "5776578.61", "home_49200", "50", "always", "369956.19", "5776578.61", "5", "2", "1159.0", "f", "none", "124", "person", "", "", "", "high", "job_full_time");
		printer.close();

//		print dummy trips
		printer = csv.createPrinter(Path.of(utils.getInputDirectory(), "trips.csv"));
		printer.printRecord("person", "trip_number", "trip_id", "dep_time", "trav_time", "wait_time", "traveled_distance", "euclidean_distance", "main_mode", "longest_distance_mode",
			"modes", "start_activity_type", "end_activity_type", "start_facility_id", "start_link", "start_x", "start_y", "end_facility_id", "end_link", "end_x", "end_y", "first_pt_boarding_stop", "last_pt_egress_stop");
		printer.printRecord("100", "1", "100_1", "13:57:42", "00:34:07", "00:00:00", "53025", "34976", "car", "car", "walk-car-walk", "home_49200", "errands_3600", "null", "-199781090",
			"369956.19", "5776578.61", "null", "152273276", "401592.51", "5761661.71", "", "");
		printer.printRecord("100", "2", "100_2", "15:42:50", "01:01:52", "00:00:00", "78917", "54643", "car", "car", "walk-car-walk", "errands_3600", "errands_4200", "null", "152273276",
			"401592.51", "5761661.71", "null", "-366338372", "455444.34", "5752394.08", "", "");
		printer.printRecord("100_goodsTraffic", "1", "100_1", "13:57:42", "00:34:07", "00:00:00", "53025", "34976", "goods", "goods", "walk-goods-walk", "home_49200", "errands_3600", "null", "-199781090",
			"369956.19", "5776578.61", "null", "152273276", "401592.51", "5761661.71", "", "");
		printer.close();
	}

}
