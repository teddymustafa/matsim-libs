package org.matsim.application.analysis.population;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.tablesaw.api.*;
import tech.tablesaw.columns.Column;


import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static tech.tablesaw.aggregate.AggregateFunctions.mean;

final class ScoringBySociodemographicGroupsAnalysis {

	private final static Logger log = LogManager.getLogger(ScoringBySociodemographicGroupsAnalysis.class);

	private final List<Group> groups;

	private Map<String, Category> categories;

	 static void main(String[] args) throws IOException {
		var analysis = new ScoringBySociodemographicGroupsAnalysis("age=0-12,12-18,18+;income=0-1000,1000-1250,1250-1500,1500-1750,1750-2000,2000-2500,2500-3000,3000-3500, 3500+");
		System.out.println(analysis); // or call methods and print results
	}

	ScoringBySociodemographicGroupsAnalysis(String groupBy) throws IOException {
		this.groups = new ArrayList<>();
		this.categories = new HashMap<>();



		// Create Categories from groupBy-string
		// for loop iterates the following:
		// [age=0-12,12-18,18+],[license=yes,no]
		 for(String attributes: groupBy.split(";")) {
			 //separate attribute from its values:
			 // [age],[0-12,12-18,18+]
			 String[] attributeValues = attributes.split("=");
			 String attribute = attributeValues[0].trim().replace('-', '_');   // economic-status zu economic_status

			 Category category;
			 Table data;
			// Returns empty set when attribute is empty
			if( attributeValues.length == 1 ) {
				category = new Category(Set.of());
			}
			// Put attributeValues[1] in a set of categories
			else {

				category = new Category(new LinkedHashSet<>(
                Arrays.asList(attributeValues[1].split(","))));

			}
			categories.put(
				attribute, // age, income
				category
			);

			groups.add(new Group(List.of(attribute)));

		}

		log.info("Detected Groups {}:", categories.keySet());
		log.info("Ranges Detected {}:", categories.values());
	}

	void groupPersons(Table persons) {
		for (Group g : groups) {
			for (String c : g.columns) {

				if (!persons.columnNames().contains(c)) {
					log.error("Column {} not found in persons table", c);
					persons.addColumns(StringColumn.create(c, persons.rowCount()));
					continue;
				}

				Column<?> column = persons.column(c);

				StringColumn copy = column.emptyCopy(column.size()).asStringColumn().setName(c);
				column.mapInto((Object value) -> categories.get(c).categorize(value), copy);
				persons.replaceColumn(c, copy);
			}
		}
	}

	void writeScoreStats(Table filteredPersons, Function<String, Path> output){

		DoubleColumn scoreCol = DoubleColumn.create("executed_score");


		 for(Group group : groups){
			 String[] byCols = group.columns.toArray(new String[0]);

			 Table stats = filteredPersons.summarize("executed_score", mean).by(byCols);
			 stats.column(stats.columnCount() -1).setName("average_score");


			 // Putting the Groups in Order
			 Comparator<Row> cmp = null;
			 for (String c:byCols){
				 Comparator<Row> next = Comparator.comparing((Row r) -> r.getString(c), BIN_ORDER);
				 cmp = (cmp == null) ? next : cmp.thenComparing(next);
			 }

			 stats = stats.sortOn(cmp);

			 String name = String.join("_", group.columns);
			 stats.write().csv(output.apply(name).toFile());

		 }

	}

	private static final Pattern LEADING_INT = Pattern.compile("^\\d+");

	private static final Comparator<String> BIN_ORDER = Comparator.comparingInt((String s) -> {
		Matcher m = LEADING_INT.matcher(s.trim());
		return m.find() ? Integer.parseInt(m.group()) : Integer.MAX_VALUE;
	}).thenComparing(Comparator.naturalOrder());

	private record Group(List<String> columns
//		,Table data
	) {
		}
}
