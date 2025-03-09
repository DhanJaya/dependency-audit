package org.dep.model;

import fr.dutra.tools.maven.deptree.core.Node;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ColorTracker {

    private List<String> generatedColors;
    private int indexAssigned;

    private Map<Node, String> assignedNodeColor = new HashMap<>();

    public ColorTracker(List<String> generatedColors, int indexAssigned) {
        this.generatedColors = generatedColors;
        this.indexAssigned = indexAssigned;
    }
    public List<String> getGeneratedColors() {
        return generatedColors;
    }

    public void setGeneratedColors(List<String> generatedColors) {
        this.generatedColors = generatedColors;
    }

    public int getIndexAssigned() {
        return indexAssigned;
    }

    public void setIndexAssigned(int indexAssigned) {
        this.indexAssigned = indexAssigned;
    }

    public void addNode(Node node, String color) {
        this.assignedNodeColor.put(node, color);
    }

    public Map<Node, String> getAssignedNodes() {
        return assignedNodeColor;
    }
}
