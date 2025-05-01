package org.dep.analyzer;

import fr.dutra.tools.maven.deptree.core.Node;
import fr.dutra.tools.maven.deptree.core.ParseException;
import fr.dutra.tools.maven.deptree.core.Parser;
import fr.dutra.tools.maven.deptree.core.TextParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.dep.model.ColorStyleTracker;
import org.dep.model.NodeStyle;
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


public class GraphAnalyzer{
    public static final String DEPENDENCY_TREE_FILE = "DepTree.txt";
    private static final Logger logger = LoggerFactory.getLogger(GraphAnalyzer.class);
    public static Option INPUT = Option.builder()
            .argName("aUrlOrPOMFile")
            .option("input")
            .hasArg()
            .required(true)
            .desc("Provides the project pom.xml file path")
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

    // To execute maven commands on the Windows OS need to update this to mvn.cmd
    public static String MAVEN_CMD = "mvn";

    public static void main(String[] args) {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            MAVEN_CMD = "mvn.cmd"; // Windows command
        }
        Options options = new Options();
        options.addOption(INPUT);
        options.addOption(FORMAT);
        options.addOption(OUTPUT);
        // create the parser
        CommandLineParser parser = new DefaultParser();
        // parse the command line arguments
        CommandLine line;
        String input;
        String format;
        String outputFile;
        String excludeScope;
        try {
            line = parser.parse(options, args);
            input = line.getParsedOptionValue(INPUT);
            format = line.getParsedOptionValue(FORMAT);
            outputFile = line.getParsedOptionValue(OUTPUT);
        } catch (org.apache.commons.cli.ParseException e) {
            HelpFormatter helpFormatter = new HelpFormatter();
            helpFormatter.printHelp("java -cp <path-to-build-jar>" + GraphAnalyzer.class.getName(), options);
            throw new RuntimeException(e);
        }

        File projectPom = new File(input);

        if (!projectPom.exists()) {
            logger.error(String.format("Invalid Maven project path: %s", input));
            return;
        }
        Graph<Node, DefaultEdge> dependencyTree = extractDependencyTree(projectPom.getParentFile());
        Map<String, Integer> duplicateNodes = findDuplicates(dependencyTree);
        // generate colors
        Map<String, ColorStyleTracker> generateColors = ColorGenerator.generateColors(duplicateNodes);
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
    public static void exportToMermaid(Graph<Node, DefaultEdge> dependencyTree, Map<String, ColorStyleTracker> generateColors, Path file) {
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
                            .append(formatDepName(dependencyTree.getEdgeSource(edge), generateColors));
                    if (dependencyTree.getEdgeTarget(edge).getScope() != null && dependencyTree.getEdgeTarget(edge).getScope().equals("test")) {
                        mermaid.append(" -. test .-> ");
                    } else {
                        mermaid.append(" --> ");
                    }
                    mermaid.append(formatDepName(dependencyTree.getEdgeTarget(edge), generateColors))
                            .append(newLine);
                }
            }
        }
        // generateColors.forEach((dep, colors) -> colors.getGeneratedColors().forEach(color -> mermaid.append("style " + color + " fill:" + color + newLine)));
        generateColors.forEach((dep, colors) -> {
            List<String> generatedColors = colors.getGeneratedColors();
            Map<Integer, NodeStyle> nodeStyles = colors.getNodeStyle(); // HashMap<Integer, String>

            for (int i = 0; i < generatedColors.size(); i++) {
                String color = generatedColors.get(i);
                StringBuilder styleString = new StringBuilder("style " + color + " fill:" + color);

                // Append node style if the index exists in the map
                if (nodeStyles.containsKey(i)) {
                    styleString.append(", ").append(nodeStyles.get(i).getStyle());
                }
                mermaid.append(styleString).append(newLine);
            }
        });
        try {
            Files.write(file, mermaid.toString().getBytes());
        } catch (IOException e) {
            logger.error(String.format("failed to create the dependency graph file %s", file));
        }
    }

    private static String formatDepName(Node node, Map<String, ColorStyleTracker> generateColors) {
        String depName = String.format("%s:%s", node.getGroupId(), node.getArtifactId());

        if (generateColors.containsKey(depName)) {
            String prefix = "";
            // assign color to node and update color tracker
            ColorStyleTracker colorStyleTracker = generateColors.get(depName);
            String colorAssigned = colorStyleTracker.getAssignedNodes().getOrDefault(node, null);
            int index = colorStyleTracker.getGeneratedColors().indexOf(colorAssigned);
            if (colorStyleTracker.getNodeStyle().containsKey(index) && colorStyleTracker.getNodeStyle().get(index).getIcon() != null) {
                prefix = colorStyleTracker.getNodeStyle().get(index).getIcon();
            }
            if (colorAssigned == null) {
                if (!node.isOmitted()) {
                    colorAssigned = colorStyleTracker.getGeneratedColors().get(0);
                    if (colorStyleTracker.getIndexAssigned() == 0) {
                        colorStyleTracker.setIndexAssigned(colorStyleTracker.getIndexAssigned() + 1);
                    }
                    prefix = "✔";
                    NodeStyle nodeStyle = colorStyleTracker.getNodeStyle().getOrDefault(0, new NodeStyle());
                    nodeStyle.setStyle("stroke-width:10px,stroke-dasharray: 5 5"); //stroke:#10ed0c,
                    nodeStyle.setIcon(prefix);
                    colorStyleTracker.addNodeStyle(0, nodeStyle);
                } else {

                    int currentIndex;
                    if (colorStyleTracker.getIndexAssigned() == 0) {
                        currentIndex = 1;
                        colorAssigned = colorStyleTracker.getGeneratedColors().get(1);
                        colorStyleTracker.setIndexAssigned(colorStyleTracker.getIndexAssigned() + 2);
                    } else {
                        currentIndex = colorStyleTracker.getIndexAssigned();
                        colorAssigned = colorStyleTracker.getGeneratedColors().get(colorStyleTracker.getIndexAssigned());
                        colorStyleTracker.setIndexAssigned(colorStyleTracker.getIndexAssigned() + 1);
                    }
                    if (node.getDescription() != null && node.getDescription().contains("conflict with")) {
                        prefix = "⚔";
                        NodeStyle nodeStyle = colorStyleTracker.getNodeStyle().getOrDefault(currentIndex, new NodeStyle());
                        nodeStyle.setStyle("stroke:#ed1b0c,stroke-width:10px,stroke-dasharray: 5 5");
                        nodeStyle.setIcon(prefix);
                        colorStyleTracker.addNodeStyle(currentIndex, nodeStyle);
                    }
                }
                colorStyleTracker.addNode(node, colorAssigned);
            }
            if (prefix.isEmpty()) {
                return String.format("%s(%s L%s-%s-%s)", colorAssigned, prefix, node.getDepLevel(), depName, node.getVersion());
            } else {
                return String.format("%s{{\"`%s L%s-%s-%s`\"}}", colorAssigned, prefix, node.getDepLevel(), depName, node.getVersion());
            }

        }
        return String.format("L%s-%s:%s-%s", node.getDepLevel(), node.getGroupId(), node.getArtifactId(), node.getVersion());
    }

    protected static Graph<Node, DefaultEdge> extractDependencyTree(File projectDir) {
        try {
            if (CommandExecutor.executeCommand(String.format("%s dependency:tree -DoutputFile=%s -Dverbose", MAVEN_CMD, DEPENDENCY_TREE_FILE), projectDir).contains("BUILD SUCCESS")) {
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
