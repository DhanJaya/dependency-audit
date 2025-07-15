package org.dep.analyzer;

import com.opencsv.CSVWriter;
import fr.dutra.tools.maven.deptree.core.Node;
import fr.dutra.tools.maven.deptree.core.ParseException;
import fr.dutra.tools.maven.deptree.core.Parser;
import fr.dutra.tools.maven.deptree.core.TextParser;
import javassist.NotFoundException;
import javassist.bytecode.BadBytecode;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.dep.model.ColorStyleTracker;
import org.dep.model.Reference;
import org.dep.util.ColorGenerator;
import org.dep.util.CommandExecutor;

import java.io.IOException;

import java.io.File;
import java.io.StringWriter;
import java.io.Reader;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.util.*;
import java.util.List;
import java.util.function.Function;

import org.dep.util.HTMLReport;
import org.dep.util.MermaidFileGenerator;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.nio.graphml.GraphMLExporter;
import org.jgrapht.traverse.BreadthFirstIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class GraphAnalyzer {
    public static final String DEPENDENCY_TREE_FILE = "DepTree.txt";
    private static final Logger logger = LoggerFactory.getLogger(GraphAnalyzer.class);
    // The project name under analysis
    public static String PROJECT_NAME = null;
    // The folder in which all reports will be generated under
    public static String REPORT_FOLDER = null;
    public static Option INPUT = Option.builder()
            .argName("aUrlOrPOMFile")
            .option("input")
            .hasArg()
            .required(true)
            .desc("Provides the project pom.xml file path")
            .build();

    public static Option TEST_DEP = Option.builder()
            .argName("exclude-test-scope")
            .option("excludetestscope")
            .hasArg()
            .required(false)
            .desc("Graph should exclude test scope dependencies by default false")
            .build();

    public static Option TRANSITIVE_FUNC = Option.builder()
            .argName("transitive-func")
            .option("transitivefunctions")
            .hasArg()
            .required(false)
            .desc("Display used transitive functions in the graph by default false")
            .build();

    // To execute maven commands on the Windows OS need to update this to mvn.cmd
    public static String MAVEN_CMD = "mvn";

    public GraphAnalyzer() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            MAVEN_CMD = "mvn.cmd"; // Windows command
        }
    }

    public static void main(String[] args) throws NotFoundException, IOException, BadBytecode, URISyntaxException {
        GraphAnalyzer graphAnalyzer = new GraphAnalyzer();

        Options options = new Options();
        options.addOption(INPUT);
        options.addOption(TEST_DEP);
        options.addOption(TRANSITIVE_FUNC);

        // create the parser
        CommandLineParser parser = new DefaultParser();
        // parse the command line arguments
        CommandLine line;
        String input;
        boolean excludeTestScope;
        boolean showTransitiveFunc;
        try {
            line = parser.parse(options, args);
            input = line.getParsedOptionValue(INPUT);
            excludeTestScope = Boolean.parseBoolean(line.getParsedOptionValue(TEST_DEP));
            showTransitiveFunc = Boolean.parseBoolean(line.getParsedOptionValue(TRANSITIVE_FUNC));
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
        graphAnalyzer.analyze(excludeTestScope, showTransitiveFunc, projectPom);
    }

    private void createReportFolder(String projectName, boolean excludeTestScope, boolean showTransitiveFunc) {
        StringBuilder suffix = new StringBuilder();
        if (excludeTestScope) {
            suffix.append("NoTest");
        }
        if (showTransitiveFunc) {
            suffix.append("WithTrans");
        }
        REPORT_FOLDER = projectName.replace(":", "_") + suffix;
    }

    public void analyze(boolean excludeTestScope, boolean showTransitiveFunc, File projectPom) throws IOException, NotFoundException, BadBytecode, URISyntaxException {
        Graph<Node, DefaultEdge> dependencyTree = extractDependencyTree(projectPom.getParentFile());

        BreadthFirstIterator<Node, DefaultEdge> iterator = new BreadthFirstIterator<>(dependencyTree);
        Node rootNode = iterator.next();
        PROJECT_NAME = rootNode.getGroupId() + ":" + rootNode.getArtifactId();
        createReportFolder(PROJECT_NAME, excludeTestScope, showTransitiveFunc);
        DepUsage depUsage = new DepUsage();
        Map<Node, Map<String, Set<Reference>>> mappedReferences = new HashMap<>();
        Map<String, Set<Reference>> allUnMappedReferences = new HashMap<>();
        depUsage.extractDepUsage(dependencyTree, projectPom.getParentFile(), MAVEN_CMD, mappedReferences, allUnMappedReferences);

        // detect if functions of transitive dependencies or especially functionality form omitted dependencies are invoked
        Map<Node, Map<String, Set<Reference>>> transitiveReferences = identifyTransitiveReferences(mappedReferences);
        Map<String, Integer> duplicateNodes = findDuplicates(dependencyTree, excludeTestScope);
        // generate colors
        Map<String, ColorStyleTracker> generateColors = ColorGenerator.generateColors(duplicateNodes);
        Map<Node, String> hrefTransitiveMap = new HashMap<>();
        MermaidFileGenerator mermaidFileGenerator = new MermaidFileGenerator();
        mermaidFileGenerator.exportToMermaid(dependencyTree, generateColors, transitiveReferences, excludeTestScope, showTransitiveFunc, hrefTransitiveMap, allUnMappedReferences);

        // generate the html
        HTMLReport.generateDependencyDetailsHTML(PROJECT_NAME, dependencyTree, mappedReferences, hrefTransitiveMap, allUnMappedReferences);
        // write CSV file with all the data
        writeDataToCSV(dependencyTree, mappedReferences, allUnMappedReferences);
      //  generateXML(dependencyTree);
    }

    private static void generateXML(Graph<Node, DefaultEdge> dependencyTree) {
        // Export to GraphML
        // ID and label generators
        Function<Node, String> vertexIdProvider = node ->
                node.getGroupId() + ":" +
                        node.getArtifactId() + ":" +
                        node.getPackaging() + ":" +
                        node.getVersion() + ":" +
                        node.getDepLevel();

        Function<Node, String> vertexLabelProvider = node ->
                node.getGroupId() + node.getArtifactId() + "\\n" + node.getVersion();

        GraphMLExporter<Node, DefaultEdge> exporter = new GraphMLExporter<>(
                vertexIdProvider

        );
        exporter.setExportVertexLabels(true);
        exporter.setExportEdgeLabels(false);

        StringWriter writer = new StringWriter();
        exporter.exportGraph(dependencyTree, writer);

        System.out.println(writer);
    }

    private void writeDataToCSV(Graph<Node, DefaultEdge> dependencyTree, Map<Node, Map<String, Set<Reference>>> mappedReferences, Map<String, Set<Reference>> allUnMappedReferences) {
        BreadthFirstIterator<Node, DefaultEdge> iterator = new BreadthFirstIterator<>(dependencyTree);
        Set<DefaultEdge> visitedEdges = new HashSet<>();
        List<String[]> rows = new ArrayList<>();
        while (iterator.hasNext()) {
            Node node = iterator.next();
            // Iterate through edges connected to this vertex
            for (DefaultEdge edge : dependencyTree.edgesOf(node)) {
                if (!visitedEdges.contains(edge)) {
                    Node currentNode = dependencyTree.getEdgeTarget(edge);
                    StringBuilder referencesString = new StringBuilder();
                    if (mappedReferences.containsKey(currentNode)) {

                        Map<String, Set<Reference>> classAndReferences = mappedReferences.get(dependencyTree.getEdgeTarget(edge));
                        // format string to display on arrow
                        for (Map.Entry<String, Set<Reference>> entry : classAndReferences.entrySet()) {
                            String className = entry.getKey();
                            Set<Reference> references = entry.getValue();

                            for (Reference reference : references) {
                                if (reference.getInstruction() != null) {
                                    referencesString.append(reference.getInstruction()).append("->").append(className).append("::").append(reference.getName()).append("\n");
                                } else {
                                    referencesString.append(className).append("::").append(reference.getName()).append("\n");
                                }
                            }
                            if (references.isEmpty()) {
                                referencesString.append(className).append("\n");
                            }
                        }
                    }
                    boolean conflicts = false;
                    if (currentNode.getDescription() != null) {
                        conflicts = currentNode.getDescription().contains("conflict with");
                    }
                    rows.add(new String[]{currentNode.getDependencyName(), currentNode.getScope(), String.valueOf(currentNode.getDepLevel()), String.valueOf(currentNode.isOmitted()), String.valueOf(conflicts), referencesString.toString()});
                    visitedEdges.add(edge);
                }
            }
        }

        try (CSVWriter writer = new CSVWriter(Files.newBufferedWriter(Paths.get(REPORT_FOLDER, "DependencyDetails.csv")))) {
            String[] header = {"Dependency", "Dependency Scope", "Dependency Level", "Omitted", "Conflicts", "Invoked References"};

            writer.writeNext(header);
            writer.writeAll(rows);
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (!allUnMappedReferences.isEmpty()) {
            try (CSVWriter writer = new CSVWriter(new FileWriter(Paths.get(REPORT_FOLDER, "DependencyDetails.csv").toString(), true))) {
                // Added a space in between the two data
                writer.writeNext(new String[] {});
                String[] header = {"Unmapped References"};
                writer.writeNext(header);
                List<String[]> unMappedData = new ArrayList<>();
                allUnMappedReferences.forEach((className, references) -> {
                    if (references == null || references.isEmpty()) {
                        unMappedData.add(new String[]{className});
                    } else {
                        references.forEach(reference -> {
                            StringBuilder referenceDetails = new StringBuilder();
                            String instruction = reference.getInstruction();
                            if (instruction != null && !instruction.isEmpty()) {
                                referenceDetails.append(instruction).append("->");
                            }
                            referenceDetails.append(className).append("::").append(reference.getName());
                            unMappedData.add(new String[]{referenceDetails.toString()});
                        });
                    }
                });
                writer.writeAll(unMappedData);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    private Map<Node, Map<String, Set<Reference>>> identifyTransitiveReferences(Map<Node, Map<String, Set<Reference>>> mappedReferences) {
        Map<Node, Map<String, Set<Reference>>> transitiveReferences = new HashMap<>();
        for (Node dependency : mappedReferences.keySet()) {
            // check dependency level and omitted status
            if (dependency.getDepLevel() > 1) {
                dependency.setTranFunctionsUsed(true);
                transitiveReferences.put(dependency, mappedReferences.get(dependency));
            }
        }
        return transitiveReferences;
    }

    protected Map<String, Integer> findDuplicates(Graph<Node, DefaultEdge> dependencyTree, boolean removeTestDep) {
        List<Node> visitedNodes = new ArrayList<>();
        Map<String, Integer> duplicateDeps = new HashMap<>();
        for (Node node : dependencyTree.vertexSet()) {
            if (removeTestDep && node.getScope() != null && node.getScope().equals("test")) {
                continue;
            }
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

    protected Graph<Node, DefaultEdge> extractDependencyTree(File projectDir) {
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

    protected Graph<Node, DefaultEdge> readDependencyTree(File depTreeFile) {
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
