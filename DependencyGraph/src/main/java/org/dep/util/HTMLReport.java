package org.dep.util;


import fr.dutra.tools.maven.deptree.core.Node;
import org.dep.model.Reference;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.traverse.BreadthFirstIterator;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

public class HTMLReport {
    public static final String HTML_PAGE = "D:\\PhD\\workspace\\Dependency-Audit-New\\dependency-audit\\output.html";
    public static void main(String[] args) throws IOException {
        //generateHTML();
    }
    public static void generateHTML(Graph<Node, DefaultEdge> dependencyTree, Map<Node, Map<String, Set<Reference>>> mappedReferences,  Map<Node, String> hrefTransitiveMap) throws IOException {
        Document doc = Document.createShell("");

        doc.head().appendElement("title").text("Dependency Report");

        // Add embedded CSS
        doc.head().appendElement("style").attr("type", "text/css").text(
                "table { border-collapse: collapse; width: 100%; }" +
                        "th, td { border: 1px solid black; padding: 8px; }" +
                        "th { background-color: #f2f2f2; }"
        );

        doc.body().appendElement("h1").text("Dependency resolution of Deep Dep");

        // Example table
        Element table = doc.body().appendElement("table");

        Element headerRow = table.appendElement("tr");
        headerRow.appendElement("th").text("Dependency");
        headerRow.appendElement("th").text("Scope");
        headerRow.appendElement("th").text("Dependency Level");
        headerRow.appendElement("th").text("Is-Omitted");
        headerRow.appendElement("th").text("Is-Conflicting");
        headerRow.appendElement("th").text("Invoked References");

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
                                    referencesString.append(reference.getInstruction()).append(" -> ").append(className).append("::").append(reference.getName()).append("\n");
                                } else {
                                    referencesString.append(className).append("::").append(reference.getName()).append("\n");
                                }
                            }
                            if (references.isEmpty()) {
                                referencesString.append(className).append("\n");
                            }
                        }
                    }

                    rows.add(new String[]{currentNode.getDependencyName(), currentNode.getScope(), String.valueOf(currentNode.getDepLevel()), String.valueOf(currentNode.isOmitted()), referencesString.toString()});
                    visitedEdges.add(edge);

                    Element row = table.appendElement("tr");
                    row.appendElement("td").text(currentNode.getDependencyName());
                    row.appendElement("td").text(currentNode.getScope());
                    row.appendElement("td").text(String.valueOf(currentNode.getDepLevel()));
                    row.appendElement("td").text(String.valueOf(currentNode.isOmitted()));
                    row.appendElement("td").text(String.valueOf(node.getDescription().contains("conflict with")));
                    if (hrefTransitiveMap.containsKey(currentNode)) {
                        //L1-org.apache.velocity:velocity-1.6.4 --.text <a href='http://google.com'>link</a>.--> - for the hyperlink
                        Element td = row.appendElement("td");
                        td.appendElement("a")
                                .attr("href", hrefTransitiveMap.get(currentNode))
                                .attr("style", "display: block; text-decoration: none; color: inherit;")
                                .text(referencesString.toString());
                    }else {
                        row.appendElement("td").text(referencesString.toString());
                    }
                }
            }
        }

        File outputFile = new File(HTML_PAGE);
        Files.write(outputFile.toPath(), doc.outerHtml().getBytes());
    }
}
