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
import tech.tablesaw.api.DoubleColumn;
import tech.tablesaw.api.StringColumn;
import tech.tablesaw.api.Table;
import tech.tablesaw.io.csv.CsvReadOptions;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class ElasticityAnalysisTest {

	private static final double BETA = 1.0;
	private static final double RATE_CAR = -0.0002;
	private static final double RATE_RIDE = -0.00015;

	@RegisterExtension
	private final MatsimTestUtils utils = new MatsimTestUtils();
	private final CsvOptions csv = new CsvOptions(CSVFormat.Predefined.Default);

	@Test
	void defaultParametersTest() throws IOException {

		writeInputFiles();

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
			.isDirectoryContaining("glob:**elasticity_stats_*.csv");
		Path.of(utils.getInputDirectory()).toFile().delete();
	}

	@Test
	void modeFilterTest() throws IOException{
		writeInputFiles();

		List<String> modes = List.of("car", "ride");

		for (String m : modes){
			new ElasticityAnalysis().execute(
				"--input-trips", Path.of(utils.getInputDirectory(), "trips.csv").toString(),
				"--input-persons", Path.of(utils.getInputDirectory(), "persons.csv").toString(),
				"--input-config", Path.of(utils.getInputDirectory(), "config.xml").toString(),
				"--subpopulation","person",
				"--modes",m,
				"--output-elasticity-stats", Path.of(utils.getOutputDirectory(), "analysis", "population", "elasticity_stats.csv").toString(),
				"--output-elasticity-stats-%s", Path.of(utils.getOutputDirectory(), "analysis", "population", "elasticity_stats_%s.csv").toString()
			);

			Table elasticity = Table.read().csv(CsvReadOptions.builder(IOUtils.getBufferedReader(Path.of(utils.getOutputDirectory(), "analysis", "population", "elasticity_stats.csv").toString()))
				.columnTypesPartial(Map.of("person", ColumnType.STRING))
				.sample(false)
				.separator(CsvOptions.detectDelimiter(Path.of(utils.getOutputDirectory(), "analysis", "population", "elasticity_stats.csv").toString())).build());

			StringColumn mainMode = elasticity.stringColumn("main_mode");

			//		only 1 row with values
			Assertions.assertThat(elasticity.rowCount()).isEqualTo(1);
			//		only mode car, no mode goods in mode share stats
			Assertions.assertThat(mainMode.get(0)).isEqualTo(m);
		}
		Path.of(utils.getInputDirectory()).toFile().delete();
	}

	@Test
	void calcPEDTest() throws IOException {

		writeInputFiles();

		new ElasticityAnalysis().execute("--subpopulation", "person",
			"--input-trips", Path.of(utils.getInputDirectory(), "trips.csv").toString(),
			"--input-persons", Path.of(utils.getInputDirectory(), "persons.csv").toString(),
			"--input-config", Path.of(utils.getInputDirectory(), "config.xml").toString(),

			"--output-elasticity-stats", Path.of(utils.getOutputDirectory(), "analysis", "population", "elasticity_stats.csv").toString(),
			"--output-elasticity-stats-%s", Path.of(utils.getOutputDirectory(), "analysis", "population", "elasticity_stats_%s.csv").toString()
			);


		Path dir = Path.of(utils.getOutputDirectory(), "analysis", "population");

		Assertions.assertThat(dir.resolve("elasticity_stats.csv"))
			.exists()
			.isNotEmptyFile();

		for (String group : List.of("age_group", "economic_status", "employment", "income_group")) {
			Assertions.assertThat(dir.resolve("elasticity_stats_%s.csv".formatted(group)))
				.exists()
				.isNotEmptyFile();
		}

		// Read elasticity_stats.csv into a Tablesaw table for data content assertions
		Table elasticity = Table.read().csv(CsvReadOptions.builder(IOUtils.getBufferedReader(Path.of(utils.getOutputDirectory(), "analysis", "population", "elasticity_stats.csv").toString()))
		   .columnTypesPartial(Map.of("person", ColumnType.STRING))
		   .sample(false)
		   .separator(CsvOptions.detectDelimiter(Path.of(utils.getOutputDirectory(), "analysis", "population", "elasticity_stats.csv").toString())).build());

		StringColumn mainMode = elasticity.stringColumn("main_mode");

		Assertions.assertThat(elasticity.rowCount()).isEqualTo(2);

		Assertions.assertThat(mainMode.get(0)).isEqualTo("car");
		Assertions.assertThat(mainMode.get(1)).isEqualTo("ride");

		Table car = elasticity.where(elasticity.stringColumn("main_mode").isEqualTo("car"));
		Table ride = elasticity.where(elasticity.stringColumn("main_mode").isEqualTo("ride"));

		Assertions.assertThat(car.numberColumn("avg_distance").getDouble(0))
			.isEqualTo(4125.0);
		Assertions.assertThat(car.numberColumn("n_Trips").get(0))
			.isEqualTo(4);
		Assertions.assertThat(ride.numberColumn("n_Trips").get(0))
			.isEqualTo(1);
		Assertions.assertThat(ride.numberColumn("avg_distance").getDouble(0))
			.isEqualTo(3000.0);

		Path.of(utils.getInputDirectory()).toFile().delete();
	}

	private void writeInputFiles() throws IOException {

		Path dir = Path.of(utils.getInputDirectory());
		Files.createDirectories(dir);


		//		print dummy persons
		CSVPrinter printer = csv.createPrinter(dir.resolve("persons.csv"));
		printer.printRecord("person", "subpopulation", "age", "income", "economic_status", "employment");
		printer.printRecord("p1","person","48","4800","high", "job_full_time");
		printer.printRecord("p2","person","22","1300","middle", "student");
		printer.printRecord("p3","person","12","250","low", "child");
		printer.printRecord("f1","freight", "","","","");
		printer.close();

		//        print dummy trips
        printer = csv.createPrinter(dir.resolve("trips.csv"));
        printer.printRecord("person", "trip_id", "main_mode", "longest_distance_mode","traveled_distance");
        printer.printRecord("p1","p1_1","car","car","500");
        printer.printRecord("p1","p1_2","ride","ride","3000");
        printer.printRecord("p2","p2_1","car","car","1000");
        printer.printRecord("p2","p2_2","car","car","3000");
        printer.printRecord("p2","p2_3","walk","walk","800");
        printer.printRecord("p2","p2_4","car","car","12000");
        printer.printRecord("p3","p3_1","bike","bike","1000");
        printer.printRecord("p3","p3_2","bike","bike","1000");
        printer.printRecord("f1","f1_1","car","car","50000");
        printer.close();

		String xml = """
					<?xml version="1.0" encoding="UTF-8"?>
					<!DOCTYPE config SYSTEM "http://www.matsim.org/files/dtd/config_v2.dtd">
					<config>
					  <module name="scoring">
						<parameterset type="scoringParameters">
						  <param name="marginalUtilityOfMoney" value="%s" />
						  <parameterset type="modeParams">
							<param name="mode" value="car" />
							<param name="monetaryDistanceRate" value="%s" />
						  </parameterset>
						  <parameterset type="modeParams">
							<param name="mode" value="ride" />
							<param name="monetaryDistanceRate" value="%s" />
						  </parameterset>
						</parameterset>
					  </module>
					</config>
					""".formatted(BETA, RATE_CAR, RATE_RIDE);

		Files.writeString(dir.resolve("config.xml"), xml, StandardCharsets.UTF_8);
	}

}
