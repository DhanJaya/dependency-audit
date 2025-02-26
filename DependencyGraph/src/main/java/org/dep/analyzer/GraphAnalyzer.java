package org.dep.analyzer;

import org.dep.model.DependencyModel;
import org.dep.util.CommandExecutor;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import fr.dutra.tools.maven.deptree.core.InputType;
import fr.dutra.tools.maven.deptree.core.Node;
import fr.dutra.tools.maven.deptree.core.ParseException;
import fr.dutra.tools.maven.deptree.core.Parser;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GraphAnalyzer {
    public static final String DEPENDENCY_TREE_FILE = "testTreecommomns.txt";
    private static final Logger logger = LoggerFactory.getLogger(GraphAnalyzer.class);

    public static void main(String[] args) {

        if (args.length < 2) {
            logger.error("Please insert 2 arguments containing the project path and the project artifact name");
        }
        String mvnProjectPath = args[0];
        String mvnArtifact = args[1];

        // verify if the project is actually a Maven project
        File projectDir = new File(mvnProjectPath);

        if (!projectDir.exists() || !new File(projectDir, "pom.xml").exists()) {
            logger.error(String.format("Invalid Maven project path: %s", mvnProjectPath));
            return;
        }

        Graph<Node, DefaultEdge>  dependencyTree = extractDependencyTree(projectDir);
        // identify the duplicate libraries in the tree
        //findDuplicates(dependencyTree);
        // LinkedList<Node> dependencyTree = readDependencyTree(new File("D:\\PhD\\workspace\\DeepDependencyAnalyzer\\testTree.txt"));
       // Graph<String, DefaultEdge> cfg = generateGraph(mvnArtifact, dependencyTree);
//        exportToMermaid(dependencyTree, Path.of("testingGraph1" + ".mermaid"));
//        exportToMermaid(mvnArtifact, dependencyTree, Path.of("testingGraph2" + ".mermaid"));
    }

    public static void exportToMermaid(String artifactName, LinkedList<Node> dependencyTree, Path file) {
        LinkedList<DependencyModel> visitedNodes = new LinkedList<>();
        Set<String> duplicateDeps = new HashSet<>();
        int dependencyLevel = 0;
        String NL = System.lineSeparator();
        String mermaid = "graph  LR;" + NL;

        mermaid = constructGraphWithDuplicates(artifactName, dependencyTree, dependencyLevel, visitedNodes, duplicateDeps, mermaid) + " classDef highlight fill:#ffcc00,stroke:#333;";
        try {
            Files.write(file, mermaid.getBytes());
        } catch (IOException e) {
            logger.error(String.format("failed to create the dependency graph file %s", file.toString()));
        }

    }


    public static void exportToMermaid(Graph<String, DefaultEdge> cfg, Path file) {
        String NL = System.lineSeparator();
        String mermaid = "graph TD;" + NL;
        for (DefaultEdge edge : cfg.edgeSet()) {
            mermaid = mermaid + "\t" + cfg.getEdgeSource(edge) + " --> " + cfg.getEdgeTarget(edge) + NL;
        }
        try {
            Files.write(file, mermaid.getBytes());
        } catch (IOException e) {
            logger.error(String.format("failed to create the dependency graph file %s", file.toString()));
        }
    }

    protected static LinkedList<DependencyModel> findDuplicates(LinkedList<Node> dependencyNodes) {
        LinkedList<DependencyModel> visitedNodes = new LinkedList<>();
        Set<String> duplicateDeps = new HashSet<>();
        int dependencyLevel = 0;
        searchTree(dependencyNodes, dependencyLevel, visitedNodes, duplicateDeps);
        duplicateDeps.forEach(System.out::println);
        return visitedNodes;
    }

    protected static void searchTree(LinkedList<Node> dependencyNodes, int dependencyLevel, List<DependencyModel> visitedNodes, Set<String> duplicateDeps) {
        dependencyNodes.forEach(dependencyNode -> {
            DependencyModel currentNode = new DependencyModel(dependencyNode.getGroupId(), dependencyNode.getArtifactId(), dependencyNode.getVersion(), dependencyNode.getClassifier(), dependencyLevel, dependencyNode.isOmitted());
            visitedNodes.forEach(visitedNode -> {
                if (visitedNode.getGroupId().equals(dependencyNode.getGroupId()) && visitedNode.getArtifactId().equals(dependencyNode.getArtifactId())) {
                    duplicateDeps.add(currentNode.getDependencyName(false));
                }
            });
            visitedNodes.add(currentNode);
            if (!dependencyNode.getChildNodes().isEmpty()) {
                searchTree(dependencyNode.getChildNodes(), dependencyLevel + 1, visitedNodes, duplicateDeps);
            }
        });
    }

    protected static String constructGraphWithDuplicates(String parentNode, LinkedList<Node> dependencyNodes, int dependencyLevel, List<DependencyModel> visitedNodes, Set<String> duplicateDeps, String mermaid) {
        StringJoiner nodeInLevel = new StringJoiner(" & ");
        for (Node dependencyNode : dependencyNodes) {

            DependencyModel currentNode = new DependencyModel(dependencyNode.getGroupId(), dependencyNode.getArtifactId(), dependencyNode.getVersion(), dependencyNode.getClassifier(), dependencyLevel, dependencyNode.isOmitted());
            String depName = String.format("L%s-%s:%s-%s%s", dependencyLevel, dependencyNode.getGroupId(), dependencyNode.getArtifactId(), dependencyNode.getVersion(), (dependencyNode.getClassifier() != null && !dependencyNode.getClassifier().isEmpty())
                    ? "-" + dependencyNode.getClassifier()
                    : "");
            for (DependencyModel visitedNode : visitedNodes) {
                if (visitedNode.getGroupId().equals(dependencyNode.getGroupId()) && visitedNode.getArtifactId().equals(dependencyNode.getArtifactId())) {
                    duplicateDeps.add(currentNode.getDependencyName(false));
                    depName = String.format("L%s-%s:%s-%s%s:::highlight", dependencyLevel, dependencyNode.getGroupId(), dependencyNode.getArtifactId(), dependencyNode.getVersion(), (dependencyNode.getClassifier() != null && !dependencyNode.getClassifier().isEmpty())
                            ? "-" + dependencyNode.getClassifier()
                            : "");
                }
            }
            nodeInLevel.add(depName);
            visitedNodes.add(currentNode);
            if (!dependencyNode.getChildNodes().isEmpty()) {
                mermaid = constructGraphWithDuplicates(depName, dependencyNode.getChildNodes(), dependencyLevel + 1, visitedNodes, duplicateDeps, mermaid);
            }
        }
        mermaid = mermaid + "\t" + parentNode + " --> " + nodeInLevel.toString() + ";" + System.lineSeparator();
        return mermaid;
    }

    protected static Graph<String, DefaultEdge> generateGraph(String startNode, LinkedList<Node> dependencyNodes) {
        Graph<String, DefaultEdge> depTree = new DefaultDirectedGraph<>(DefaultEdge.class);
        depTree.addVertex(startNode);
        for (Node node : dependencyNodes) {
            addNodesToGraph(startNode, node, depTree);
        }
        return depTree;
    }

    protected static void addNodesToGraph(String parentNode, Node currentNode, Graph<String, DefaultEdge> depTree) {

        String depName = String.format("%s:%s-%s", currentNode.getGroupId(), currentNode.getArtifactId(), currentNode.getVersion());
        depTree.addVertex(depName);
        depTree.addEdge(parentNode, depName);
        if (!currentNode.getChildNodes().isEmpty()) {
            for (Node childNode : currentNode.getChildNodes()) {
                addNodesToGraph(depName, childNode, depTree);
            }
        }
    }

    protected static Graph<Node, DefaultEdge> extractDependencyTree(File projectDir) {
        // For windows need to use mvn.cmd instead of mvn
        try {
            if (CommandExecutor.executeCommand(String.format("mvn.cmd dependency:tree -DoutputFile=%s -Dverbose", DEPENDENCY_TREE_FILE), projectDir)) {
                // Read json file to extract the dependency tree
                // check if file exits
                File dependencyTreeFile = new File(projectDir, DEPENDENCY_TREE_FILE);
                if (!dependencyTreeFile.exists()) {
                    logger.warn("Could not locate the file containing the dependency tree");
                } else {
                    return readDependencyTree(dependencyTreeFile);
                }
            } else {
                logger.warn("Failed to generate the dependency tree for the project");
            }
        } catch (IOException e) {
            logger.warn("Error occurred while generating the dependency tree");
        }
        throw new RuntimeException("Error occurred while generating the dependency tree");
    }

    protected static Graph<Node, DefaultEdge> readDependencyTree(File depTreeFile) {
        Reader r = null;
        try {
            r = new BufferedReader(new InputStreamReader(new FileInputStream(depTreeFile), StandardCharsets.UTF_8));
        } catch (FileNotFoundException e) {
            logger.warn(String.format("Failed to locate dependency tree file: %s", depTreeFile));
        }
        InputType type = InputType.TEXT;
        Parser parser = type.newParser();
        try {
            return parser.parse(r);
        } catch (ParseException e) {
            logger.warn(String.format("Failed to parse the dependency tree file: %s", depTreeFile));
        }
        throw new RuntimeException();
    }

}
