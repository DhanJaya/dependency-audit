package org.dep.analyzer;

import fr.dutra.tools.maven.deptree.core.Node;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;

public class GraphAnalyzerTest {

    @Test
    public void testExtractDependencyTree() {
        URL testProject = getClass().getClassLoader().getResource("testproj1");

        LinkedList<Node> dependencyTree = GraphAnalyzer.extractDependencyTree(new File(testProject.getFile()));
        Assertions.assertEquals(4, dependencyTree.size(), "The direct dependency count does not match");
        Assertions.assertEquals("jgrapht-core", dependencyTree.get(3).getArtifactId(), "The 3rd dependency does not match");
        Assertions.assertEquals(2, dependencyTree.get(3).getChildNodes().size(), "The number of child nodes for the dependency does not match");
    }



    @Test
    public void testExportToMermaid() {
        Path depGraphInMermaid = Path.of("target/depGraph.mermaid");
        URL testProject = getClass().getClassLoader().getResource("dependencytree/deptree1.txt");
        LinkedList<Node> dependencyTree = GraphAnalyzer.readDependencyTree(new File(testProject.getFile()));

        GraphAnalyzer.exportToMermaid("org.test:artifact1", dependencyTree, depGraphInMermaid);
        Assertions.assertTrue(Files.exists(depGraphInMermaid));

    }
}
