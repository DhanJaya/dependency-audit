package org.dep.analyzer;

import org.dep.util.CommandExecutor;

import java.io.File;
import java.io.IOException;

public class GraphAnalyzer {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Please insert 2 arguments containing the project path and the project artifact name");
        }
        String mvnProjectPath = args[0];
        String mvnArtifact = args[1];

        // verify if the project is actually a Maven project
        File projectDir = new File(mvnProjectPath);

        if (!projectDir.exists() || !new File(projectDir, "pom.xml").exists()) {
            System.out.println("Invalid Maven project path: " + mvnProjectPath);
            return;
        }
        GraphAnalyzer graphAnalyzer = new GraphAnalyzer();
        graphAnalyzer.extractDependencyTree(projectDir);

    }

    public void extractDependencyTree(File projectDir) {
        // For windows need to use mvn.cmd instead of mvn
        try {
            if (CommandExecutor.executeCommand("mvn.cmd dependency:tree -DoutputTyoe=json -DoutputFile=testTree.json", projectDir)) {
                // Read json file to extract the dependency tree
                // check if file exits


            } else {
                System.out.println("Failed to generate the dependency tree for the project");

            }
        } catch (IOException | InterruptedException e) {
            System.out.println("Error occurred while generating the dependency tree");
        }
    }
}
