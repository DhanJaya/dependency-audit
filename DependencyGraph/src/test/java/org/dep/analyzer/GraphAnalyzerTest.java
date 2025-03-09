package org.dep.analyzer;

import fr.dutra.tools.maven.deptree.core.Node;
import org.dep.model.ColorTracker;
import org.dep.util.ColorGenerator;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

public class GraphAnalyzerTest {

    @Test
    public void testExtractDependencyTree() {
        URL testProject = getClass().getClassLoader().getResource("testproj1");

        Graph<Node, DefaultEdge> dependencyTree = GraphAnalyzer.extractDependencyTree(new File(testProject.getFile()));
        Set<Node> allVertex = dependencyTree.vertexSet();
        Assertions.assertEquals(9, allVertex.size(), "The total nodes of the graph is incorrect");
        Node rootNode = allVertex.iterator().next();
        Assertions.assertEquals("artifact1", rootNode.getArtifactId(), "The project artifact name does not match");
        Assertions.assertEquals(4, dependencyTree.edgesOf(rootNode).size(), "The project has 4 direct dependencies.");
    }

    @Test
    public void testDuplicateNodes() {
        URL testProject = getClass().getClassLoader().getResource("dependencytree/deptree1.txt");
        Graph<Node, DefaultEdge> dependencyTree = GraphAnalyzer.readDependencyTree(new File(testProject.getFile()));
        Map<String, Integer> duplicateNodes = GraphAnalyzer.findDuplicates(dependencyTree);
        Assertions.assertEquals(5, duplicateNodes.size(), "The number of duplicate nodes does not match");
    }


    @Test
    public void testExportToMermaid() {
        Path depGraphInMermaid = Path.of("target/depGraph.mermaid");
        URL testProject = getClass().getClassLoader().getResource("dependencytree/deptree1.txt");
        Graph<Node, DefaultEdge> dependencyTree = GraphAnalyzer.readDependencyTree(new File(testProject.getFile()));
        Map<String, Integer> duplicateNodes = GraphAnalyzer.findDuplicates(dependencyTree);
        Map<String, ColorTracker> generateColors = ColorGenerator.generateColors(duplicateNodes);
        GraphAnalyzer.exportToMermaid(dependencyTree, generateColors, depGraphInMermaid);
        Assertions.assertTrue(Files.exists(depGraphInMermaid));

    }
}
