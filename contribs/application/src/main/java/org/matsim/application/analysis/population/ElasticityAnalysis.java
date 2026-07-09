package org.matsim.application.analysis.population;

import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

class ElasticityAnalysis {
	public static void main() {

		//TODO: dont use absolute path for interoperability

		File configFile = new File("/home/teddymustafa/Desktop/FG-VSP/elasticity/berlin-v7.1-1pct.output_config.xml");
		Config config = ConfigUtils.loadConfig(configFile.getPath());

		File inputFile = new File("/home/teddymustafa/Desktop/FG-VSP/elasticity/berlin-v7.1-1pct.output_trips.csv.gz"); // ABSOLUTER PFAD HIER
		File outputFile = new File("home/teddymustafa/Desktop/FG-VSP/elasticity/analysis/elasticity/elasticity.csv"); // ABSOLUTER PFAD HIER


		String delimiter = ";";
		//TODO: Commandline Argument Funktionalität hinzufügen
		Set<String> relevantModes = Set.of("car", "ride");
		Map<String, Double> pricePerMeterByMode = new HashMap<>();

		for (String mode : relevantModes) {
			double rate = config.scoring()
					.getScoringParameters(null)
					.getModes()
					.get(mode)
					.getMonetaryDistanceRate();

			System.out.println("mode = " + mode + ", monetaryDistanceRate = " + rate);

			pricePerMeterByMode.put(mode, rate);
		}


		//TODO: selber in config.xml suchen
		double marginalUtilityOfMoney = config.scoring().getScoringParameters(null).getMarginalUtilityOfMoney();


		// creates any missing folders in the path
		outputFile.getParentFile().mkdirs();
		try(

			FileInputStream inSt = new FileInputStream(inputFile); // raw compressed bytes
			GZIPInputStream inGZ = new GZIPInputStream(inSt); // decompressed bytes
			InputStreamReader inReader = new InputStreamReader(inGZ, StandardCharsets.UTF_8);
			BufferedReader in = new BufferedReader(inReader);

			FileWriter outWriter = new FileWriter(outputFile);
			BufferedWriter out = new BufferedWriter(outWriter);
			) {


			// Checks for header
			String headerLine = in.readLine();

			// Array that stores header
			String[] header = headerLine.split(delimiter);

			// a placeholder its impossible for array to possess negative value*
			int idxPerson = -1;
			int idxTravDist = -1;
			int idxMainMode = -1;
			int idxLongestMode = -1;

			//checks for position of the relevant header and consequently the column
			for(int i = 0; i < header.length; i++){
				if (header[i].equals("person")) idxPerson = i;
				if (header[i].equals("traveled_distance")) idxTravDist = i;
				if (header[i].equals("main_mode")) idxMainMode = i;
				if (header[i].equals("longest_distance_mode")) idxLongestMode = i;
			}

			// *which is relevant for this IllegalStateException
			if(idxPerson == -1  || idxMainMode == -1 || idxTravDist == -1 || idxLongestMode == -1 ){
				throw new IllegalStateException(
					"Expected column not found in header:" + headerLine);
			}

			// Map that stores keystring "mode" and double "sum of distance"
			Map<String, Double> sumDistMode = new HashMap<>();
			// Map that stores keystring "mode" and set of  "personsByMode"
			Map<String, Set<String>> personsByMode = new HashMap<>();
			for (String mode : relevantModes){
				sumDistMode.put(mode, 0.0);
				personsByMode.put(mode, new HashSet<>());
			}

			// lets no duplicate
			Set<String> allPersons = new HashSet<>();

			String line;
			while ((line = in.readLine()) != null){
				if (line.isBlank()){
					continue;
				}

				String[] row = line.split(delimiter);

				String person = row[idxPerson];

				allPersons.add(person);

				String mainMode = row[idxMainMode];
				String longestMode = row[idxLongestMode];

				// filter(mainmode == car,ride && longestmode == car,ride )
				if(relevantModes.contains(mainMode) && relevantModes.contains(longestMode)){
					double distance = Double.parseDouble(row[idxTravDist]);
					sumDistMode.put(mainMode, sumDistMode.get(mainMode) + distance);
					personsByMode.get(mainMode).add(person);
				}
			}

			int nPersonsTotal = allPersons.size();

			out.write("mode,elasticity");
			out.newLine();

			for (String mode : relevantModes) {
				int nPersonsMode = personsByMode.get(mode).size();
				double sumDistance = sumDistMode.get(mode);

				double avgDistance = sumDistance/nPersonsMode;

				double pricePerMeter = pricePerMeterByMode.get(mode);
				double price = avgDistance*pricePerMeter;
				// anzahl trips bestimmter mode durch gesamtzahl des trips
				double modeShare = (double) nPersonsMode / nPersonsTotal;
				double elasticity = -marginalUtilityOfMoney * (price * (1 - modeShare));

				out.write(mode + "," + elasticity);
				out.newLine();

			}

			System.out.println("Results saved to "+ outputFile);

		}

		catch (Exception e) {
			System.out.println(e);
		}

	}
}
