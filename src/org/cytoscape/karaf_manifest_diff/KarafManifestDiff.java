package org.cytoscape.karaf_manifest_diff;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import org.eclipse.osgi.framework.util.Headers;
import org.eclipse.osgi.util.ManifestElement;
import org.osgi.framework.BundleException;
import org.osgi.framework.Version;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
/**
 * Compares two files, hardcoded and stored in the resources/karaf_manifest_diff directory.
 * 
 * Each file contains all the bundle headers exported via the  Karaf `bundles:headers` command 
 * in the karaf console.
 * 
 * The results are gently massaged, because Karaf thought it prudent to replace that OSGi-standard
 * ":" character in every header with an " = ", thereby making the entire file impossible to 
 * parse immediately via a standard library.
 * 
 * Which brings us to the org.eclipse bundles, which we use to parse the massaged files and 
 * turn our files into data structures.
 * 
 * This allows us to create a long printout, which contains in order:
 * - The old package exports, organized by originating bundle
 * - The new package exports, organized by originating bundle
 * - A list of packages that the new Cytoscape Karaf configuration is no longer exporting.
 * - Warnings for packages shared by both the new and old configuration that differ 
 *   by a MAJOR version number, as these might have breaking API changes.
 * 	
 * By default, all of these items are exported without any remarkable formatting.
 * 
 * By including three arguments, starting with -cyjdeps, the final two package lists will be 
 * formatted to generate input compatible with the cyjdeps project, created by Mike Kucera 
 * (https://github.com/mikekucera/cy-jdeps).
 * 
 * Argument format:
 * 
 * -cyjdeps [cyjdeps app directory] [output file]
 * 
 * Example arguments: 
 * -cyjdeps '/media/davidotasek/skynet/IN/2018_01_05 Karaf Classes/downloaded_apps' app_class_analysis.txt
 * 
 * 
 * @author davidotasek
 *
 */
public class KarafManifestDiff {
	public static void main(String[] args) {

		boolean formatForCyJdeps = false;
		String cyJdepsAppDirectory = null;
		String cyJdepsDestinationFile = null;
		if (args.length > 0 && "-cyjdeps".equals(args[0])) {
			formatForCyJdeps = true;
			cyJdepsAppDirectory = args[1];
			cyJdepsDestinationFile = args[2];
		}



		try {
			System.out.println("Old package exports:");
			System.out.println("--------------------");
			Map<String, Headers<String, String>> oldManifests = loadManifests(new File("./resources/karaf_manifest_diff/cytoscape.3.6.0.MF"));
			Map<String, Version> oldVersionMap = getVersionMap(oldManifests);
			
			System.out.println();
			System.out.println("New package exports:");
			System.out.println("--------------------");
			Map<String, Headers<String, String>> newManifests = loadManifests(new File("./resources/karaf_manifest_diff/cytoscape.3.7.0-SNAPSHOT.MF"));
			Map<String, Version> newVersionMap = getVersionMap(newManifests);
			//System.out.println("New Version Export Package Count: " + newVersionMap.size());
			
			Set<String> missingPackages = new HashSet<String>(oldVersionMap.keySet());
			missingPackages.removeAll(newVersionMap.keySet());
			System.out.println();
			System.out.println("Missing packages in new version:");
			System.out.println("--------------------------------");
			for (String packageName : missingPackages) {
				printForJDeps(formatForCyJdeps, cyJdepsAppDirectory, cyJdepsDestinationFile, packageName);
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
					 printForJDeps(formatForCyJdeps, cyJdepsAppDirectory, cyJdepsDestinationFile, packageName);
				} else {
					//System.out.println("Major versions match: " + packageName + "\t" + oldVersion + "\t" + newVersion);
				}
			}
			
		} catch (BundleException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}


		//System.out.println(count);
	}

	// '/media/davidotasek/skynet/IN/2018_01_05 Karaf Classes/downloaded_apps'
	// app_class_analysis.txt
	private static void printForJDeps(boolean formatForCyJdeps, String cyJdepsAppDirectory,
	String cyJdepsDestinationFile, String line) {
		if (formatForCyJdeps) {
			if (cyJdepsAppDirectory.contains(" ")) {
				cyJdepsAppDirectory = "'" + cyJdepsAppDirectory + "'";
			}
			if (cyJdepsDestinationFile.contains(" ")) {
				cyJdepsDestinationFile = "'" + cyJdepsDestinationFile + "'";
			}
			System.out.println("python find_package.py "+cyJdepsAppDirectory+" " + line + " >> " + cyJdepsDestinationFile);
		} else {
			System.out.println(line);
		}
	}
	
	private static Map<String, Version> getVersionMap(Map<String, Headers<String, String>> manifests) throws BundleException {
		Map<String, Version> versionMap = new HashMap<String, Version>();
		for (String bundle: manifests.keySet()) {
			System.out.println(bundle);
			String exportPackage = manifests.get(bundle).get("Export-Package");
			ManifestElement[] elements = ManifestElement.parseHeader("Export-Package", exportPackage);
			if (elements != null) {
				Map<String, Version> elementVersionMap = getVersionMap(elements);
				for (Map.Entry<String, Version> entry : elementVersionMap.entrySet()) {
					System.out.println("\t" + entry.getKey() + "\t" + entry.getValue());

					if (!versionMap.containsKey(entry.getKey())) {
						versionMap.put(entry.getKey(), entry.getValue());
					} else {
						Version existingVersion = versionMap.get(entry.getKey());
						if (existingVersion.compareTo(entry.getValue()) < 0) {
							System.out.println("\t\tShades previous version: " + existingVersion);
							versionMap.put(entry.getKey(), entry.getValue());
						}
					}
				}
			}
		}
		return versionMap;
	}

	private static Map<String, Version> getVersionMap(ManifestElement[] elements) {
		Map<String, Version> output = new HashMap<String, Version>();
		for (ManifestElement element : elements) {
			if (!element.getValue().startsWith("org.cytoscape"))
			{ 	String versionString =  element.getAttribute("version");
			if (versionString != null && !versionString.equals("0.0.0"))
			{	
				Version version = new Version(versionString);
				output.put(element.getValue(), version);

				//System.out.println("\t" + element.getValue() + "\t" + version.toString());
			}
			}
		}
		return output;
	}

	private static boolean isDivider(String previousLine, String line) {
		if (previousLine == null) {
			return false;
		} else {
			if (previousLine.length() == line.length()) {
				int dashCount = 0;
				for (char c : line.toCharArray()) {
					if (c == '-') {
						dashCount++;
					}
				}
				return dashCount > 4 && dashCount == line.length();

			} 
			else {
				return false;
			}
		}
	}

	private static Map<String, Headers<String, String>> loadManifests(File file) throws BundleException {
		Map<String, Headers<String, String>> output = new HashMap<String, Headers<String, String>>();


		try {
			BufferedReader in = new BufferedReader(new FileReader(file));
			StringBuilder manifestStringBuilder = null;
			String bundle = null;
			String lastLine = null;
			int blankLineCount = 0;
			for (String line = in.readLine(); line != null; line = in.readLine()) {
				if (isDivider(lastLine, line)) {
					if (bundle != null && manifestStringBuilder != null) {
						Headers<String, String> headers = getManifest(bundle, manifestStringBuilder);
						output.put(bundle, headers);
					}
					bundle = lastLine;
					manifestStringBuilder = new StringBuilder();
					blankLineCount = 0;
				}	
				else {
					if (manifestStringBuilder != null && blankLineCount < 1) {
						manifestStringBuilder.append(line.replaceFirst(" = ", ": "));
						manifestStringBuilder.append("\n"); 
					}
				}
				if (line.trim().length() == 0) {
					blankLineCount++;
				}
				lastLine = line;
			}
			if (bundle != null && manifestStringBuilder != null) {
				Headers<String, String> headers = getManifest(bundle, manifestStringBuilder);
				output.put(bundle, headers);
			}
			in.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return output;
	}

	private static Headers<String, String> getManifest(String bundle, StringBuilder stringBuilder) throws IOException, BundleException {
		//String string = stringBuilder.toString();
		//System.out.println("<<<<<<< " + bundle);
		//System.out.println(string);
		//System.out.println(">>>>>>>");

		Headers<String, String> headers = Headers.parseManifest(new ByteArrayInputStream(stringBuilder.toString().getBytes(StandardCharsets.UTF_8.name())));
		return headers;
	}
}
