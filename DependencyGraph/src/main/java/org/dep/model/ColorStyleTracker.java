package org.dep.model;

import fr.dutra.tools.maven.deptree.core.Node;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ColorStyleTracker {

    private List<String> generatedColors;
    private int indexAssigned;
    private Map<Node, NodeStyle> nodeStyles = new HashMap<>();

    public ColorStyleTracker(List<String> generatedColors, int indexAssigned) {
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

    public Map<Node, NodeStyle> getNodeStyles() {
        return nodeStyles;
    }

       public void addNodeStyle(Node node, NodeStyle nodeStyle) {
        this.nodeStyles.put(node, nodeStyle);
    }

}
