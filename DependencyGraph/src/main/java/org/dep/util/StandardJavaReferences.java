package org.dep.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import javassist.*;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class StandardJavaReferences {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String JAVA_REFERENCES = "InBuiltJavaMethods/java-references.json";

    public static void main(String[] args) throws Exception {
        List<String> javaClasses = getJavaClasses();

        Map<String, Set<String>> allJavaReferences = extractReferences(javaClasses);
        saveStandardJavaReferences(allJavaReferences, getStandardJavaReferences());

        Map<String, Set<String>> loaded = loadStandardJavaReferences();

        // Print to verify
        loaded.forEach((cls, members) -> {
            System.out.println("Class: " + cls);
            members.forEach(member -> System.out.println("  " + member));
        });
    }

    // Extract method and field names from classes
    public static Map<String, Set<String>> extractReferences(List<String> javaClasses) {
        Map<String, Set<String>> allJavaReferences = new HashMap<>();
        ClassPool pool = ClassPool.getDefault();

        for (String javaClass : javaClasses) {
            Set<String> references = new HashSet<>();
            CtClass clazz = null;
            try {
                clazz = pool.get(javaClass);
            } catch (NotFoundException e) {
                continue;
            }

            for (CtMethod ctMethod : clazz.getMethods()) {
                references.add(ctMethod.getName() + ctMethod.getSignature());
            }
            for (CtField field : clazz.getFields()) {
                references.add(field.getName());
            }

            allJavaReferences.put(javaClass, references);
        }
        return allJavaReferences;
    }

    // Save to JSON
    public static void saveStandardJavaReferences(Map<String, Set<String>> data, File file) throws IOException {
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, data);
    }

    // Read from JSON
    public static Map<String, Set<String>> loadStandardJavaReferences() throws IOException, URISyntaxException {

        return objectMapper.readValue(getStandardJavaReferences(), new TypeReference<Map<String, Set<String>>>() {
        });
    }

    private static File getStandardJavaReferences() throws URISyntaxException {
        URL resourceUrl = StandardJavaReferences.class.getClassLoader().getResource(JAVA_REFERENCES);
        if (resourceUrl == null) {
            throw new IllegalArgumentException("Java References file not found");
        }
        return new File(resourceUrl.toURI());
    }
    /**
     * Extract all the Java classes in the Standard Java library. This code only works for Java 9+, since till Java 8 the standard java classes were available in the rt.jar file
     *
     * @return
     * @throws IOException
     */
    private static List<String> getJavaClasses() throws IOException {
        List<String> standardJavaClasses = new ArrayList<>();
        URI uri = URI.create("jrt:/");
        try (FileSystem fs = FileSystems.newFileSystem(uri, java.util.Collections.emptyMap())) {
            Path basePath = fs.getPath("/modules");
            // Collect all paths first to reuse them
            List<Path> allPaths;
            try (Stream<Path> pathStream = Files.walk(basePath)) {
                allPaths = pathStream.collect(Collectors.toList());
            }

            // Extract module names from module-info.class files
            List<String> moduleNames = allPaths.stream()
                    .filter(path -> path.toString().endsWith("module-info.class"))
                    .map(path -> path.toString().replace("module-info.class", ""))
                    .collect(Collectors.toList());

            // Extract class names from .class files
            allPaths.stream()
                    .filter(path -> path.toString().endsWith(".class"))
                    .forEach(path -> {
                        String classPath = path.toString();
                        for (String module : moduleNames) {
                            if (classPath.startsWith(module)) {
                                String className = classPath.substring(module.length())
                                        .replace(".class", "")
                                        .replace("/", ".");
                                standardJavaClasses.add(className);
                            }
                        }
                    });
        }
        return standardJavaClasses;
    }

}
