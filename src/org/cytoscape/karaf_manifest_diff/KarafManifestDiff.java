package org.cytoscape.karaf_manifest_diff;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import org.osgi.framework.Version;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
/**
 * Compares two files, hard-coded and stored in the resources directory.
 * 
 * Each file contains all the packages exported from a running version of Cytoscape with only the
 * core apps included. Each file was generated via the Karaf `package:exports` command in the 
 * Karaf console.
 * 
 * If you are creating these files again, be aware of the following:
 * 
 * - Since Cytoscape 3.6 stripped some core features, in order to use the 'package:exports' 
 *   command you'll have to first install the 'package' feature, which can be done from the 
 *   Karaf command line using the command `feature:install package'
 * 
 * The format of the results from each version of Cytoscape are mildly different, with 3.6 and 
 * 3.7 using the delimiters | and │ respectively (annoyingly similar though).
 * 
 * org.osgi.framework.Version is used internally to turn the version strings into Java data.
 * 
 * With the packages and versions loaded we can create a long printout, which contains in order:
 * - The old package exports, organized by originating bundle
 * - The new package exports, organized by originating bundle
 * - A list of packages that the new Cytoscape Karaf configuration is no longer exporting.
 * - Warnings for packages shared by both the new and old configuration that differ 
 *   by a MAJOR version number, as these might have breaking API changes.
 * 	
 * By default, all of these items are exported without any remarkable formatting.
 * 
 * The package names can then be used by Mike Kucera's very useful cy-jdeps utility, which can 
 * then find references to those packages within all of Cytoscape's Apps.
 * 
 * (https://github.com/mikekucera/cy-jdeps).
 * 
 * @author davidotasek
 *
 */
public class KarafManifestDiff {
	public static void main(String[] args) {

		
			System.out.println("Old package exports:");
			System.out.println("--------------------");
			Map<String, List<String[]>> oldManifests = loadManifests(new File("./resources/package_exports_3.6.0.txt"));
			Map<String, Version> oldVersionMap = getVersionMap(oldManifests);

			System.out.println();
			System.out.println("New package exports:");
			System.out.println("--------------------");
			Map<String, List<String[]>> newManifests = loadManifests(new File("./resources/package_exports_3.7.0-SNAPSHOT.txt"));
			Map<String, Version> newVersionMap = getVersionMap(newManifests);
			//System.out.println("New Version Export Package Count: " + newVersionMap.size());

			Set<String> missingPackages = new HashSet<String>(oldVersionMap.keySet());
			missingPackages.removeAll(newVersionMap.keySet());
			System.out.println();
			System.out.println("Missing packages in new version:");
			System.out.println("--------------------------------");
			for (String packageName : missingPackages) {
				System.out.println(packageName);
			}

			Set<String> sharedPackages = new HashSet<String>(newVersionMap.keySet());
			sharedPackages.retainAll(oldVersionMap.keySet());

			System.out.println();
			System.out.println("Major version differences:");
			System.out.println("--------------------------");
			for (String packageName : sharedPackages) {
				Version oldVersion = oldVersionMap.get(packageName);
				Version newVersion = newVersionMap.get(packageName);

				if (newVersion.getMajor() != oldVersion.getMajor()) {
					System.out.println(packageName);
				} else {
					//System.out.println("Major versions match: " + packageName + "\t" + oldVersion + "\t" + newVersion);
				}
			}
		//System.out.println(count);
	}

	private static Map<String, Version> getVersionMap(Map<String, List<String[]>> manifests) {
		Map<String, Version> versionMap = new HashMap<String, Version>();
		for (String bundle: manifests.keySet()) {
			System.out.println(bundle);
			List<String[]> lines = manifests.get(bundle);
			for (String[] columns : lines) {

				String packageName = columns[0];
				Version version = new Version(columns[1]);

				System.out.println("\t" + packageName + "\t" + version);

				if (!versionMap.containsKey(packageName)) {
					versionMap.put(packageName, version);
				} else {
					Version existingVersion = versionMap.get(packageName);
					if (existingVersion.compareTo(version) < 0) {
						System.out.println("\t\tShades previous version: " + existingVersion);
						versionMap.put(packageName, version);
					}
				}
			}
		}

		return versionMap;
	}



	private static Map<String, List<String[]>> loadManifests(File file) {
		Map<String, List<String[]>> output = new HashMap<String, List<String[]>>();


		try {
			BufferedReader in = new BufferedReader(new FileReader(file));
			//Read column headers
			in.readLine();
			//Read divider
			in.readLine();
			
		
			for (String line = in.readLine(); line != null; line = in.readLine()) {
				
				String[] columns = line.split("\\||│");
				for (int i = 0; i < columns.length; i++)
				{
					columns[i] = columns[i].trim();
				}
				if (columns.length < 4) {
					for (String column : columns) {
					System.out.println(column);}
				}
				String bundleName = columns[3];
				List<String[]> packageList = output.get(bundleName);
				if (packageList == null) {
					packageList = new ArrayList<String[]>();
				}
				packageList.add(columns);
				output.put(bundleName, packageList);
			}
			in.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return output;
	}
}
