package org.callsite;

import javassist.NotFoundException;
import javassist.bytecode.BadBytecode;
import javassist.bytecode.ClassFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

import java.io.*;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class CallSiteFinderTest {

    @Test
    public void testExtractClassDetails() throws IOException, NotFoundException {
        String classFilePath = "testcallsite\\ClassLevel.class";
        Map<String, Set<String>> methodCalls = new HashMap<>();
        Map<String, Set<String>> fieldReferences = new HashMap<>();
        // Load as a URL
        URL resource = getClass().getClassLoader().getResource(classFilePath);
        if (resource == null) {
            throw new IllegalArgumentException("Class file not found: " + classFilePath);
        }

        try (InputStream inputStream = resource.openStream()) {
            ClassFile cf = new ClassFile(new DataInputStream(inputStream));
            CallSiteFinder.extractCallSites(cf, methodCalls, fieldReferences);
            Assertions.assertEquals(6, methodCalls.size());
            Assertions.assertTrue(methodCalls.containsKey("org.apache.commons.exec.ProcessDestroyer"));
            Assertions.assertTrue(methodCalls.containsKey("java.beans.JavaBean"));
            Assertions.assertTrue(methodCalls.containsKey("org.callsite.SuperClass"));
        }
    }


    @Test
    public void testExtractFieldDetails() throws IOException, NotFoundException, BadBytecode {
        String classFilePath = "testcallsite\\InstanceAndClassFields.class";
        Map<String, Set<String>> methodCalls = new HashMap<>();
        Map<String, Set<String>> fieldReferences = new HashMap<>();
        // Load as a URL
        URL resource = getClass().getClassLoader().getResource(classFilePath);
        if (resource == null) {
            throw new IllegalArgumentException("Class file not found: " + classFilePath);
        }

        try (InputStream inputStream = resource.openStream()) {
            ClassFile cf = new ClassFile(new DataInputStream(inputStream));

            CallSiteFinder.extractCallSites(cf, methodCalls, fieldReferences);
            Assertions.assertEquals(15, methodCalls.size());
            Assertions.assertTrue(methodCalls.containsKey("org.slf4j.LoggerFactory"));
            Assertions.assertTrue(methodCalls.get("org.slf4j.LoggerFactory").contains("getLogger::(Ljava/lang/Class;)Lorg/slf4j/Logger;"));
            Assertions.assertTrue(methodCalls.containsKey("org.apache.commons.cli.Option$Builder"));
            Assertions.assertTrue(methodCalls.get("org.apache.commons.cli.Option$Builder").contains("argName::(Ljava/lang/String;)Lorg/apache/commons/cli/Option$Builder;"));
            Assertions.assertTrue(methodCalls.containsKey("java.lang.StringBuilder"));
            Assertions.assertTrue(methodCalls.get("java.lang.StringBuilder").contains("append::(Ljava/lang/String;)Ljava/lang/StringBuilder;"));
            Assertions.assertTrue(methodCalls.containsKey("java.lang.ClassNotFoundException"));
            Assertions.assertTrue(methodCalls.get("java.lang.ClassNotFoundException").contains("printStackTrace::()V"));
            Assertions.assertTrue(methodCalls.containsKey("org.apache.commons.lang3.builder.ToStringExclude"));

            Assertions.assertEquals(4, fieldReferences.size());
            Assertions.assertTrue(fieldReferences.containsKey("org.callsite.InstanceAndClassFields"));
            Assertions.assertTrue(fieldReferences.get("org.callsite.InstanceAndClassFields").contains("CLASS_OPTION"));
        }
    }

}
