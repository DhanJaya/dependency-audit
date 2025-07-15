package org.dep.util;


import fr.dutra.tools.maven.deptree.core.Node;
import org.dep.model.Reference;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.traverse.BreadthFirstIterator;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;


import static org.dep.analyzer.GraphAnalyzer.REPORT_FOLDER;

public class HTMLReport {

    private static final Logger logger = LoggerFactory.getLogger(HTMLReport.class);

    // Name of the HTML file created with dependency details
    public static final String DEP_DETAILS_HTML = "DependencyDetails.html";

    //Name of the HTML file created with the dependency graph
    public static final String GRAPH_HTML = "Graph.html";

    public static void generateMermaidGraphHTML(String mermaidGraph, String rootName) throws IOException {
        Document doc = Document.createShell("");

        doc.head().appendElement("title").text("Dependency Graph");
        // Add script to <head>
        Element head = doc.head();
        head.appendElement("script")
                .attr("type", "module")
                .append("import mermaid from 'https://cdn.jsdelivr.net/npm/mermaid@10/dist/mermaid.esm.min.mjs';\n" +
                        "mermaid.initialize({ startOnLoad: true });");
        // Add content to <body>
        Element body = doc.body();
        body.appendElement("h2").text(rootName);
        Element divTag = body.appendElement("div");
        divTag.appendElement("pre").addClass("mermaid").text(mermaidGraph);
        File outputFile = new File(REPORT_FOLDER, GRAPH_HTML);
        Helper.createFolderIfNotExists(REPORT_FOLDER);
        Files.write(outputFile.toPath(), doc.outerHtml().getBytes());
        logger.info("Output File: " + outputFile.getAbsolutePath());
    }

    public static void generateDependencyDetailsHTML(String projectName, Graph<Node, DefaultEdge> dependencyTree, Map<Node, Map<String, Set<Reference>>> mappedReferences, Map<Node, String> hrefTransitiveMap, Map<String, Set<Reference>> allUnMappedReferences) throws IOException {
        Document doc = Document.createShell("");

        doc.head().appendElement("title").text("Dependency Report");

        // Add embedded CSS
        doc.head().appendElement("style").attr("type", "text/css").text(
                "table { border-collapse: collapse; width: 100%; }" +
                        "th, td { border: 1px solid black; padding: 8px; }" +
                        "th { background-color: #f2f2f2; }"
        );

        doc.body().appendElement("h1").text("Dependency resolution of " + projectName);

        // Example table
        Element table = doc.body().appendElement("table");

        appendTableHeader(table);

        appendTableBody(dependencyTree, mappedReferences, hrefTransitiveMap, table);

        addUnMappedReferences(projectName, allUnMappedReferences, doc);

        File outputFile = new File(REPORT_FOLDER, DEP_DETAILS_HTML);
        Files.write(outputFile.toPath(), doc.outerHtml().getBytes());
    }

    private static void addUnMappedReferences(String projectName, Map<String, Set<Reference>> allUnMappedReferences, Document doc) {
        if (!allUnMappedReferences.isEmpty()) {
            // include the unmapped references in the HTML
            doc.body().appendElement("h2").text("Unmapped references to the resolved dependencies of  " + projectName);
            // Create an unordered list element
            Element ul = new Element("ul");
            for (Map.Entry<String, Set<Reference>> entry : allUnMappedReferences.entrySet()) {
                String className = entry.getKey();
                Set<Reference> references = entry.getValue();
                if (references.isEmpty()) {
                    ul.appendElement("li").text(className);
                } else {
                    references.forEach(ref -> {
                        ul.appendElement("li").text(className + "::" + ref.getName());
                    });
                }
            }
            // Insert the list into the body (or any specific element)
            doc.body().appendChild(ul);
        }
    }

    private static void appendTableBody(Graph<Node, DefaultEdge> dependencyTree, Map<Node, Map<String, Set<Reference>>> mappedReferences, Map<Node, String> hrefTransitiveMap, Element table) {
        BreadthFirstIterator<Node, DefaultEdge> iterator = new BreadthFirstIterator<>(dependencyTree);
        Set<DefaultEdge> visitedEdges = new HashSet<>();
        while (iterator.hasNext()) {
            Node node = iterator.next();
            // Iterate through edges connected to this vertex
            for (DefaultEdge edge : dependencyTree.edgesOf(node)) {
                if (!visitedEdges.contains(edge)) {
                    Node currentNode = dependencyTree.getEdgeTarget(edge);
                    StringBuilder referencesString = new StringBuilder();
                    if (mappedReferences.containsKey(currentNode)) {

                        Map<String, Set<Reference>> classAndReferences = mappedReferences.get(dependencyTree.getEdgeTarget(edge));
                        for (Map.Entry<String, Set<Reference>> entry : classAndReferences.entrySet()) {
                            String className = entry.getKey();
                            Set<Reference> references = entry.getValue();

                            for (Reference reference : references) {
                                if (reference.getInstruction() != null) {
                                    referencesString.append(reference.getInstruction()).append(" -> ").append(className).append("::").append(reference.getName()).append("<br>");
                                } else {
                                    referencesString.append(className).append("::").append(reference.getName()).append("<br>");
                                }
                            }
                            if (references.isEmpty()) {
                                referencesString.append(className).append("<br>");
                            }
                        }
                    }
                    visitedEdges.add(edge);
                    Element row;
                    if (hrefTransitiveMap.containsKey(currentNode)) {
                        row = table.appendElement("tr id = \"" + hrefTransitiveMap.get(currentNode) + "\"");
                    } else {
                        row = table.appendElement("tr");

                    }

                    row.appendElement("td").text(currentNode.getDependencyName());
                    row.appendElement("td").text(currentNode.getScope());
                    row.appendElement("td").text(String.valueOf(currentNode.getDepLevel()));
                    row.appendElement("td").text(String.valueOf(currentNode.isOmitted()));
                    if (node.getDescription() != null) {
                        row.appendElement("td").text(String.valueOf(node.getDescription().contains("conflict with")));
                    } else {
                        row.appendElement("td").text("");
                    }
                    if (hrefTransitiveMap.containsKey(currentNode)) {
                        //L1-org.apache.velocity:velocity-1.6.4 --.text <a href='http://google.com'>link</a>.--> - for the hyperlink
                        Element td = row.appendElement("td");
                        td.appendElement("a")
                                .attr("href", hrefTransitiveMap.get(currentNode))
                                .attr("style", "display: block; text-decoration: none; color: inherit;")
                                .html(referencesString.toString());
                    } else {
                        row.appendElement("td").html(referencesString.toString());
                    }
                }
            }
        }
    }

    private static void appendTableHeader(Element table) {
        Element headerRow = table.appendElement("tr");
        headerRow.appendElement("th").text("Dependency");
        headerRow.appendElement("th").text("Scope");
        headerRow.appendElement("th").text("Dependency Level");
        headerRow.appendElement("th").text("Is Omitted");
        headerRow.appendElement("th").text("Is Conflicting");
        headerRow.appendElement("th").text("Invoked References");
    }
}
