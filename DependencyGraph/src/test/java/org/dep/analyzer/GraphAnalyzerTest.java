package org.dep.analyzer;

import fr.dutra.tools.maven.deptree.core.Node;
import javassist.NotFoundException;
import javassist.bytecode.BadBytecode;
import org.dep.model.ColorStyleTracker;
import org.dep.util.ColorGenerator;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class GraphAnalyzerTest {

    @Test
    public void testExtractDependencyTree() {
        GraphAnalyzer graphAnalyzer = new GraphAnalyzer();
        URL testProject = getClass().getClassLoader().getResource("testproj1");

        Graph<Node, DefaultEdge> dependencyTree = graphAnalyzer.extractDependencyTree(new File(testProject.getFile()));
        Set<Node> allVertex = dependencyTree.vertexSet();
        Assertions.assertEquals(9, allVertex.size(), "The total nodes of the graph is incorrect");
        Node rootNode = allVertex.iterator().next();
        Assertions.assertEquals("artifact1", rootNode.getArtifactId(), "The project artifact name does not match");
        Assertions.assertEquals(4, dependencyTree.edgesOf(rootNode).size(), "The project has 4 direct dependencies.");
    }

    @Test
    public void testDuplicateNodes() {
        GraphAnalyzer graphAnalyzer = new GraphAnalyzer();
        URL testProject = getClass().getClassLoader().getResource("dependencytree/deptree1.txt");
        Graph<Node, DefaultEdge> dependencyTree = graphAnalyzer.readDependencyTree(new File(testProject.getFile()));
        Map<String, Integer> duplicateNodes = graphAnalyzer.findDuplicates(dependencyTree, false);
        Assertions.assertEquals(5, duplicateNodes.size(), "The number of duplicate nodes does not match");
    }


    @Test
    public void testExportToMermaid() {
        GraphAnalyzer graphAnalyzer = new GraphAnalyzer();
        Path depGraphInMermaid = Path.of("target/depGraph.mermaid");
        URL testProject = getClass().getClassLoader().getResource("dependencytree/deptree1.txt");
        Graph<Node, DefaultEdge> dependencyTree = graphAnalyzer.readDependencyTree(new File(testProject.getFile()));
        Map<String, Integer> duplicateNodes = graphAnalyzer.findDuplicates(dependencyTree, false);
        Map<String, ColorStyleTracker> generateColors = ColorGenerator.generateColors(duplicateNodes);
        graphAnalyzer.exportToMermaid(dependencyTree, generateColors, depGraphInMermaid, new HashMap<>(), false);
        Assertions.assertTrue(Files.exists(depGraphInMermaid));

    }

    @Test
    public void testExportToMermaidWithTestDependenciesRemoved() {
        GraphAnalyzer graphAnalyzer = new GraphAnalyzer();
        Path depGraphInMermaid = Path.of("target/depGraphWithOutTest.mermaid");
        URL testProject = getClass().getClassLoader().getResource("dependencytree/deptree1.txt");
        Graph<Node, DefaultEdge> dependencyTree = graphAnalyzer.readDependencyTree(new File(testProject.getFile()));
        Map<String, Integer> duplicateNodes = graphAnalyzer.findDuplicates(dependencyTree, true);
        Map<String, ColorStyleTracker> generateColors = ColorGenerator.generateColors(duplicateNodes);
        graphAnalyzer.exportToMermaid(dependencyTree, generateColors, depGraphInMermaid, new HashMap<>(), true);
        Assertions.assertTrue(Files.exists(depGraphInMermaid));

    }

    @Test
    public void testGraphWithTransitiveUsage() throws NotFoundException, IOException, BadBytecode {
        GraphAnalyzer graphAnalyzer = new GraphAnalyzer();
        Path depGraphInMermaid = Path.of("target/depGraphWithTransitiveUsage.mermaid");
        URL testProject = getClass().getClassLoader().getResource("DependencyAuditTest/pom.xml");
        File projectPom = new File(testProject.getFile());
        graphAnalyzer.analyze("target/depGraphWithTransitiveUsage", false, projectPom);

        Assertions.assertTrue(Files.exists(depGraphInMermaid));
    }
}
