package org.dep.model;

import java.util.Objects;

public class Reference {
    String name;
    String instruction;


    public Reference(String name, String invocationType) {
        this.name = name;
        this.instruction = invocationType;
    }

    public String getName() {
        return name;
    }

    public String getInstruction() {
        return instruction;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Reference reference = (Reference) o;
        return Objects.equals(name, reference.name) && Objects.equals(instruction, reference.instruction);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, instruction);
    }
}
