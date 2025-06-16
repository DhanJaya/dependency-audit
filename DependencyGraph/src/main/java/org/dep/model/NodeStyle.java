package org.dep.model;

import fr.dutra.tools.maven.deptree.core.Node;

public class NodeStyle {
    private String nodeAlias;
    private Node node;
    private String color;
    private String icon;
    private String style;

    public String getNodeAlias() {
        return nodeAlias;
    }

    public Node getNode() {
        return node;
    }

    public void setNode(Node node) {
        this.node = node;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public void setNodeAlias(String nodeAlias) {
        this.nodeAlias = nodeAlias;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getStyle() {
        return style;
    }

    public void setStyle(String style) {
        this.style = style;
    }
}
