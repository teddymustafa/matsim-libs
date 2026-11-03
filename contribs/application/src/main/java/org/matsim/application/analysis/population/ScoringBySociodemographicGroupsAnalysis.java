package org.matsim.application.analysis.population;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.core.utils.io.IOUtils;
import tech.tablesaw.api.*;
import tech.tablesaw.io.csv.CsvReadOptions;
import tech.tablesaw.selection.Selection;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.*;

final class ScoringBySociodemographicGroupsAnalysis {

	private final static Logger log = LogManager.getLogger(ScoringBySociodemographicGroupsAnalysis.class);

	private final List<Group> groups;

	private Map<String, Category> categories;

	 static void main(String[] args) throws IOException {
		var analysis = new ScoringBySociodemographicGroupsAnalysis("C:/Users/teddy/Desktop/FG_VSP/output/berlin-v7.1-1pct.output_persons.csv");
		System.out.println(analysis); // or call methods and print results
	}

	ScoringBySociodemographicGroupsAnalysis(String refData) throws IOException {

		try (BufferedReader reader = IOUtils.getBufferedReader(refData)) {
			Table ref = Table.read().csv(CsvReadOptions.builder(reader)
				.columnTypes((column) -> column.equals("executed_score") ? ColumnType.DOUBLE : ColumnType.STRING)
				.sample(false).separator(';')
				.build());

			List<String> columns = new ArrayList<>(ref.columnNames());
			// remove non group columns
			columns.removeAll(Set.of("dist_group", "main_mode", "executed_score"));

			// Collect all contained groups
			Set<List<String>> groups = new HashSet<>();
			for (Row row : ref) {

				List<String> g = new ArrayList<>();
				for (String c : columns) {
					if (!row.getString(c).isEmpty())
						g.add(c);
				}
				if (!g.isEmpty())
					groups.add(g);
			}

			log.info("Detected groups: {}", groups);

			this.groups = new ArrayList<>();

			for (List<String> g : groups) {

				Selection sel = Selection.withRange(0, ref.rowCount());
				for (String c : g) {
					sel = sel.and(ref.stringColumn(c).isNotEqualTo(""));
				}

				Table gRef = ref.where(sel);
				this.groups.add(new ScoringBySociodemographicGroupsAnalysis.Group(g, gRef));
			}

			this.categories = new HashMap<>();
			for (List<String> group : groups) {
				for (String g : group) {
					if (!this.categories.containsKey(g)) {
						this.categories.put(g, new Category(ref.column(g).asStringColumn().removeMissing().asSet()));
					}
				}
			}

			// Norm shares per instance of each group to sum of 1
			for (ScoringBySociodemographicGroupsAnalysis.Group group : this.groups) {

				String norm = group.columns.getFirst();
				if (group.columns.size() > 1)
					throw new UnsupportedOperationException("Multiple columns not supported yet");

				Table df = group.data;
				for (String label : df.stringColumn(norm).asSet()) {
					DoubleColumn dist_group = df.doubleColumn("executed_score");
					Selection sel = df.stringColumn(norm).isEqualTo(label);
					double total = dist_group.where(sel).sum();
					if (total > 0)
						dist_group.set(sel, dist_group.divide(total));
				}
			}
		}
	}

	private record Group(List<String> columns, Table data) {
	}
}
