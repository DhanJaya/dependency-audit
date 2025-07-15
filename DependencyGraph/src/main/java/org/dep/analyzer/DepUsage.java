package org.dep.analyzer;

import fr.dutra.tools.maven.deptree.core.Node;
import javassist.ClassPath;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.CtField;
import javassist.CtConstructor;
import javassist.NotFoundException;
import javassist.bytecode.BadBytecode;
import org.dep.model.Reference;
import org.dep.util.StandardJavaReferences;
import org.reference.ReferenceFinder;
import org.dep.util.CommandExecutor;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.traverse.BreadthFirstIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.dep.util.Helper.createFolderIfNotExists;

public class DepUsage {

    private static final Logger logger = LoggerFactory.getLogger(DepUsage.class);
    private static final String COPY_DEPENDENCY_FOLDER = "/DepCopied";
    private static final String TARGET = "/target";
    private static final String CLASSES = "/classes";
    private static final String TEST_CLASSES = "/test-classes";
    private final static String META_INF_FILE = "META-INF";

    public void extractDepUsage(Graph<Node, DefaultEdge> dependencyTree, File projectDir, String mvnCmd, Map<Node, Map<String, Set<Reference>>> mappedReferences, Map<String, Set<Reference>> allUnMappedReferences) throws IOException, NotFoundException, BadBytecode {
        if (copyProjectDependencies(projectDir, mvnCmd)) {
            Set<String> clientClasses = findJavaClassesInDirectory(projectDir);
            // get classes in dependencies jar files
            if (!clientClasses.isEmpty()) {
                File dependencyDirectory = new File(projectDir + COPY_DEPENDENCY_FOLDER);
                // a map with all the classes and the jarfiles they are linked to
                Map<String, List<Node>> allClassesInDep = new HashMap<>();
                //TODO: do we need to keep track of the files that could not be extracted
                getDependencyClasses(dependencyDirectory, dependencyTree, allClassesInDep);

                if (buildClientProject(projectDir, mvnCmd)) {
                    String projectTargetFolder = projectDir + TARGET;
                    // check if target exists and class and test-classes
                    File targetFolder = new File(projectTargetFolder);
                    if (targetFolder.exists()) {
                        // get classes in target folder
                        File classesDirectory = new File(projectTargetFolder + CLASSES);
                        File testClassesDirectory = new File(projectTargetFolder + TEST_CLASSES);
                        Map<String, Set<Reference>> referencesInClientCode = new HashMap<>();
                        getCallSitesToVerify(classesDirectory, referencesInClientCode);
                        getCallSitesToVerify(testClassesDirectory, referencesInClientCode);
                        // filter the client related classes and external classes
                        excludeInternalCallSites(referencesInClientCode, clientClasses);
                        //iteratively search for the invoked references in the dep classes
                        checkReferencesInDep(allClassesInDep, referencesInClientCode, mappedReferences, allUnMappedReferences);
                    }
                }
            }
        }
    }

    private void checkReferencesInDep(Map<String, List<Node>> allClassesInDep, Map<String, Set<Reference>> externalReferencesInvoked, Map<Node, Map<String, Set<Reference>>> mappedReferences, Map<String, Set<Reference>> allUnMappedReferences) throws NotFoundException, IOException {
        // include the java classes as well, so that the standard java class will not be marked as unmapped references
        Map<String, Set<String>> standardJavaClasses = StandardJavaReferences.loadStandardJavaReferences();
        ClassPool classPool = new ClassPool(true);
        for (String referencedClass : externalReferencesInvoked.keySet()) {
            // get jars connected to that class
            if (allClassesInDep.containsKey(referencedClass)) {
                //TODO: Currently we only check the first dependency that includes the class
                Set<Reference> unMappedReferences = new HashSet<>(externalReferencesInvoked.get(referencedClass));
                List<Node> dependenciesWithClass = allClassesInDep.get(referencedClass);
                List<String> parentClasses = new ArrayList<>();
                if (dependenciesWithClass.size() > 0) {
                    Node matchedDependency = dependenciesWithClass.get(0);
                    ClassPath classPath = classPool.insertClassPath(matchedDependency.getJarName());
                    mapReferenceWithDep(matchedDependency, referencedClass, unMappedReferences, mappedReferences, parentClasses, classPool);
                    classPool.removeClassPath(classPath);
                }
                if (!unMappedReferences.isEmpty()) {
                    iterativelySearchParentClasses(allClassesInDep, mappedReferences, unMappedReferences, parentClasses, classPool, standardJavaClasses);
                }
                if (!unMappedReferences.isEmpty()) {
                    allUnMappedReferences.put(referencedClass, unMappedReferences);
                }
            } else if (!standardJavaClasses.containsKey(referencedClass)) {
                allUnMappedReferences.put(referencedClass, new HashSet<>(externalReferencesInvoked.get(referencedClass)));
            }
        }
    }

    private void iterativelySearchParentClasses(Map<String, List<Node>> allClassesInDep, Map<Node, Map<String, Set<Reference>>> mappedReferences, Set<Reference> referencesToMap, List<String> parentClasses, ClassPool classPool, Map<String, Set<String>> standardJavaClasses) throws NotFoundException {
        if (!parentClasses.isEmpty() && !referencesToMap.isEmpty()) {
            for (String parentClass : parentClasses) {
                if (allClassesInDep.containsKey(parentClass)) {
                    List<Node> dependenciesWithParentClass = allClassesInDep.get(parentClass);
                    List<String> superParentClasses = new ArrayList<>();
                    if (dependenciesWithParentClass.size() > 0 && !referencesToMap.isEmpty()) {
                        // We only consider the first match with the node if not found it will be highlighted in the graph
                        Node matchedDependency = dependenciesWithParentClass.get(0);
                        ClassPath classPath = classPool.insertClassPath(matchedDependency.getJarName());
                        mapReferenceWithDep(matchedDependency, parentClass, referencesToMap, mappedReferences, superParentClasses, classPool);
                        classPool.removeClassPath(classPath);
                    }
                    if (!referencesToMap.isEmpty()) {
                        iterativelySearchParentClasses(allClassesInDep, mappedReferences, referencesToMap, superParentClasses, classPool, standardJavaClasses);
                    }
                } else {
                    // check if it is a Standard Java Reference
                    if (standardJavaClasses.containsKey(parentClass)) {
                        Set<String> classReferences = standardJavaClasses.get(parentClass);
                        if (!referencesToMap.isEmpty()) {
                            referencesToMap.removeIf(methodOrField -> classReferences.stream().anyMatch(ref -> ref.equals(methodOrField.getName())));
                        }
                    }
                }
            }
        }
    }

    private void mapReferenceWithDep(Node dependency, String referencedClass, Set<Reference> references, Map<Node, Map<String, Set<Reference>>> mappedReferences, List<String> parentClasses, ClassPool classPool) throws NotFoundException {
        if (references.isEmpty()) {
            Map mappedPriorReferences = mappedReferences.getOrDefault(dependency, new HashMap<>());
            mappedPriorReferences.put(referencedClass, references);
            mappedReferences.put(dependency, mappedPriorReferences);
        } else {
            CtClass ctClass = classPool.get(referencedClass);
            Set<Reference> mappedMethodsAndFields = new HashSet<>();

            mapReferences(references, ctClass, mappedMethodsAndFields);
            if (!mappedMethodsAndFields.isEmpty()) {
                Map mappedPriorReferences = mappedReferences.getOrDefault(dependency, new HashMap<>());
                mappedPriorReferences.put(referencedClass, mappedMethodsAndFields);
                mappedReferences.put(dependency, mappedPriorReferences);
            }
            if (!references.isEmpty()) {
                // extract the superclass and interfaces
                String superclass = ctClass.getClassFile().getSuperclass();
                if (superclass != null) {
                    parentClasses.add(superclass);
                }
                Collections.addAll(parentClasses, ctClass.getClassFile().getInterfaces());
            }
        }
    }

    private static void mapReferences(Set<Reference> methodsAndFields, CtClass ctClass, Set<Reference> mappedMethodsAndFields) {

        Iterator<Reference> iterator = methodsAndFields.iterator();

        while (iterator.hasNext()) {
            Reference methodOrField = iterator.next();
            boolean referenceFound = false;

            // Check methods
            for (CtMethod method : ctClass.getDeclaredMethods()) {
                if ((method.getName() + method.getSignature()).equals(methodOrField.getName())) {
                    mappedMethodsAndFields.add(methodOrField);
                    referenceFound = true;
                    break;
                }
            }
            if (!referenceFound) {
                for (CtConstructor constrtuctor : ctClass.getDeclaredConstructors()) {
                    if (("<init>" + constrtuctor.getSignature()).equals(methodOrField.getName())) {
                        mappedMethodsAndFields.add(methodOrField);
                        referenceFound = true;
                        break;
                    }
                }
            }

            // Check fields
            if (!referenceFound) {
                for (CtField field : ctClass.getDeclaredFields()) {
                    if (field.getName().equals(methodOrField.getName())) {
                        mappedMethodsAndFields.add(methodOrField);
                        referenceFound = true;
                        break;
                    }
                }
            }

            if (referenceFound) {
                // Remove matched item
                iterator.remove();
            }
        }
    }


    private void getDeepDepCalls(Map<Node, Set<String>> dependencyClasses, Map<String, Set<String>> callSitesInClient, Map<Node, Map<String, Set<String>>> invokedFunctions) {
        for (String classSite : callSitesInClient.keySet()) {
            for (Map.Entry<Node, Set<String>> entry : dependencyClasses.entrySet()) {
                Node node = entry.getKey();
                Set<String> libClasses = entry.getValue();
                for (String libClass : libClasses) {
                    String formatLibClass = libClass.replace("/", ".");
                    if (formatLibClass.equals(classSite)) { //TODO have to check if this works with Inner classes which have the $ sign
                        Map invokedCallAndMethods = invokedFunctions.getOrDefault(node, new HashMap<>());
                        invokedCallAndMethods.put(classSite, callSitesInClient.get(classSite));
                        invokedFunctions.put(node, invokedCallAndMethods);
                    }
                }
            }
        }
    }

    /**
     * Exclude the internal classes from the class sites
     *
     * @param references      all references which needs to be filtered
     * @param internalClasses internal classes to be filtered out
     */
    private void excludeInternalCallSites(Map<String, Set<Reference>> references, Set<String> internalClasses) {
        if (references != null) {
            Iterator<String> iterator = references.keySet().iterator();

            while (iterator.hasNext()) {
                String classOfSite = iterator.next();
                for (String internalClass : internalClasses) {
                    // format the internal classes
                    String formatInternalClass = internalClass.replace("/", ".");
                    if (classOfSite.startsWith(formatInternalClass) || (classOfSite.startsWith("[L") && classOfSite.startsWith("[L" + formatInternalClass))) {
                        iterator.remove(); // Safely remove the entry
                        break; // Exit inner loop after removal
                    }
                }
            }
        }
    }

    /**
     * Get call sites of project classes which needs to be verified with if they are external dependency calls
     *
     * @param classesDirectory directory path to get the class files
     * @return A map containing internal and external call sites used in the project
     * @throws IOException An exception will occur if the find call site method returns an exception while process
     */
    private void getCallSitesToVerify(File classesDirectory, Map<String, Set<Reference>> classesAndCallSites) throws IOException, BadBytecode {
        if (classesDirectory.exists()) {
            File[] classDirectories = classesDirectory.listFiles(File::isDirectory);
            for (File projectClassesDir : classDirectories) {
                if (!META_INF_FILE.equals(projectClassesDir.getName())) {
                    List<File> classFiles = Files.walk(projectClassesDir.toPath())
                            .map(Path::toFile)
                            .filter(f -> f.getName().endsWith(".class"))// && f.getName().contains("BadRequest") && f.getName().contains("Builder") && f.getName().contains("FieldViolation"))
                            .toList();

                    for (File classFile : classFiles) {
                        Map<String, Set<Reference>> extractedCallSites = ReferenceFinder.extractReferences(classFile.getAbsolutePath());
                        for (Map.Entry<String, Set<Reference>> entry : extractedCallSites.entrySet()) {
                            String key = entry.getKey();
                            Set<Reference> newSet = entry.getValue();

                            classesAndCallSites.merge(
                                    key,
                                    new HashSet<>(newSet),
                                    (existingSet, incomingSet) -> {
                                        existingSet.addAll(incomingSet);
                                        return existingSet;
                                    }
                            );
                        }
                    }
                }
            }
        }
    }

    /**
     * Extracting all java files in the entire repository
     *
     * @param dir directory of the repository (can also be the project folder of not child projects are available)
     * @return Set of java classes in the repository
     * @throws IOException
     */
    private Set<String> findJavaClassesInDirectory(File dir) throws IOException {
        Set<String> filteredClasses = new HashSet<>();
        try (Stream<Path> stream = Files.walk(Paths.get(dir.toURI()))) {
            Set<String> javaClassesInRepo = stream
                    .filter(file -> (!Files.isDirectory(file) && file.toString().endsWith(".java")))
                    .map(Path::toAbsolutePath)
                    .map(Path::toString)
                    .map(filePath -> filePath.replace(".java", ""))
                    .map(filePath -> filePath.replace("\\", "/"))
                    .collect(Collectors.toSet());
            for (String javaClassInRepo : javaClassesInRepo) {
                if (javaClassInRepo.contains("src/main/java/")) {
                    filteredClasses.add(javaClassInRepo.split("src/main/java/")[1]);
                } else if (javaClassInRepo.contains("src/test/java/")) {
                    filteredClasses.add(javaClassInRepo.split("src/test/java/")[1]);
                } else {
                    filteredClasses.add(javaClassInRepo);
                }
            }
        }
        return filteredClasses;
    }

    /**
     * Get all dependency classes in the collected dependency folder
     *
     * @param dependencyDirectory the directory in which all dependencies were copied to
     * @return all the classes in dependencies and any other external files
     * @throws IOException throw exception if jar file is not found
     */
    private void getDependencyClasses(File dependencyDirectory, Graph<Node, DefaultEdge> dependencyTree, Map<String, List<Node>> allClassesInDep) throws IOException {
        File[] depFiles = dependencyDirectory.listFiles();
        if (depFiles != null) {

            // map the jar file with the dependency name
            Node rootNode = dependencyTree.vertexSet().iterator().next();
            BreadthFirstIterator<Node, DefaultEdge> bfsIterator = new BreadthFirstIterator<>(dependencyTree, rootNode);
            boolean nodeNotFound = true;
            while (bfsIterator.hasNext()) {
                Node node = bfsIterator.next();
                if (!node.isOmitted()) {
                    for (File depFile : depFiles) {
                        if (depFile.getName().endsWith(".jar")) {
                            if ((node.getClassifier() == null &&
                                    depFile.getName().equals(node.getArtifactId() + "-" + node.getVersion() + ".jar")) ||
                                    (node.getClassifier() != null && depFile.getName().equals(node.getArtifactId() +
                                            "-" + node.getVersion() + "-" + node.getClassifier() + ".jar"))) {
                                node.setJarName(depFile.getAbsolutePath());
                                // add all classes of the jar file in to the Map
                                Set<String> allClasses = getJavaClassNamesFromCompressedFiles(depFile);
                                for (String className : allClasses) {
                                    String formattedClassName = className.replace("/", ".");
                                    List<Node> depThatContainsClass = allClassesInDep.getOrDefault(formattedClassName, new ArrayList<Node>());
                                    depThatContainsClass.add(node);
                                    allClassesInDep.put(formattedClassName, depThatContainsClass);
                                }
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * get all java classes for a given jar file
     *
     * @param givenFile jar file to extract the java classes
     * @return the java classes in the jar file
     * @throws IOException
     */
    private Set<String> getJavaClassNamesFromCompressedFiles(File givenFile) throws IOException {
        Set<String> classNames = new HashSet<>();
        try (JarFile jarFile = new JarFile(givenFile)) {
            Enumeration<JarEntry> e = jarFile.entries();
            while (e.hasMoreElements()) {
                JarEntry jarEntry = e.nextElement();
                if (jarEntry.getName().endsWith(".class")) {
                    String className = jarEntry.getName().replace(".class", "");
                    classNames.add(className);
                }
            }
        }
        return classNames;
    }

    /**
     * Build the client project without tests to generate the class files for the analysis
     *
     * @param projectDir the local directory which contains the project
     * @return if the project was successfully built or not
     * @throws IOException
     */
    private boolean buildClientProject(File projectDir, String mvnCmd) throws IOException {
        if (CommandExecutor.executeCommand(String.format("%s clean compile test-compile", mvnCmd), projectDir).contains("BUILD SUCCESS")) {
            logger.info("Project compiles successfully!!");
            return true;
        } else {
            logger.warn("Failed to compile the project");
            return false;
        }
    }

    /**
     * Copy the client dependencies to the provided folder for the analysis
     *
     * @param projectDir the local directory which contains the project
     * @return if the dependencies were copied to the directory
     * @throws IOException
     */
    private boolean copyProjectDependencies(File projectDir, String mvnCmd) throws IOException {
        boolean dependenciesCopied = false;
        String depCopiedLocation = projectDir.toString() + COPY_DEPENDENCY_FOLDER;
        // need to create a folder to copy the dependencies within the client project
        if (createFolderIfNotExists(depCopiedLocation)) {
            if (CommandExecutor.executeCommand(String.format("%s dependency:copy-dependencies -DoutputDirectory=%s", mvnCmd, depCopiedLocation), projectDir).contains("BUILD SUCCESS")) {
                dependenciesCopied = true;
                logger.info("Copied dependencies for  " + depCopiedLocation);
            } else {
                logger.warn("Failed to copy the dependencies for the project");
            }
        } else {
            logger.warn("Failed to create folder to copy the dependencies");

        }
        return dependenciesCopied;
    }

}
