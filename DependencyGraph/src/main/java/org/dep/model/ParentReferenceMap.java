package org.dep.model;

import fr.dutra.tools.maven.deptree.core.Node;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ParentReferenceMap {

    Node initialChildNode;
    List<String> parentClasses = new ArrayList<>();
    Set<String> methodsAndFields = new HashSet<>();

    public ParentReferenceMap(Node childNode) {
        this.initialChildNode = childNode;
    }

    public Node getInitialChildNode() {
        return initialChildNode;
    }

    public void setInitialChildNode(Node initialChildNode) {
        this.initialChildNode = initialChildNode;
    }

    public List<String> getParentClasses() {
        return parentClasses;
    }

    public void addParentClasses(String parentClass) {
        this.parentClasses.add(parentClass);
    }

    public Set<String> getMethodsAndFields() {
        return methodsAndFields;
    }

    public void setMethodsAndFields(Set<String> methodsAndFields) {
        this.methodsAndFields = methodsAndFields;
    }
}
