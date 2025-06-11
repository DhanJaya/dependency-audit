package org.reference;

import javassist.bytecode.BadBytecode;
import javassist.bytecode.ClassFile;
import org.dep.model.Reference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

import java.io.*;
import java.net.URL;
import java.util.Map;
import java.util.Set;

public class ReferenceFinderTest {

    @Test
    public void testExtractClassDetails() throws IOException, BadBytecode {
        String classFilePath = "testcallsite\\ClassLevel.class";
        // Load as a URL
        URL resource = getClass().getClassLoader().getResource(classFilePath);
        if (resource == null) {
            throw new IllegalArgumentException("Class file not found: " + classFilePath);
        }

        try (InputStream inputStream = resource.openStream()) {
            ClassFile cf = new ClassFile(new DataInputStream(inputStream));
            Map<String, Set<Reference>> classReferences = ReferenceFinder.detectReferences(cf);

            Assertions.assertEquals(7, classReferences.size());
            // verify Implemented interface
            Assertions.assertTrue(classReferences.containsKey("org.apache.commons.exec.ProcessDestroyer"));
            // verify class annotation
            Assertions.assertTrue(classReferences.containsKey("java.beans.JavaBean"));
            // verify extended super class
            Assertions.assertTrue(classReferences.containsKey("org.callsite.SuperClass"));
        }
    }

    @Test
    public void testExtractFieldDetails() throws IOException, BadBytecode {
        String classFilePath = "testcallsite\\InstanceAndClassFields.class";
        // Load as a URL
        URL resource = getClass().getClassLoader().getResource(classFilePath);
        if (resource == null) {
            throw new IllegalArgumentException("Class file not found: " + classFilePath);
        }

        try (InputStream inputStream = resource.openStream()) {
            ClassFile cf = new ClassFile(new DataInputStream(inputStream));
            Map<String, Set<Reference>> classReferences = ReferenceFinder.detectReferences(cf);
            Assertions.assertEquals(15, classReferences.size());
            // verify class variable initialization
            Assertions.assertTrue(classReferences.containsKey("org.slf4j.LoggerFactory"));
            // verify class variable initialization method invocation
            Reference refLoggerFactory  = classReferences.get("org.slf4j.LoggerFactory").iterator().next();
            Assertions.assertTrue(refLoggerFactory.getName().equals("getLogger(Ljava/lang/Class;)Lorg/slf4j/Logger;"));
            // verify instance variable initialization
            Assertions.assertTrue(classReferences.containsKey("org.apache.commons.cli.Option$Builder"));
            Assertions.assertEquals(6, classReferences.get("org.apache.commons.cli.Option$Builder").size());
            // verify instance variable initialization method invocation
            Assertions.assertTrue(classReferences.get("org.apache.commons.cli.Option$Builder").stream().anyMatch(ref ->"required(Z)Lorg/apache/commons/cli/Option$Builder;".equals(ref.getName())));
            // verify instance variable type
            Assertions.assertTrue(classReferences.containsKey("java.lang.StringBuilder"));
            Assertions.assertEquals(2, classReferences.get("java.lang.StringBuilder").size());
            // verify instance variable initialization using chained method
            Assertions.assertTrue(classReferences.get("java.lang.StringBuilder").stream().anyMatch(ref ->"<init>(Ljava/lang/String;)V".equals(ref.getName())));
            // verify exception thrown in static block
            Assertions.assertTrue(classReferences.containsKey("java.lang.ClassNotFoundException"));
            // verify method invoked in the exception thrown in static block
            Reference refClassNotFoundException  = classReferences.get("java.lang.ClassNotFoundException").iterator().next();
            Assertions.assertTrue(refClassNotFoundException.getName().equals("printStackTrace()V"));
            // verify annotation used for the variable
            Assertions.assertTrue(classReferences.containsKey("org.apache.commons.lang3.builder.ToStringExclude"));

            // verify class variable name
            Assertions.assertTrue(classReferences.containsKey("org.callsite.InstanceAndClassFields"));
            Assertions.assertEquals(4, classReferences.get("org.callsite.InstanceAndClassFields").size());
            // verify instance variable initialization using chained method
            Assertions.assertTrue(classReferences.get("org.callsite.InstanceAndClassFields").stream().anyMatch(ref ->"stringBuilder".equals(ref.getName())));
        }
    }

    @Test
    public void testExtractMethodDetails() throws IOException, BadBytecode {
        String classFilePath = "testcallsite\\MethodLevel.class";

        // Load as a URL
        URL resource = getClass().getClassLoader().getResource(classFilePath);
        if (resource == null) {
            throw new IllegalArgumentException("Class file not found: " + classFilePath);
        }

        try (InputStream inputStream = resource.openStream()) {
            ClassFile cf = new ClassFile(new DataInputStream(inputStream));
            Map<String, Set<Reference>> classReferences = ReferenceFinder.detectReferences(cf);
            Assertions.assertEquals(28, classReferences.size());
            // verify method signature exception
            Assertions.assertTrue(classReferences.containsKey("org.apache.commons.cli.UnrecognizedOptionException"));
            // verify local variable
            Assertions.assertTrue(classReferences.containsKey("org.apache.commons.cli.Options"));
            // verify local variable with chained methods
            Assertions.assertTrue(classReferences.containsKey("org.apache.commons.cli.Option$Builder"));
            Assertions.assertTrue(classReferences.get("org.apache.commons.cli.Option$Builder").stream().anyMatch(ref ->"required(Z)Lorg/apache/commons/cli/Option$Builder;".equals(ref.getName())));
            // verify new object created
            Assertions.assertTrue(classReferences.containsKey("org.apache.commons.cli.DefaultParser"));
            Assertions.assertTrue(classReferences.get("org.apache.commons.cli.DefaultParser").stream().anyMatch(ref ->"<init>()V".equals(ref.getName())));
            // verify caught exception
            Assertions.assertTrue(classReferences.containsKey("org.apache.commons.cli.ParseException"));
            // verify thrown exception
            Assertions.assertTrue(classReferences.containsKey("java.lang.RuntimeException"));
            // verify method return type
            Assertions.assertTrue(classReferences.containsKey("fr.dutra.tools.maven.deptree.core.Node"));
            // verify method return type
            Assertions.assertTrue(classReferences.containsKey("org.jgrapht.Graph"));
            // verify method nested return type
            Assertions.assertTrue(classReferences.containsKey("org.dep.analyzer.GraphAnalyzer"));
            // verify method parameter type
            Assertions.assertTrue(classReferences.containsKey("org.apache.commons.io.input.CharSequenceInputStream"));
            // verify method nested parameter type
            Assertions.assertTrue(classReferences.containsKey("org.callsite.CallSiteFinder"));
            // verify cast type and its method invocation
            Assertions.assertTrue(classReferences.get("javassist.bytecode.MethodInfo").stream().anyMatch(ref ->"getAttribute(Ljava/lang/String;)Ljavassist/bytecode/AttributeInfo;".equals(ref.getName())));
            // verify method annotation
            Assertions.assertTrue(classReferences.containsKey("java.lang.Deprecated"));
        }
    }

}
