package org.dep.analyzer;

import org.dep.model.ColorTracker;
import org.dep.model.DependencyModel;
import org.dep.util.CommandExecutor;

import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

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
        System.out.println(Color.red.getRGB());
        String hex = String.format("#%02x%02x%02x", Color.red.getRed(), Color.red.getGreen(), Color.red.getBlue());
        // change the hue to generate different base colors
        int rgb = Color.HSBtoRGB(0.0f, 1.0f, 0.52f);

        System.out.println(rgb);



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

        Graph<Node, DefaultEdge> dependencyTree = extractDependencyTree(projectDir);
        Map<String, Integer> duplicateNodes = findDuplicates(dependencyTree);
        // generate colors
        Map<String, ColorTracker> generateColors = generateColors(duplicateNodes);
        exportToMermaid(dependencyTree, generateColors, Path.of("testingGraph3" + ".mermaid"));

    }

    protected static Map<String, ColorTracker> generateColors(Map<String, Integer> duplicateNodes) {



        Map<String, ColorTracker> generateColors = new HashMap<>();
        AtomicReference<Color> baseColor = new AtomicReference<>(new Color(100, 150, 200));
        System.out.println(baseColor.get());
        duplicateNodes.forEach((key, value) -> {
            List<String> colors = colorGenerator(value, baseColor.get());
            ColorTracker colorTracker = new ColorTracker(colors, 0);
            generateColors.put(key, colorTracker);
            System.out.println(getComplementaryColor(baseColor.get()));
            baseColor.set(getComplementaryColor(baseColor.get()));
        });
        return generateColors;
    }

    // Generate complementary color by rotating hue by 180 degrees
    public static Color getComplementaryColor(Color color) {
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        float complementaryHue = (hsb[0] + 0.5f) % 1.0f; // Rotate hue by 180 degrees
        return Color.getHSBColor(complementaryHue, hsb[1], hsb[2]);
    }

    // Generate lighter shades using JFreeChart's ColorUtilities
    public static Color getLighterShade(Color color, float brightnessFactor) {
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        float newBrightness = Math.min(hsb[2] + brightnessFactor, 1.0f); // Increase brightness
        return Color.getHSBColor(hsb[0], hsb[1], newBrightness);
    }


    public static List<String> colorGenerator(int numberOfShades, Color baseColor) {
        List<String> generatedColors = new ArrayList<>();
        // Generate lighter shades of the base color
        for (int i = 1; i <= numberOfShades; i++) {
            float factor = i * 0.1f; // Increase brightness factor
            Color lighterShade = getLighterShade(baseColor, factor);
            String hex = String.format("#%02x%02x%02x", lighterShade.getRed(), lighterShade.getGreen(), lighterShade.getBlue());
            generatedColors.add(hex);
        }
        return generatedColors;
    }


    protected static Map<String, Integer> findDuplicates(Graph<Node, DefaultEdge> dependencyTree) {
        List<Node> visitedNodes = new ArrayList<>();
        Map<String, Integer> duplicateDeps = new HashMap<>();
        for (DefaultEdge edge : dependencyTree.edgeSet()) {
            Node currentNode = dependencyTree.getEdgeTarget(edge);
            visitedNodes.forEach(visitedNode -> {
                // TODO: have to decide if we are adding the classifier
                if (visitedNode.getGroupId().equals(currentNode.getGroupId()) && visitedNode.getArtifactId().equals(currentNode.getArtifactId())) {

                    duplicateDeps.compute(String.format("%s:%s", currentNode.getGroupId(), currentNode.getArtifactId()), (dep, count) -> (count == null) ? 2 : count + 1);
                }
            });
            visitedNodes.add(currentNode);
        }
        return duplicateDeps;
    }

    public static void exportToMermaid(Graph<Node, DefaultEdge> dependencyTree, Map<String, ColorTracker> generateColors, Path file) {
        String NL = System.lineSeparator();
        StringBuilder mermaid = new StringBuilder("graph  LR;" + NL);
        //style DB fill:#00758f
        for (DefaultEdge edge : dependencyTree.edgeSet()) {
            mermaid.append("\t" + formatDepName(dependencyTree.getEdgeSource(edge), false, generateColors) + " --> " + formatDepName(dependencyTree.getEdgeTarget(edge), true, generateColors) + NL);
        }
        // append the color styles to the string
        //style DB fill:#00758f
        generateColors.forEach((dep, colors) -> {
            colors.getGeneratedColors().forEach(color -> mermaid.append("style " + color + " fill:" + color + NL));

        });
        try {
            Files.write(file, mermaid.toString().getBytes());
        } catch (IOException e) {
            logger.error(String.format("failed to create the dependency graph file %s", file.toString()));
        }
    }

    private static String formatDepName(Node node, boolean isTarget, Map<String, ColorTracker> generateColors) {
        if (isTarget) {
            String depName = String.format("%s:%s", node.getGroupId(), node.getArtifactId());
            if (generateColors.containsKey(depName)) {
                // assign color to node and update color tracker
                ColorTracker colorTracker = generateColors.get(depName);
                System.out.println(depName);
                String color = colorTracker.getGeneratedColors().get(colorTracker.getIndexAssigned());
                colorTracker.setIndexAssigned(colorTracker.getIndexAssigned() + 1);
                return String.format("%s(L%s-%s-%s)", color, node.getDepLevel(), depName, node.getVersion());

            }
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
