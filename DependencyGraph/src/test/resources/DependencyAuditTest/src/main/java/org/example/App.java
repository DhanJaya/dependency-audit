package org.example;

import org.apfloat.Apfloat;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;
import org.jheaps.tree.FibonacciHeap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class App
{
    private static final Logger logger = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) {
        logger.info("Application started.");
        logger.debug("This is a debug message.");
        logger.error("Something went wrong!", new RuntimeException("Example exception"));
    }
    public static void useGraphLib() {
        SimpleGraph<String, DefaultEdge> graph = new SimpleGraph<>(DefaultEdge.class);

        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        graph.addEdge("A", "B");
        graph.addEdge("B", "C");

        System.out.println("Graph vertices: " + graph.vertexSet());
        System.out.println("Graph edges: " + graph.edgeSet());

        FibonacciHeap<String, Double> heap = new FibonacciHeap<>();
        heap.insert("A", 3.0);
        heap.insert("B", 1.0);
        heap.insert("C", 2.0);

        System.out.println("Min element from heap: " + heap.findMin().getValue());

        Apfloat bigCalc = new Apfloat("1.414213562373095048801688724209", 50); // sqrt(2) approx
        Apfloat result = bigCalc.multiply(new Apfloat("2.0", 50));
        System.out.println("High-precision calculation: 2 × √2 ≈ " + result.toString(true));
    }
}
