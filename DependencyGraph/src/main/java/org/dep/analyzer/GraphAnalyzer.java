package org.dep.analyzer;

import fr.dutra.tools.maven.deptree.core.*;
import fr.dutra.tools.maven.deptree.core.ParseException;
import fr.dutra.tools.maven.deptree.core.Parser;
import org.apache.commons.cli.*;
import org.dep.model.ColorTracker;
import org.dep.util.ColorGenerator;
import org.dep.util.CommandExecutor;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;

import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.traverse.BreadthFirstIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GraphAnalyzer {
    public static final String DEPENDENCY_TREE_FILE = "DepTree.txt";
    private static final Logger logger = LoggerFactory.getLogger(GraphAnalyzer.class);
    public static Option INPUT = Option.builder()
            .argName("aUrlOrPOMFile")
            .option("input")
            .hasArg()
            .required(true)
            .desc("Provides the project path")
            .build();

    public static Option FORMAT = Option.builder()
            .argName("json|md")
            .option("format")
            .hasArg()
            .required(true)
            .desc("the output format , md is markdown + mermaid")
            .build();

    public static Option OUTPUT = Option.builder()
            .argName("output-file")
            .option("output")
            .hasArg()
            .required(true)
            .desc("The name of the output file")
            .build();

    public static void main(String[] args) throws org.apache.commons.cli.ParseException {
        Options options = new Options();
        options.addOption(INPUT);
        options.addOption(FORMAT);
        options.addOption(OUTPUT);
        // create the parser
        CommandLineParser parser = new DefaultParser();
        // parse the command line arguments
        CommandLine line = parser.parse(options, args);
        String input = line.getParsedOptionValue(INPUT);
        String format = line.getParsedOptionValue(FORMAT);
        String outputFile = line.getParsedOptionValue(OUTPUT);

        File projectPom = new File(input);

        if (!projectPom.exists()) {
            logger.error(String.format("Invalid Maven project path: %s", input));
            return;
        }
        Graph<Node, DefaultEdge> dependencyTree = extractDependencyTree(projectPom.getParentFile());
        Map<String, Integer> duplicateNodes = findDuplicates(dependencyTree);
        // generate colors
        Map<String, ColorTracker> generateColors = ColorGenerator.generateColors(duplicateNodes);
        exportToMermaid(dependencyTree, generateColors, Path.of(outputFile + ".mermaid"));
    }

    protected static Map<String, Integer> findDuplicates(Graph<Node, DefaultEdge> dependencyTree) {
        List<Node> visitedNodes = new ArrayList<>();
        Map<String, Integer> duplicateDeps = new HashMap<>();
        for (Node node : dependencyTree.vertexSet()) {
            for (Node visitedNode : visitedNodes) {
                // TODO: have to decide if we are adding the classifier
                if (visitedNode.getGroupId().equals(node.getGroupId()) && visitedNode.getArtifactId().equals(node.getArtifactId())) {
                    duplicateDeps.put(String.format("%s:%s", node.getGroupId(), node.getArtifactId()), duplicateDeps.getOrDefault(String.format("%s:%s", node.getGroupId(), node.getArtifactId()), 1) + 1);
                    break;
                }
            }
            visitedNodes.add(node);
        }
        return duplicateDeps;
    }

    /**
     * Generate the mermaid file for the passed dependency tree at the given path location
     *
     * @param dependencyTree
     * @param generateColors
     * @param file
     */
    public static void exportToMermaid(Graph<Node, DefaultEdge> dependencyTree, Map<String, ColorTracker> generateColors, Path file) {
        String newLine = System.lineSeparator();
        StringBuilder mermaid = new StringBuilder("graph  LR;" + newLine);
        BreadthFirstIterator<Node, DefaultEdge> iterator = new BreadthFirstIterator<>(dependencyTree);
        Set<DefaultEdge> visitedEdges = new HashSet<>();
        while (iterator.hasNext()) {
            Node node = iterator.next();
            // Iterate through edges connected to this vertex
            for (DefaultEdge edge : dependencyTree.edgesOf(node)) {
                if (!visitedEdges.contains(edge)) {
                    visitedEdges.add(edge);
                    mermaid
                            .append("\t")
                            .append(formatDepName(dependencyTree.getEdgeSource(edge), generateColors))
                            .append(" --> ").append(formatDepName(dependencyTree.getEdgeTarget(edge), generateColors))
                            .append(newLine);
                }
            }
        }
        generateColors.forEach((dep, colors) -> colors.getGeneratedColors().forEach(color -> mermaid.append("style " + color + " fill:" + color + newLine)));
        try {
            Files.write(file, mermaid.toString().getBytes());
        } catch (IOException e) {
            logger.error(String.format("failed to create the dependency graph file %s", file));
        }
    }

    private static String formatDepName(Node node, Map<String, ColorTracker> generateColors) {
        String depName = String.format("%s:%s", node.getGroupId(), node.getArtifactId());
        if (generateColors.containsKey(depName)) {
            // assign color to node and update color tracker
            ColorTracker colorTracker = generateColors.get(depName);
            String colorAssigned = colorTracker.getAssignedNodes().getOrDefault(node, null);

            if (colorAssigned == null) {
                colorAssigned = colorTracker.getGeneratedColors().get(colorTracker.getIndexAssigned());
                colorTracker.setIndexAssigned(colorTracker.getIndexAssigned() + 1);
                colorTracker.addNode(node, colorAssigned);
            }
            return String.format("%s(L%s-%s-%s)", colorAssigned, node.getDepLevel(), depName, node.getVersion());
        }
        return String.format("L%s-%s:%s-%s", node.getDepLevel(), node.getGroupId(), node.getArtifactId(), node.getVersion());
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
        Parser parser = new TextParser();
        try {
            return parser.parse(r);
        } catch (ParseException e) {
            logger.warn(String.format("Failed to parse the dependency tree file: %s", depTreeFile));
        }
        throw new RuntimeException();
    }

}
