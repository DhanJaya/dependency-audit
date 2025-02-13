package org.dep.model;

public class DependencyModel {
    private final String groupId;
    private final String artifactId;
    private final String version;
    private final String classifier;
    private final int depLevel;

    public DependencyModel(String groupId, String artifactId, String version, String classifier, int depLevel) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.classifier = classifier;
        this.depLevel = depLevel;
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

    public String getDependencyName(boolean withClassifier) {
        if (withClassifier) {
            return (classifier != null) ? (this.groupId + ":" + this.artifactId + ":" + this.classifier) :
                    (this.groupId + ":" + this.artifactId);
        } else {
            return this.groupId + ":" + this.artifactId;
        }
    }
}
