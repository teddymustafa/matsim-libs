package org.matsim.application.analysis.population;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.core.utils.io.IOUtils;
import tech.tablesaw.api.*;
import tech.tablesaw.columns.Column;
import tech.tablesaw.io.csv.CsvReadOptions;
import tech.tablesaw.selection.Selection;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;

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
			 Category category;
			 Table data;
			// Returns empty set when attribute is empty
			if( attributeValues.length == 1 ) {
				category = new Category(Set.of());
				data = Table.create(attributeValues[0],
					StringColumn.create(attributeValues[0]));
			}
			// Put attributeValues[1] in a set of categories
			else {

				category = new Category(

					Set.of(
						attributeValues[1].split(",")
					));
				data = Table.create(attributeValues[0], StringColumn.create(attributeValues[0],attributeValues[1].split(",")));
			}
			// Put the attribute itself (attributeValues[0]) as the key
			categories.put(attributeValues[0], category);

			groups.add(new Group(List.of(attributeValues[0])));

		}

		 Set<String> attributes = categories.keySet();


		log.info("Detected Groups {}:", attributes);
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

	void writeAvgScore(Table filteredPersons, Function<String, Path> output){

		DoubleColumn scoreColResult = DoubleColumn.create("avg_plan_score");

		 for(Group group : groups){
//			 List<String> columns = new ArrayList<>(List.of("executed_score"));
//			 columns.addAll(group.columns);
//
//			 String[] join = columns.toArray(new String[0]);
//
//			 Double meanScore = filteredPersons.numberColumn("executed_score").mean();
			 double mean = filteredPersons.numberColumn("executed_score").mean();

			 scoreColResult.append(mean);
		 }

		 Table slim = Table.create(
			 filteredPersons.numberColumn("executed_score"),
			 filteredPersons.numberColumn(String.valueOf(groups))
		 );
	}

	private record Group(List<String> columns
//		,Table data
	) {
		}
}
