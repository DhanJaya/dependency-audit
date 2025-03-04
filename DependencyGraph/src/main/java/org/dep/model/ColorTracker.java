package org.dep.model;

import java.util.List;

public class ColorTracker {

    private List<String> generatedColors;
    private int indexAssigned;

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
}
