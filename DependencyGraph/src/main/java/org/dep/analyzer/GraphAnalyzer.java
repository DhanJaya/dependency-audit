package org.dep.analyzer;

import org.dep.model.DependencyModel;
import org.dep.util.CommandExecutor;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import fr.dutra.tools.maven.deptree.core.InputType;
import fr.dutra.tools.maven.deptree.core.Node;
import fr.dutra.tools.maven.deptree.core.ParseException;
import fr.dutra.tools.maven.deptree.core.Parser;

public class GraphAnalyzer {
    public static final String DEPENDENCY_TREE_FILE = "testTree.txt";
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

        LinkedList<Node> dependencyTree = extractDependencyTree(projectDir);
        // identify the duplicate libraries in the tree
        findDuplicates(dependencyTree);
    }


    private static void findDuplicates(LinkedList<Node> dependencyNodes) {
        List<DependencyModel> visitedNodes = new ArrayList<>();
        List<String> duplicateDeps = new ArrayList<>();
        int dependencyLevel = 1;
        searchTree(dependencyNodes, dependencyLevel, visitedNodes, duplicateDeps);

    }

    private static void searchTree(LinkedList<Node> dependencyNodes, int dependencyLevel, List<DependencyModel> visitedNodes, List<String> duplicateDeps) {
        dependencyNodes.forEach(dependencyNode -> {
            DependencyModel currentNode = new DependencyModel(dependencyNode.getGroupId(), dependencyNode.getArtifactId(), dependencyNode.getVersion(), dependencyNode.getClassifier(), dependencyLevel);

        });
    }

    private static LinkedList<Node> extractDependencyTree(File projectDir) {
        // For windows need to use mvn.cmd instead of mvn
        try {
            if (CommandExecutor.executeCommand(String.format("mvn.cmd dependency:tree -DoutputTyoe=json -DoutputFile=%s", DEPENDENCY_TREE_FILE), projectDir)) {
                // Read json file to extract the dependency tree
                // check if file exits
                File dependencyTreeFile = new File(projectDir, DEPENDENCY_TREE_FILE);
                if (!dependencyTreeFile.exists()) {
                    System.out.println("No dependency tree file created");
                } else {
                    return readDependencyTree(dependencyTreeFile);
                }
            } else {
                System.out.println("Failed to generate the dependency tree for the project");
            }
        } catch (IOException | InterruptedException e) {
            System.out.println("Error occurred while generating the dependency tree");
        }
        return new LinkedList<>();
    }

    private static LinkedList<Node> readDependencyTree(File depTreeFile) throws FileNotFoundException {
        Reader r = new BufferedReader(new InputStreamReader(new FileInputStream(depTreeFile), StandardCharsets.UTF_8));
        InputType type = InputType.TEXT;
        Parser parser = type.newParser();
        try {
            Node tree = parser.parse(r);
            if (tree != null) {
                return tree.getChildNodes();
            }
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return new LinkedList<>();
    }
}
