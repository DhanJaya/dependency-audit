package org.dep.util;

import fr.dutra.tools.maven.deptree.core.Node;
import org.apache.poi.ss.util.CellReference;
import org.dep.model.ColorStyleTracker;
import org.dep.model.NodeStyle;
import org.dep.model.Reference;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.traverse.BreadthFirstIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class MermaidFileGenerator {

    private static final Logger logger = LoggerFactory.getLogger(MermaidFileGenerator.class);


    /**
     * Generate the mermaid file for the passed dependency tree at the given path location
     *
     * @param dependencyTree
     * @param generateColors
     * @param file
     */
    public void exportToMermaid(Graph<Node, DefaultEdge> dependencyTree, Map<String, ColorStyleTracker> generateColors, Path file, Map<Node, Map<String, Set<Reference>>> transitiveDepAndReferences, boolean removeTestDep, boolean showTransitiveFunc, Map<Node, String> hrefTransitiveMap, Map<String, Set<Reference>> allUnMappedReferences) throws IOException {
        String newLine = System.lineSeparator();
        StringBuilder mermaid = new StringBuilder("graph  LR;" + newLine);
        addLegendToGraph(mermaid, newLine);
        Map<Node, String> nodeAliasMap = new HashMap<>();
        int index = 0;

        // Assign aliases and declare node labels
        for (Node node : dependencyTree.vertexSet()) {
            if (removeTestDep && node.getScope() != null && node.getScope().equals("test")) {
                continue;
            }
            String alias = CellReference.convertNumToColString(index);
            nodeAliasMap.put(node, alias);
            mermaid.append(alias)
                    .append(formatDepName(node, generateColors, nodeAliasMap))
                    .append(newLine);
            index++;
        }

        BreadthFirstIterator<Node, DefaultEdge> iterator = new BreadthFirstIterator<>(dependencyTree);
        Set<DefaultEdge> visitedEdges = new HashSet<>();
        List<Integer> linkNumbers = new ArrayList<>();
        // The edge is starting at 3 since edges were used to display the legend
        int counterForEdges = 3;
        // keep track of the initial node to link transitive dependencies
        Node rootNode = null;
        String rootNodeName = null;
        while (iterator.hasNext()) {
            Node node = iterator.next();
            if (rootNode == null) {
                rootNode = node;
                rootNodeName = node.getGroupId() + ":" + node.getArtifactId();
            }
            // Iterate through edges connected to this vertex
            for (DefaultEdge edge : dependencyTree.edgesOf(node)) {
                if (!visitedEdges.contains(edge)) {
                    Node source = dependencyTree.getEdgeSource(edge);
                    Node target = dependencyTree.getEdgeTarget(edge);
                    if (removeTestDep && target.getScope() != null && target.getScope().equals("test")) {
                        continue;
                    }
                    visitedEdges.add(edge);
                    String srcAlias = nodeAliasMap.get(source);
                    String tgtAlias = nodeAliasMap.get(target);
                    mermaid.append("\t").append(srcAlias);

                    if (target.getScope() != null && "test".equals(target.getScope())) {
                        mermaid.append(" -. test .-> ");
                    } else {
                        mermaid.append(" --> ");
                    }
                    mermaid.append(tgtAlias).append(newLine);

                    ++counterForEdges;
                    if (showTransitiveFunc && transitiveDepAndReferences.containsKey(dependencyTree.getEdgeTarget(edge))) {
                        includeTransitiveReference(rootNode, dependencyTree.getEdgeTarget(edge), transitiveDepAndReferences, mermaid, showTransitiveFunc, nodeAliasMap, hrefTransitiveMap);
                        linkNumbers.add(counterForEdges);
                        ++counterForEdges;
                    }
                }
            }
        }
        // append link style to the graph
        appendColorsToGraph(generateColors, newLine, mermaid);
        appendTransitiveLinkColor(linkNumbers, mermaid);
        // generate HTML file
        HTMLReport.generateMermaidGraphHTML(mermaid.toString(), rootNodeName);
        Files.write(file, mermaid.toString().getBytes());
    }

    private void addLegendToGraph(StringBuilder graph, String lineSeparator) {
        graph.append("subgraph Legend").append(lineSeparator)
                .append("direction LR").append(lineSeparator)
                .append("L0{{✔\uFE0F Resolved Dependency}}").append(lineSeparator)
                .append("style L0 stroke-width:10px,stroke-dasharray: 5 5").append(lineSeparator)
                .append("L1{{\"`❌ Conflicts with another version`\"}}").append(lineSeparator)
                .append("style L1 stroke:#ed1b0c,stroke-width:10px,stroke-dasharray: 5 5").append(lineSeparator)
                .append("L2(Project)").append(lineSeparator)
                .append("L3(Dependency)").append(lineSeparator)
                .append("L4(Project)").append(lineSeparator)
                .append("L5(Transitive Dependency)").append(lineSeparator)
                .append("L6(Project)").append(lineSeparator)
                .append("L7(Test Scope Dependency)").append(lineSeparator)
                .append(" L2 --> L3").append(lineSeparator)
                .append(" L4 --> L5").append(lineSeparator)
                .append("L6 -.test.-> L7").append(lineSeparator)
                .append("linkStyle 1 stroke:red").append(lineSeparator)
                .append(" end").append(lineSeparator);
    }

    private String formatDepName(Node node, Map<String, ColorStyleTracker> generateColors, Map<Node, String> nodeAliasMap) {
        String depName = String.format("%s:%s", node.getGroupId(), node.getArtifactId());

        if (generateColors.containsKey(depName)) {
            String prefix = "";
            // assign color to node and update color tracker
            ColorStyleTracker colorStyleTracker = generateColors.get(depName);
            if (colorStyleTracker.getNodeStyles().containsKey(node) && colorStyleTracker.getNodeStyles().get(node).getIcon() != null) {
                prefix = colorStyleTracker.getNodeStyles().get(node).getIcon();
            } else {
                NodeStyle nodeStyle = new NodeStyle();
                nodeStyle.setNodeAlias(nodeAliasMap.get(node));
                if (!node.isOmitted()) {
                    if (colorStyleTracker.getIndexAssigned() == 0) {
                        colorStyleTracker.setIndexAssigned(colorStyleTracker.getIndexAssigned() + 1);
                    }
                    prefix = "✔️";//""✔";
                    nodeStyle.setStyle("stroke-width:10px,stroke-dasharray: 5 5"); //stroke:#10ed0c,
                    nodeStyle.setIcon(prefix);
                    nodeStyle.setColor(colorStyleTracker.getGeneratedColors().get(0));
                } else {
                    int currentIndex;
                    // This is done to set the 1st color to the winning node
                    if (colorStyleTracker.getIndexAssigned() == 0) {
                        currentIndex = 1;
                    } else {
                        currentIndex = colorStyleTracker.getIndexAssigned();
                    }
                    nodeStyle.setColor(colorStyleTracker.getGeneratedColors().get(currentIndex));
                    colorStyleTracker.setIndexAssigned(currentIndex + 1);
                    if (node.getDescription() != null && node.getDescription().contains("conflict with")) {
                        prefix = "❌";//"⚔";
                        nodeStyle.setStyle("stroke:#ed1b0c,stroke-width:10px,stroke-dasharray: 5 5");
                        nodeStyle.setIcon(prefix);
                    }
                }
                colorStyleTracker.addNodeStyle(node, nodeStyle);
            }
            if (prefix.isEmpty()) {
                return String.format("(%s L%s-%s-%s)", prefix, node.getDepLevel(), depName, node.getVersion());
            } else {
                return String.format("{{\"`%s L%s-%s-%s`\"}}", prefix, node.getDepLevel(), depName, node.getVersion());
            }
        }
        return String.format("(L%s-%s:%s-%s)", node.getDepLevel(), node.getGroupId(), node.getArtifactId(), node.getVersion());
    }

    private void appendTransitiveLinkColor(List<Integer> linkNumbers, StringBuilder mermaid) {
        if (!linkNumbers.isEmpty()) {
            String linkNum = linkNumbers.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));
            mermaid.append("linkStyle " + linkNum + " stroke:red");
        }
    }

    private void includeTransitiveReference(Node rootNode, Node transitiveDep, Map<Node, Map<String, Set<Reference>>> transitiveDepAndReferences, StringBuilder mermaid, boolean showTransitiveFunc, Map<Node, String> nodeAliasMap, Map<Node, String> hrefTransitiveMap) {
        String newLine = System.lineSeparator();
        // get the references used and display it on teh node edge
        if (showTransitiveFunc) {
            String srcAlias = nodeAliasMap.get(rootNode);
            String tgtAlias = nodeAliasMap.get(transitiveDep);
            mermaid
                    .append("\t")
                    .append(srcAlias);
            StringBuilder referencesString = new StringBuilder();
            Map<String, Set<Reference>> transitiveReferences = transitiveDepAndReferences.get(transitiveDep);
            // maximum number of dependencies being appended to the graph
            int limit = 2;
            int count = 0;
            // Only print the first 2 references used in a transitive dependency
            outterLoop:
            for (Map.Entry<String, Set<Reference>> entry : transitiveReferences.entrySet()) {
                String className = entry.getKey();
                Set<Reference> references = entry.getValue();

                for (Reference reference : references) {
                    count++;
                    if (limit >= count) {
                        String instruction = reference.getInstruction();
                        if (instruction != null && !instruction.isEmpty()) {
                            referencesString.append(instruction).append("->");
                        }
                        referencesString.append(className)
                                .append("::")
                                .append(reference.getName());
                        referencesString.append(newLine);
                    } else {
                        String href = transitiveDep.getGroupId() + transitiveDep.getArtifactId();
                        referencesString.append(" ... <a href='").append(HTMLReport.DEP_DETAILS_HTML).append("#")
                                //.append(GraphAnalyzer.projectName)
                        .append(href).append("'>more</a>");
                        hrefTransitiveMap.put(transitiveDep, href);
                        break outterLoop;
                    }
                }
                if (references.isEmpty()) {
                    count++;
                    if (limit >= count) {
                        referencesString.append(className).append(newLine);
                    }
                }
            }
            mermaid.append("-- \"" + referencesString + "\" --> ");
            mermaid.append(tgtAlias)
                    .append(newLine);
        }
    }

    private void appendColorsToGraph(Map<String, ColorStyleTracker> generateColors, String newLine, StringBuilder mermaid) {
        generateColors.forEach((dep, colors) -> {
            Map<Node, NodeStyle> nodeStyles = colors.getNodeStyles();

            for (Map.Entry<Node, NodeStyle> entry : nodeStyles.entrySet()) {
                NodeStyle nodeStyle = entry.getValue();
                StringBuilder styleString = new StringBuilder();
                styleString.append("style " + nodeStyle.getNodeAlias() + " fill:" + nodeStyle.getColor());
                if (nodeStyle.getStyle() != null) {
                    styleString.append(", ").append(nodeStyle.getStyle());
                }
                mermaid.append(styleString).append(newLine);
            }
        });
    }
}
