package org.dep.analyzer;

import fr.dutra.tools.maven.deptree.core.Node;
import javassist.NotFoundException;
import javassist.bytecode.BadBytecode;
import org.dep.model.Reference;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.traverse.BreadthFirstIterator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class DepUsageTest {

    @Test
    public void testExtractDependencyTree() throws NotFoundException, IOException, BadBytecode, URISyntaxException {
        String mvnCmd = "mvn";
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            mvnCmd = "mvn.cmd"; // Windows command
        }

        GraphAnalyzer graphAnalyzer = new GraphAnalyzer();
        URL testProject = getClass().getClassLoader().getResource("DependencyAuditTest");

        Graph<Node, DefaultEdge> dependencyTree = graphAnalyzer.extractDependencyTree(new File(testProject.getFile()));
        Map<String, Set<Reference>> allUnMappedReferences = new HashMap<>();
        DepUsage depUsage = new DepUsage();
        depUsage.extractDepUsage(dependencyTree, new File(testProject.getFile()), mvnCmd, allUnMappedReferences);

        BreadthFirstIterator<Node, DefaultEdge> iterator = new BreadthFirstIterator<>(dependencyTree);
        Node matchedNode = null;
        while (iterator.hasNext()) {
            Node dependency = iterator.next();
            if (dependency.getDependencyName().equals("org.slf4j:slf4j-api:2.0.16")) {
                matchedNode = dependency;
                break;
            }
        }
        Map<String, Set<Reference>> matchedReferencesAndClasses = matchedNode.getReferences();
        Assertions.assertEquals(2, matchedReferencesAndClasses.size());
        // detect the references used from the org.slf4j.Logger class
        Set<Reference> references = matchedReferencesAndClasses.get("org.slf4j.Logger");
        Assertions.assertTrue(references != null);
        Assertions.assertEquals(3, references.size());
        Assertions.assertTrue(references.stream().anyMatch(ref -> ref.getName().contains("debug(Ljava/lang/String;)V")));
        Assertions.assertTrue(references.stream().anyMatch(ref -> ref.getName().contains("error(Ljava/lang/String;Ljava/lang/Throwable;)V")));
    }
}
