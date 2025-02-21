package org.dep.model;


import java.util.LinkedList;

public class DependencyModel {
    private final String groupId;
    private final String artifactId;
    private final String version;
    private final String classifier;
    private final int depLevel;
    private final boolean omitted;
    private final LinkedList<DependencyModel> childNodes = new LinkedList<DependencyModel>();

    public DependencyModel(String groupId, String artifactId, String version, String classifier, int depLevel, boolean omitted) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.classifier = classifier;
        this.depLevel = depLevel;
        this.omitted = omitted;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getVersion() {
        return version;
    }

    public String getClassifier() {
        return classifier;
    }

    public int getDepLevel() {
        return depLevel;
    }

    public boolean getOmitted() {
        return omitted;
    }

    public boolean addChildNode(final DependencyModel o) {
        return this.childNodes.add(o);
    }


    public String getDependencyName(boolean withClassifier) {
        if (withClassifier) {
            return (classifier != null) ? (this.groupId + ":" + this.artifactId + ":" + this.classifier) :
                    (this.groupId + ":" + this.artifactId);
        } else {
            return this.groupId + ":" + this.artifactId;
        }
    }
}
