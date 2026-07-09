package org.matsim.application.analysis.population;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

class ElasticityAnalysis {
	public static void main() {

		//TODO: dont use absolute path for interoperability
		File inputFile = new File(); // ABSOLUTER PFAD HIER
		File outputFile = new File(); // ABSOLUTER PFAD HIER

		String delimiter = ";";
		//TODO: selber in config.xml suchen
		double pricePerMeter = 1.49e-4;
		double mutts = 1.0;
		//TODO: Commandline Argument Funktionalität hinzufügen
		Set<String> relevantModes = Set.of("car", "ride");

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


			String headerLine = in.readLine();
			String[] header = headerLine.split(delimiter);

			int idxPerson = -1;
			int idxTravDist = -1;
			int idxMainMode = -1;
			int idxLongestMode = -1;

			for(int i = 0; i < header.length; i++){
				if (header[i].equals("person")) idxPerson = i;
				if (header[i].equals("traveled_distance")) idxTravDist = i;
				if (header[i].equals("main_mode")) idxMainMode = i;
				if (header[i].equals("longest_distance_mode")) idxLongestMode = i;
			}

			if(idxPerson == -1  || idxMainMode == -1 || idxTravDist == -1 || idxLongestMode == -1 ){
				throw new IllegalStateException(
					"Expected column not found in header:" + headerLine);
			}

			// Speichert Summe von traveled_distance je nach modes
			Map<String, Double> sumDistMode = new HashMap<>();
			// Speichert Summe von nPersons je nach modes
			Map<String, Set<String>> personsByMode = new HashMap<>();
			for (String mode : relevantModes){
				sumDistMode.put(mode, 0.0);
				personsByMode.put(mode, new HashSet<>());
			}

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

				// only keep rows where BOTH columns are car/ride
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
				double price = avgDistance*pricePerMeter;
				double modeShare = (double) nPersonsMode / nPersonsTotal;
				double elasticity = -mutts * (price * (1 - modeShare));

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
