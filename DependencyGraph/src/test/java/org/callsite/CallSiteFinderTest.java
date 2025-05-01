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
    public void testExtractClassDetails() throws IOException, NotFoundException, BadBytecode {
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
            CallSiteFinder.detectCallSites(cf, methodCalls, fieldReferences);
            Assertions.assertEquals(6, methodCalls.size());
            // verify Implemented interface
            Assertions.assertTrue(methodCalls.containsKey("org.apache.commons.exec.ProcessDestroyer"));
            // verify class annotation
            Assertions.assertTrue(methodCalls.containsKey("java.beans.JavaBean"));
            // verify extended super class
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

            CallSiteFinder.detectCallSites(cf, methodCalls, fieldReferences);
            Assertions.assertEquals(15, methodCalls.size());
            // verify class variable initialization
            Assertions.assertTrue(methodCalls.containsKey("org.slf4j.LoggerFactory"));
            // verify class variable initialization method invocation
            Assertions.assertTrue(methodCalls.get("org.slf4j.LoggerFactory").contains("getLogger(Ljava/lang/Class;)Lorg/slf4j/Logger;"));
            // verify instance variable initialization
            Assertions.assertTrue(methodCalls.containsKey("org.apache.commons.cli.Option$Builder"));
            // verify instance variable initialization method invocation
            Assertions.assertTrue(methodCalls.get("org.apache.commons.cli.Option$Builder").contains("argName(Ljava/lang/String;)Lorg/apache/commons/cli/Option$Builder;"));
            // verify instance variable type
            Assertions.assertTrue(methodCalls.containsKey("java.lang.StringBuilder"));
            // verify instance variable initialization using chained method
            Assertions.assertTrue(methodCalls.get("java.lang.StringBuilder").contains("append(Ljava/lang/String;)Ljava/lang/StringBuilder;"));
            // verify exception thrown in static block
            Assertions.assertTrue(methodCalls.containsKey("java.lang.ClassNotFoundException"));
            // verify method invoked in the exception thrown in static block
            Assertions.assertTrue(methodCalls.get("java.lang.ClassNotFoundException").contains("printStackTrace()V"));
            // verify annotation used for the variable
            Assertions.assertTrue(methodCalls.containsKey("org.apache.commons.lang3.builder.ToStringExclude"));

            Assertions.assertEquals(4, fieldReferences.size());
            // verify class variable name
            Assertions.assertTrue(fieldReferences.get("org.callsite.InstanceAndClassFields").contains("CLASS_OPTION"));
        }
    }

    @Test
    public void testExtractMethodDetails() throws IOException, NotFoundException, BadBytecode {
        String classFilePath = "testcallsite\\MethodLevel.class";
        Map<String, Set<String>> methodCalls = new HashMap<>();
        Map<String, Set<String>> fieldReferences = new HashMap<>();
        // Load as a URL
        URL resource = getClass().getClassLoader().getResource(classFilePath);
        if (resource == null) {
            throw new IllegalArgumentException("Class file not found: " + classFilePath);
        }

        try (InputStream inputStream = resource.openStream()) {
            ClassFile cf = new ClassFile(new DataInputStream(inputStream));
            CallSiteFinder.detectCallSites(cf, methodCalls, fieldReferences);
            Assertions.assertEquals(33, methodCalls.size());
            // verify method signature exception
            Assertions.assertTrue(methodCalls.containsKey("org.apache.commons.cli.UnrecognizedOptionException"));
            // verify local variable
            Assertions.assertTrue(methodCalls.containsKey("org.apache.commons.cli.Options"));
            // verify local variable with chained methods
            Assertions.assertTrue(methodCalls.containsKey("org.apache.commons.cli.Option$Builder"));
            Assertions.assertTrue(methodCalls.get("org.apache.commons.cli.Option$Builder").contains("required(Z)Lorg/apache/commons/cli/Option$Builder;"));
            // verify new object created
            Assertions.assertTrue(methodCalls.containsKey("org.apache.commons.cli.DefaultParser"));
            Assertions.assertTrue(methodCalls.get("org.apache.commons.cli.DefaultParser").contains("<init>()V"));
            // verify caught exception
            Assertions.assertTrue(methodCalls.containsKey("org.apache.commons.cli.ParseException"));
            // verify thrown exception
            Assertions.assertTrue(methodCalls.containsKey("java.lang.RuntimeException"));
            // verify method return type
            Assertions.assertTrue(methodCalls.containsKey("fr.dutra.tools.maven.deptree.core.Node"));
            // verify method return type
            Assertions.assertTrue(methodCalls.containsKey("org.jgrapht.Graph"));
            // verify method nested return type
            Assertions.assertTrue(methodCalls.containsKey("org.dep.analyzer.GraphAnalyzer"));
            // verify method parameter type
            Assertions.assertTrue(methodCalls.containsKey("org.apache.commons.io.input.CharSequenceInputStream"));
            // verify method nested parameter type
            Assertions.assertTrue(methodCalls.containsKey("org.callsite.CallSiteFinder"));
            // verify cast type and its method invocation
            Assertions.assertTrue(methodCalls.get("javassist.bytecode.MethodInfo").contains("getAttribute(Ljava/lang/String;)Ljavassist/bytecode/AttributeInfo;"));
            // verify method annotation
            Assertions.assertTrue(methodCalls.containsKey("java.lang.Deprecated"));
        }
    }

}
