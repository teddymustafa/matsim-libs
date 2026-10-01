package org.matsim.application.analysis.population;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.matsim.application.options.CsvOptions;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.testcases.MatsimTestUtils;
import tech.tablesaw.api.ColumnType;
import tech.tablesaw.api.StringColumn;
import tech.tablesaw.api.Table;
import tech.tablesaw.io.csv.CsvReadOptions;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.Map;



public class ElasticityAnalysisTest {

	@RegisterExtension
	private final MatsimTestUtils utils = new MatsimTestUtils();
	private final CsvOptions csv = new CsvOptions(CSVFormat.Predefined.Default);

	private static final double BETA = 0.5;
	private static final double RATE_CAR = -0.0002;
	private static final double RATE_RIDE = -0.0001;
	private static final double EPS = 1e-9;

	@TempDir
	Path runDir;

	private Path out;

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
			.isDirectoryContaining("glob:**elasticity_stats_%s.csv")
		;


		Path.of(utils.getInputDirectory()).toFile().delete();
	}

	@Test
	void personFilterTest() throws IOException {

		writeInputFiles();

		new ElasticityAnalysis().execute("--subpopulation", "person", "--modes","car,ride",

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

		Table elasticity = Table.read().csv(CsvReadOptions.builder(IOUtils.getBufferedReader(Path.of(utils.getOutputDirectory(), "analysis", "population", "elasticity_stats.csv").toString()))
			.columnTypesPartial(Map.of("person", ColumnType.STRING))
			.sample(false)
			.separator(CsvOptions.detectDelimiter(Path.of(utils.getOutputDirectory(), "analysis", "population", "elasticity_stats.csv").toString())).build());

		StringColumn mainMode = elasticity.stringColumn("main_mode");

		//		only 1 row with values
		Assertions.assertThat(elasticity.rowCount()).isEqualTo(1);
		//		only mode car, no mode goods in mode share stats
		Assertions.assertThat(mainMode.get(0)).isEqualTo("car");

		Path.of(utils.getInputDirectory()).toFile().delete();
	}

	private void writeInputFiles() throws IOException {
		Path dir = Path.of(utils.getInputDirectory());
		Files.createDirectories(dir);

		//	print dummy persons
		CSVPrinter printer = csv.createPrinter(dir.resolve("persons.csv"));
		printer.printRecord("person", "subpopulation", "age", "income", "economic_status", "employment");
		printer.printRecord("p1", "person", "10", "100", "low", "child");
		printer.printRecord("p2", "person", "40", "2500", "high", "job_full_time");
		printer.printRecord("p3", "person", "67", "1500", "medium", "retiree");
		printer.printRecord("f1", "freight", "", "", "", "");
		printer.close();

		//		print dummy trips
		printer = csv.createPrinter(dir.resolve("trips.csv"));
		printer.printRecord("person", "traveled_distance", "main_mode", "longest_distance_mode");
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

		//	print config with known scoring parameters
		PrintWriter w = new PrintWriter(dir.resolve("config.xml").toFile(), StandardCharsets.UTF_8);
		w.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
		w.println("<!DOCTYPE config SYSTEM \"http://www.matsim.org/files/dtd/config_v2.dtd\">");
		w.println("<config>");
		w.println("  <module name=\"scoring\">");
		w.println("    <parameterset type=\"scoringParameters\">");
		w.println("      <param name=\"marginalUtilityOfMoney\" value=\"" + BETA + "\" />");
		w.println("      <parameterset type=\"modeParams\">");
		w.println("        <param name=\"mode\" value=\"car\" />");
		w.println("        <param name=\"monetaryDistanceRate\" value=\"" + RATE_CAR + "\" />");
		w.println("      </parameterset>");
		w.println("      <parameterset type=\"modeParams\">");
		w.println("        <param name=\"mode\" value=\"ride\" />");
		w.println("        <param name=\"monetaryDistanceRate\" value=\"" + RATE_RIDE + "\" />");
		w.println("      </parameterset>");
		w.println("    </parameterset>");
		w.println("  </module>");
		w.println("</config>");
		w.close();

	}



}
