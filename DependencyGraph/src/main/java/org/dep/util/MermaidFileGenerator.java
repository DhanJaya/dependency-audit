package org.dep.util;

import fr.dutra.tools.maven.deptree.core.Node;
import org.apache.poi.ss.util.CellReference;
import org.dep.model.ColorStyleTracker;
import org.dep.model.NodeStyle;
import org.dep.model.Reference;
import org.jgrapht.Graph;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.shortestpath.AllDirectedPaths;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.traverse.BreadthFirstIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Collectors;

public class MermaidFileGenerator {

    private static final Logger logger = LoggerFactory.getLogger(MermaidFileGenerator.class);

    /**
     * Generate the mermaid file for the passed dependency tree at the given path location
     *
     * @param dependencyTree
     * @param generateColors
     */
    public void exportToMermaid(Graph<Node, DefaultEdge> dependencyTree, Map<String, ColorStyleTracker> generateColors, boolean removeTestDep, boolean showTransitiveFunc, Map<Node, String> hrefTransitiveMap, Map<String, Set<Reference>> allUnMappedReferences) throws IOException {
        String newLine = System.lineSeparator();
        StringBuilder mermaid = new StringBuilder("graph  LR;" + newLine);
        addLegendToGraph(mermaid, newLine);
        Map<Node, String> nodeAliasMap = new HashMap<>();
        int index = 0;
        Node rootNode = null;
        String rootNodeName = null;
        boolean isRootNode = false;
        // Assign aliases and declare node labels
        for (Node node : dependencyTree.vertexSet()) {
            if (dependencyTree.inDegreeOf(node) == 0) {
                rootNode = node;
                rootNodeName = node.getGroupId() + ":" + node.getArtifactId();
                isRootNode = true;
            }
            if (removeTestDep && node.getScope() != null && node.getScope().equals("test")) {
                continue;
            }
            String alias = CellReference.convertNumToColString(index);
            nodeAliasMap.put(node, alias);
            mermaid.append(alias)
                    .append(formatDepName(node, generateColors, nodeAliasMap, isRootNode))
                    .append(newLine);
            index++;
            isRootNode = false;
        }
        BreadthFirstIterator<Node, DefaultEdge> iterator = new BreadthFirstIterator<>(dependencyTree);
        Set<DefaultEdge> visitedEdges = new HashSet<>();
        List<Integer> linkNumbers = new ArrayList<>();
        // The edge is starting at 3 since edges were used to display the legend
        int counterForEdges = 3;
        // keep track of the initial node to link transitive dependencies

        while (iterator.hasNext()) {
            Node node = iterator.next();
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
                    // Need to handle scope updated from test when test scope is omitted
                    if (srcAlias == null) {
                        AllDirectedPaths<Node, DefaultEdge> allPaths =
                                new AllDirectedPaths<>(dependencyTree);
                        List<GraphPath<Node, DefaultEdge>> paths =
                                allPaths.getAllPaths(rootNode, target, true, null);
                        // add the node connections to the graph and alias map, need to check if its only one path available. then get the first name
                        for (GraphPath<Node, DefaultEdge> graphPath : paths) {
                            for (DefaultEdge defaultEdge : graphPath.getEdgeList()) {
                                if (visitedEdges.contains(defaultEdge)) {
                                    continue;
                                }
                                visitedEdges.add(defaultEdge);
                                Node parentNode = dependencyTree.getEdgeSource(defaultEdge);
                                Node childNode = dependencyTree.getEdgeTarget(defaultEdge);
                                if (!nodeAliasMap.containsKey(parentNode)) {
                                    String parentAlias = CellReference.convertNumToColString(index);
                                    nodeAliasMap.put(parentNode, parentAlias);
                                    mermaid.append(parentAlias)
                                            .append(formatDepName(node, generateColors, nodeAliasMap, isRootNode))
                                            .append(newLine);
                                    index++;
                                }
                                if (!nodeAliasMap.containsKey(childNode)) {
                                    String childAlias = CellReference.convertNumToColString(index);
                                    nodeAliasMap.put(childNode, childAlias);
                                    mermaid.append(childAlias)
                                            .append(formatDepName(node, generateColors, nodeAliasMap, isRootNode))
                                            .append(newLine);
                                    index++;
                                }
                                // Add the node links to the graph except last node which will be added in the next step
                                if (!target.equals(childNode)) {
                                    mermaid.append("\t").append(nodeAliasMap.get(parentNode));
                                    if (childNode.getScope() != null && "test".equals(childNode.getScope())) {
                                        mermaid.append(" -. test .-> ");
                                    } else {
                                        mermaid.append(" --> ");
                                    }
                                    mermaid.append(nodeAliasMap.get(childNode)).append(newLine);
                                    ++counterForEdges;
                                }
                            }
                        }
                        srcAlias = nodeAliasMap.get(source);
                    }


                    mermaid.append("\t").append(srcAlias);

                    if (target.getScope() != null && "test".equals(target.getScope())) {
                        mermaid.append(" -. test .-> ");
                    } else {
                        mermaid.append(" --> ");
                    }
                    mermaid.append(tgtAlias).append(newLine);

                    ++counterForEdges;
                    if (target.getDepLevel() > 1 && !target.getReferences().isEmpty()) {
                        includeTransitiveReference(rootNode, target, mermaid, showTransitiveFunc, nodeAliasMap, hrefTransitiveMap);
                        linkNumbers.add(counterForEdges);
                        ++counterForEdges;
                    }
                }
            }
        }
        // append link style to the graph
        appendColorsToGraph(generateColors, newLine, mermaid);
        appendTransitiveLinkColor(linkNumbers, mermaid);

        addUnMappedReferencesToGraph(allUnMappedReferences, newLine, mermaid, index);

        // generate HTML file
        HTMLReport.generateMermaidGraphHTML(mermaid.toString(), rootNodeName);
      //  Files.write(Path.of("TransitiveFunctions.mermaid"), mermaid.toString().getBytes()); //- to write to a .mermaid file
    }

    private static void addUnMappedReferencesToGraph(Map<String, Set<Reference>> allUnMappedReferences, String newLine, StringBuilder mermaid, int index) {
        // Append unmapped referenced to the graph
        if (!allUnMappedReferences.isEmpty()) {
            // create index for unmapped references
            String unmappedAlias = CellReference.convertNumToColString(index);
            mermaid.append(newLine).append(unmappedAlias).append("[\"`**Unmapped References**").append(newLine);
            allUnMappedReferences.forEach((className, references) -> {
                if (references == null || references.isEmpty()) {
                    mermaid.append(className).append(newLine);
                } else {
                    references.forEach(reference -> {
                        String instruction = reference.getInstruction();
                        if (instruction != null && !instruction.isEmpty()) {
                            mermaid.append(instruction).append("->");
                        }
                        mermaid.append(className).append("::").append(reference.getName()).append(newLine);
                    });
                }
            });
            mermaid.append("`\"]").append(newLine).append("style ").append(unmappedAlias).append(" fill:none,stroke-width:5px,stroke: grey");
        }
    }

    private void addLegendToGraph(StringBuilder graph, String lineSeparator) {
        graph.append("subgraph Legend").append(lineSeparator)
                .append("direction LR").append(lineSeparator)
                .append("L0{{✔️ Resolved Dependency}}").append(lineSeparator)
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

    private String formatDepName(Node node, Map<String, ColorStyleTracker> generateColors, Map<Node, String> nodeAliasMap, boolean isRootNode) {
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
                    prefix = "✔️";
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
                        prefix = "❌";
                        nodeStyle.setStyle("stroke:#ed1b0c,stroke-width:10px,stroke-dasharray: 5 5");
                        nodeStyle.setIcon(prefix);
                    }
                }
                colorStyleTracker.addNodeStyle(node, nodeStyle);
            }
            if (prefix.isEmpty()) {
                return String.format("(%s &lt;&lt;Level %s&gt;&gt; \\n %s-%s)", prefix, node.getDepLevel(), depName, node.getVersion());
            } else {
                return String.format("{{\"%s &lt;&lt;Level %s&gt;&gt; \\n %s-%s\"}}", prefix, node.getDepLevel(), depName, node.getVersion());
            }
        }
        if (isRootNode) {
            return String.format("(%s:%s-%s)", node.getGroupId(), node.getArtifactId(), node.getVersion());
        } else {
            return String.format("(&lt;&lt;Level %s&gt;&gt; \\n %s:%s-%s)", node.getDepLevel(), node.getGroupId(), node.getArtifactId(), node.getVersion());
        }
    }

    private void appendTransitiveLinkColor(List<Integer> linkNumbers, StringBuilder mermaid) {
        if (!linkNumbers.isEmpty()) {
            String linkNum = linkNumbers.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));
            mermaid.append("linkStyle ").append(linkNum).append(" stroke:red");
        }
    }

    private void includeTransitiveReference(Node rootNode, Node transitiveDep, StringBuilder mermaid, boolean showTransitiveFunc, Map<Node, String> nodeAliasMap, Map<Node, String> hrefTransitiveMap) {
        String newLine = System.lineSeparator();
        // get the references used and display it on the node edge
        String srcAlias = nodeAliasMap.get(rootNode);
        String tgtAlias = nodeAliasMap.get(transitiveDep);
        mermaid
                .append("\t")
                .append(srcAlias);

        if (showTransitiveFunc) {
            StringBuilder referencesString = new StringBuilder();

            Map<String, Set<Reference>> transitiveReferences = transitiveDep.getReferences();
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
                                .append(reference.getName())
                                .append(newLine);
                    } else {
                        String href = transitiveDep.getGroupId() + transitiveDep.getArtifactId();
                        referencesString.append(" ... <a href='")
                                .append(HTMLReport.DEP_DETAILS_HTML)
                                .append("#")
                                .append(href)
                                .append("'>more</a>");
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
            mermaid.append("-- \"")
                    .append(referencesString)
                    .append("\" --> ")
                    .append(tgtAlias)
                    .append(newLine);
        } else {
            mermaid.append(" --> ")
                    .append(tgtAlias)
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
