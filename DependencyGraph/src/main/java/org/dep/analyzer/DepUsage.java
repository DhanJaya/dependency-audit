package org.dep.analyzer;

import fr.dutra.tools.maven.deptree.core.Node;
import javassist.*;
import javassist.bytecode.BadBytecode;
import org.reference.ReferenceFinder;
import org.dep.util.CommandExecutor;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.traverse.BreadthFirstIterator;
import org.dep.model.ParentReferenceMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
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

    public void extractDepUsage(Graph<Node, DefaultEdge> dependencyTree, File projectDir, String mvnCmd) throws IOException, NotFoundException, BadBytecode {
        if (copyProjectDependencies(projectDir, mvnCmd)) {
            Set<String> clientClasses = findJavaClassesInDirectory(projectDir);
            // get classes in dependencies jar files
            if (!clientClasses.isEmpty()) {
                File dependencyDirectory = new File(projectDir + COPY_DEPENDENCY_FOLDER);
                Map<Node, Set<String>> dependencyClasses = new LinkedHashMap<>();
                //TODO: do we need to keep track of the files that could not be extracted
                Set<String> externalFiles = getDependencyClasses(dependencyDirectory, dependencyClasses, dependencyTree);
                if (buildClientProject(projectDir, mvnCmd)) {
                    String projectTargetFolder = projectDir + TARGET;
                    // check if target exists and class and test-classes
                    File targetFolder = new File(projectTargetFolder);
                    if (targetFolder.exists()) {
                        // get classes in target folder
                        File classesDirectory = new File(projectTargetFolder + CLASSES);
                        File testClassesDirectory = new File(projectTargetFolder + TEST_CLASSES);
                        Map<String, Set<String>> callSitesInClientSourceCode = getCallSitesToVerify(classesDirectory);
                        // classesCallSites.entrySet().forEach(entrey -> {entrey.getValue().forEach(val -> logger.info(val));});
                        Map<String, Set<String>> callSitesInClientTestCode = getCallSitesToVerify(testClassesDirectory);
                        // TODO: filter the client related classes and external classes
                        excludeInternalCallSites(callSitesInClientSourceCode, clientClasses);
                        excludeInternalCallSites(callSitesInClientTestCode, clientClasses);
                        // TODO: map the call sites with dependency classes
                        Map<Node, Map<String, Set<String>>> invokedReferences = new HashMap<>();
                        getDeepDepCalls(dependencyClasses, callSitesInClientSourceCode, invokedReferences);
                        getDeepDepCalls(dependencyClasses, callSitesInClientTestCode, invokedReferences);
                        // Check if all the functions invoked are actually in the jar file of the dependency its matched with
                        Map<Node, Map<String, Set<String>>> unMappedReferences = new HashMap<>();
                        Map<Node, Map<String, Set<String>>> mappedReferences = new HashMap<>();
                        List<ParentReferenceMap> parentReferencesToVerify = verifyFunctionsAreInDepJars(invokedReferences, dependencyDirectory, mappedReferences, unMappedReferences);

                        // if unMappedReferences is not empty need to run this iteratively
                        if (parentReferencesToVerify.size() > 0) {
                            // need to check the dependency tree to verify the class

                        }


                    }
                }
            }
        }
    }

    private List<ParentReferenceMap> verifyFunctionsAreInDepJars(Map<Node, Map<String, Set<String>>> invokedReferences, File depDirectory, Map<Node, Map<String, Set<String>>> mappedReferences, Map<Node, Map<String, Set<String>>> unMappedReferences) {
        List<ParentReferenceMap> parentReferencesToVerify = new ArrayList<>();
        for (Map.Entry<Node, Map<String, Set<String>>> invokedReferencessEntry : invokedReferences.entrySet()) {
            Node dependency = invokedReferencessEntry.getKey();
            Map<String, Set<String>> invokedClassAndReferences = invokedReferencessEntry.getValue();
            String dependencyJarName = dependency.getJarName();
            // check if jar is in path
            try (JarFile dependencyJarFile = new JarFile(depDirectory + "/" + dependencyJarName)) {
                for (Map.Entry<String, Set<String>> invokedClassAndReferencesEntry : invokedClassAndReferences.entrySet()) {
                    String className = invokedClassAndReferencesEntry.getKey();
                    Set<String> methodsAndFields = invokedClassAndReferencesEntry.getValue();

                    String classEntryName = className.replace('.', '/') + ".class";
                    JarEntry jarEntry = dependencyJarFile.getJarEntry(classEntryName);

                    if (trackNotFoundClasses(unMappedReferences, dependency, className, methodsAndFields, jarEntry))
                        break;
                    if (methodsAndFields.isEmpty()) {
                        Map mappedPriorReferences = mappedReferences.getOrDefault(dependency, new HashMap<>());
                        mappedPriorReferences.put(className, methodsAndFields);
                        mappedReferences.put(dependency, mappedPriorReferences);
                    } else {
                        // if there is a match need to check if the class contains the fields and methods
                        InputStream classInputStream = dependencyJarFile.getInputStream(jarEntry);
                        ClassPool pool = ClassPool.getDefault();
                        CtClass ctClass = pool.makeClass(classInputStream);
                        Set<String> unMappedMethodsAndFields = new HashSet<>();
                        Set<String> mappedMethodsAndFields = new HashSet<>();

                        mapReferences(methodsAndFields, ctClass, unMappedMethodsAndFields, mappedMethodsAndFields);
                        if (!mappedMethodsAndFields.isEmpty()) {
                            Map mappedPriorReferences = mappedReferences.getOrDefault(dependency, new HashMap<>());
                            mappedPriorReferences.put(className, mappedMethodsAndFields);
                            mappedReferences.put(dependency, mappedPriorReferences);

                        }
                        if (!unMappedMethodsAndFields.isEmpty()) {
                            verifyUnMappedReferences(parentReferencesToVerify, dependency, ctClass, unMappedMethodsAndFields, dependencyJarFile, mappedReferences);
                        }
                        ctClass.detach();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return parentReferencesToVerify;
    }

    private static boolean trackNotFoundClasses(Map<Node, Map<String, Set<String>>> unMappedReferences, Node dependency, String className, Set<String> methodsAndFields, JarEntry jarEntry) {
        if (jarEntry == null) {
            // This will create a Linkage error if the class is not found, hence we need to track it
            System.out.println("Class " + className + " not found in the jar.");
            Map unMappedPriorReferences = unMappedReferences.getOrDefault(dependency, new HashMap<>());
            unMappedPriorReferences.put(className, methodsAndFields);
            unMappedReferences.put(dependency, unMappedPriorReferences);
            return true;
        }
        return false;
    }

    private static void mapReferences(Set<String> methodsAndFields, CtClass ctClass, Set<String> unMappedMethodsAndFields, Set<String> mappedMethodsAndFields) {
        boolean methodFound = false;
        boolean fieldFound = false;

        for (String methodOrField : methodsAndFields) {
            // Check methods
            for (CtMethod method : ctClass.getDeclaredMethods()) {
                if (method.getName().equals(methodOrField)) {
                    mappedMethodsAndFields.add(methodOrField);
                    methodFound = true;
                }
            }

            // Check fields
            for (CtField field : ctClass.getDeclaredFields()) {
                if (field.getName().equals(methodOrField)) {
                    mappedMethodsAndFields.add(methodOrField);
                    fieldFound = true;
                }
            }
            if (!methodFound && !fieldFound) {
                unMappedMethodsAndFields.add(methodOrField);
            }
        }
    }

    private static void verifyUnMappedReferences(List<ParentReferenceMap> parentReferencesToVerify, Node dependency, CtClass ctClass, Set<String> unMappedMethodsAndFields, JarFile jarFile, Map<Node, Map<String, Set<String>>> mappedReferences) throws NotFoundException, IOException {
        //TODO: have to verify if the super class or the interface has declared the methods of fields
        // these can be from different classes not belonging to the current Jar file as well

        unMappedMethodsAndFields = iterativelySearchParents(dependency, unMappedMethodsAndFields, jarFile, mappedReferences, ctClass);

        ParentReferenceMap parentReferenceMap = new ParentReferenceMap(dependency);
//        parentReferenceMap.setMethodsAndFields(unMappedMethodsAndFields);
//        if (superclass != null) {
//            parentReferenceMap.addParentClasses(superclass.getName()); // todo: check if this is the full name
//        }
//        if (interfaces.length > 0) {
//            for (CtClass inheritedInterface : interfaces) {
//                parentReferenceMap.addParentClasses(inheritedInterface.getName()); // todo: check if this is the full name
//            }
//        }
        parentReferencesToVerify.add(parentReferenceMap);
    }

    private static Set<String> iterativelySearchParents(Node dependency, Set<String> unMappedMethodsAndFields, JarFile jarFile, Map<Node, Map<String, Set<String>>> mappedReferences, CtClass currentClass) throws IOException, NotFoundException {
        boolean validSuperClass = true;
        boolean validInterfaces = true;

        while ((validSuperClass || validInterfaces) && !unMappedMethodsAndFields.isEmpty()) {
            CtClass superclass = currentClass.getSuperclass();
            CtClass [] interfaces = currentClass.getInterfaces();

            validSuperClass = (superclass != null || !"java.lang.Object".equals(superclass.getName()));

            // Check if all interfaces are either null or java.lang.Object
            validInterfaces = false;
            for (CtClass interfaceType : interfaces) {
                if (interfaceType != null && !"java.lang.Object".equals(interfaceType.getName())) {
                    validInterfaces = true;
                    break;
                }
            }
            unMappedMethodsAndFields = verifyParentReferences(dependency, unMappedMethodsAndFields, jarFile, mappedReferences, superclass);
            if (!unMappedMethodsAndFields.isEmpty()) {
                for (CtClass interfaceType : interfaces) {
                    unMappedMethodsAndFields = verifyParentReferences(dependency, unMappedMethodsAndFields, jarFile, mappedReferences, interfaceType);
                    if (unMappedMethodsAndFields.isEmpty()) {
                        break;
                    }
                }
            }
            currentClass = superclass;
            // TODO: have to think about iteratively searching the interfaces

        }
        return unMappedMethodsAndFields;
    }

    private static Set<String> verifyParentReferences(Node dependency, Set<String> unMappedMethodsAndFields, JarFile jarFile, Map<Node, Map<String, Set<String>>> mappedReferences, CtClass parentClass) throws IOException {
        if (parentClass != null && !"java.lang.Object".equals(parentClass)) {
            // check if the super type is in the same jar file if not pass it in to the Dependency tree
            if (parentClass != null) {
                String superClassEntryName = parentClass.getName().replace('.', '/') + ".class";
                JarEntry superClassJarEntry = jarFile.getJarEntry(superClassEntryName);
                if (superClassJarEntry != null) {
                    unMappedMethodsAndFields = checkIfReferencesExist(dependency, superClassEntryName, unMappedMethodsAndFields, jarFile, mappedReferences, superClassJarEntry);
                }
            }
        }
        return unMappedMethodsAndFields;
    }

    private static Set<String> checkIfReferencesExist(Node node, String className, Set<String> unMappedMethodsAndFields, JarFile jarFile, Map<Node, Map<String, Set<String>>> mappedMethods, JarEntry jarEntry) throws IOException {
        InputStream classInputStream = jarFile.getInputStream(jarEntry);
        ClassPool pool = ClassPool.getDefault();
        CtClass ctParentClass = pool.makeClass(classInputStream);
        Set<String> unMappedReferencesInParent = new HashSet<>();
        Set<String> mappedMethodsAndFields = new HashSet<>();

        mapReferences(unMappedMethodsAndFields, ctParentClass, unMappedReferencesInParent, mappedMethodsAndFields);
        if (!mappedMethodsAndFields.isEmpty()) {
            Map mappedClasses = mappedMethods.getOrDefault(node, new HashMap<>());
            mappedClasses.put(className, mappedMethodsAndFields);
            mappedMethods.put(node, mappedClasses);
        }
        return unMappedReferencesInParent;
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
     * @param callSites       all call sites which needs to be filtered
     * @param internalClasses internal classes to be filtered out
     */
    private void excludeInternalCallSites(Map<String, Set<String>> callSites, Set<String> internalClasses) {
        if (callSites != null) {
            Iterator<String> iterator = callSites.keySet().iterator();

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
    private Map<String, Set<String>> getCallSitesToVerify(File classesDirectory) throws IOException, NotFoundException, BadBytecode {
        Map<String, Set<String>> classesAndCallSites = new HashMap<>();
        if (classesDirectory.exists()) {
            File[] classDirectories = classesDirectory.listFiles(File::isDirectory);
            for (File projectClassesDir : classDirectories) {
                if (!META_INF_FILE.equals(projectClassesDir.getName())) {
                    List<File> classFiles = Files.walk(projectClassesDir.toPath())
                            .map(Path::toFile)
                            .filter(f -> f.getName().endsWith(".class"))
                            .toList();
                    for (File classFile : classFiles) {
                        Map<String, Set<String>> extractedCallSites = ReferenceFinder.extractReferences(classFile.getAbsolutePath());
                        for (Map.Entry<String, Set<String>> entry : extractedCallSites.entrySet()) {
                            String key = entry.getKey();
                            Set<String> newSet = entry.getValue();

                            classesAndCallSites.merge(
                                    key,
                                    new HashSet<>(newSet),
                                    (existingSet, incomingSet) -> {
                                        existingSet.addAll(incomingSet);
                                        return existingSet;
                                    }
                            );
                        }
                        //  classesAndCallSites.putAll(CallSiteFinder.extractCallSites(classFile.getAbsolutePath()));
                    }
                }
            }
        }
        return classesAndCallSites;
    }

    /**
     * Extracting all java files in the entire repository
     *
     * @param dir directory of the repository (can also be the project folder of not child projects are available)
     * @return Set of java classes in the repository
     * @throws IOException
     */
    private Set<String> findJavaClassesInDirectory(File dir) throws IOException {
        Stream<Path> stream = Files.walk(Paths.get(dir.toURI()));
        Set<String> javaClassesInRepo = stream
                .filter(file -> (!Files.isDirectory(file) && file.toString().endsWith(".java")))
                .map(Path::toAbsolutePath)
                .map(Path::toString)
                .map(filePath -> filePath.replace(".java", ""))
                .map(filePath -> filePath.replace("\\", "/"))
                .collect(Collectors.toSet());
        Set<String> filteredClasses = new HashSet<>();
        for (String javaClassInRepo : javaClassesInRepo) {
            if (javaClassInRepo.contains("src/main/java/")) {
                filteredClasses.add(javaClassInRepo.split("src/main/java/")[1]);
            } else if (javaClassInRepo.contains("src/test/java/")) {
                filteredClasses.add(javaClassInRepo.split("src/test/java/")[1]);
            } else {
                filteredClasses.add(javaClassInRepo);
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
    private Set<String> getDependencyClasses(File dependencyDirectory, Map<Node, Set<String>> dependencyClasses, Graph<Node, DefaultEdge> dependencyTree) throws IOException {
        File[] depFiles = dependencyDirectory.listFiles();
        Set<String> externalFiles = new HashSet<>();
        if (depFiles != null) {
            for (File depFile : depFiles) {
                if (depFile.getName().endsWith(".jar")) {
                    // map the jar file with the dependency name
                    Node rootNode = dependencyTree.vertexSet().iterator().next();
                    BreadthFirstIterator<Node, DefaultEdge> bfsIterator = new BreadthFirstIterator<>(dependencyTree, rootNode);
                    boolean nodeNotFound = true;
                    while (bfsIterator.hasNext() && nodeNotFound) {
                        Node node = bfsIterator.next();
                        if ((node.getClassifier() == null &&
                                depFile.getName().equals(node.getArtifactId() + "-" + node.getVersion() + ".jar")) ||
                                (node.getClassifier() != null && depFile.getName().equals(node.getArtifactId() +
                                        "-" + node.getVersion() + "-" + node.getClassifier() + ".jar"))) {
                            dependencyClasses.put(node, getJavaClassNamesFromCompressedFiles(depFile));
                            nodeNotFound = false;
                        }
                    }
                    if (nodeNotFound) {
                        externalFiles.add(depFile.getName());
                    }
                } else {
                    externalFiles.add(depFile.getName());
                }
            }
        }
        return externalFiles;
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
        boolean projectBuildSuccess = false;
        if (CommandExecutor.executeCommand(String.format("%s clean compile", mvnCmd), projectDir).contains("BUILD SUCCESS")) {
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
