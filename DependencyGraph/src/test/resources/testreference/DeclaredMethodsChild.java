package org.reference;

import java.util.ArrayList;

public class DeclaredMethodsChild extends DeclaredMethodsParent{
    public ArrayList<String> foo() {
        System.out.println("Parent");
        return new ArrayList<>();
    }
}
