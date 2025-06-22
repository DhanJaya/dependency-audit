package org.reference;

import java.util.ArrayList;
import java.util.List;

public class DeclaredMethodsParent {

    public List<String> foo() {
        System.out.println("Parent");
        return new ArrayList<>();
    }
}
