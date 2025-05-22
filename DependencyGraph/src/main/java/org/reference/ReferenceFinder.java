package org.reference;

import javassist.NotFoundException;
import javassist.bytecode.*;
import javassist.bytecode.annotation.Annotation;

import java.io.*;
import java.util.*;

public class ReferenceFinder {

    public static void main(String[] args) throws NotFoundException, IOException, BadBytecode {

        String className = "D:\\PhD\\workspace\\Dependency-Audit-New\\dependency-audit\\DependencyGraph\\target\\classes\\org\\callsite\\ClassLevel.class";
        BufferedInputStream fin
                = new BufferedInputStream(new FileInputStream(className));
        ClassFile cf = new ClassFile(new DataInputStream(fin));
        Map<String, Set<String>> methodCalls = new HashMap<>();
        Map<String, Set<String>> fieldReferences = new HashMap<>();
        detectReferences(cf, methodCalls, fieldReferences);
    }

    public static Map<String, Set<String>> extractReferences(String classFileLocation) throws IOException, BadBytecode{
        BufferedInputStream fin
                = new BufferedInputStream(new FileInputStream(classFileLocation));
        ClassFile cf = new ClassFile(new DataInputStream(fin));
        Map<String, Set<String>> methodCalls = new HashMap<>();
        Map<String, Set<String>> fieldReferences = new HashMap<>();
        detectReferences(cf, methodCalls, fieldReferences);
        methodCalls.putAll(fieldReferences);
        return methodCalls;
    }

    /**
     * Extract all call sites of a class
     *
     * @param cf              The ClassFile object of the analyzed class
     * @param methodCalls     collection to store all the class level invocations with its methods
     * @param fieldReferences collection to store all the field level callsites
     * @throws IOException
     * @throws NotFoundException
     */
    public static void detectReferences(ClassFile cf, Map<String, Set<String>> methodCalls, Map<String, Set<String>> fieldReferences) throws BadBytecode {
        ConstPool constPool = cf.getConstPool();
        // Extract class annotations
        extractAnnotations(cf.getAttributes(), methodCalls);
        // Extract method annotations
        for (MethodInfo method : cf.getMethods()) {

            SignatureAttribute sigAttr = (SignatureAttribute) method.getAttribute(SignatureAttribute.tag);
            if (sigAttr != null) {
                String signature = sigAttr.getSignature();
                SignatureAttribute.MethodSignature parsedSig =
                        SignatureAttribute.toMethodSignature(signature);
                extractReturnAndParamTypes(parsedSig.getReturnType(), methodCalls);

                // Parameter types
                for (SignatureAttribute.Type paramType : parsedSig.getParameterTypes()) {
                    extractReturnAndParamTypes(paramType, methodCalls);
                }
            }
            extractAnnotations(method.getAttributes(), methodCalls);
        }
        // Extract field annotations
        for (FieldInfo fieldInfo : cf.getFields()) {
            extractAnnotations(fieldInfo.getAttributes(), methodCalls);
        }
        // Extract primitive fields TODO: not sure if we need this as they are inbuilt Java variable types
        extractPrimitiveAndStringFields(cf.getFields(), fieldReferences);
        // Extract method calls, field references, and class references
        for (int i = 1; i < constPool.getSize(); i++) {
            switch (constPool.getTag(i)) {
                case ConstPool.CONST_Methodref:
                case ConstPool.CONST_InterfaceMethodref:
                    methodCalls
                            .computeIfAbsent(constPool.getMethodrefClassName(i), k -> new HashSet<>())
                            .add(constPool.getMethodrefName(i) + constPool.getMethodrefType(i));
                    break;
                case ConstPool.CONST_Fieldref:
                    fieldReferences
                            .computeIfAbsent(constPool.getFieldrefClassName(i), k -> new HashSet<>())
                            .add(constPool.getFieldrefName(i));
                    break;
                case ConstPool.CONST_Class:
                    if (!methodCalls.containsKey(constPool.getClassInfo(i))) {
                        methodCalls.put(constPool.getClassInfo(i), new HashSet<>());
                    }
                    break;
            }
        }
    }

    /**
     * Extract the parameter types and the return types for the method signature
     * @param type the parameter or return type value
     * @param methodCalls collection to store all the type variables
     */
    private static void extractReturnAndParamTypes(SignatureAttribute.Type type, Map<String, Set<String>> methodCalls) {
        if (type instanceof SignatureAttribute.ClassType classType) {
            if (!methodCalls.containsKey(classType.getName())) {
                methodCalls.put(classType.getName(), new HashSet<>());
            }
            SignatureAttribute.TypeArgument[] args = classType.getTypeArguments();
            if (args != null) {
                for (SignatureAttribute.TypeArgument arg : args) {
                    if (arg.getType() != null) {
                        extractReturnAndParamTypes(arg.getType(), methodCalls);
                    }
                }
            }
        } else if (type instanceof SignatureAttribute.ArrayType) {
            extractReturnAndParamTypes(((SignatureAttribute.ArrayType) type).getComponentType(), methodCalls);
        }
    }

    /**
     * Extract all the attributes at the class, field and method level
     *
     * @param attributes  list of attributes of either the class, fields or methods
     * @param methodCalls collection to store all the class level invocations
     */
    private static void extractAnnotations(List<AttributeInfo> attributes, Map<String, Set<String>> methodCalls) {
        for (AttributeInfo attr : attributes) {
            if (attr instanceof AnnotationsAttribute) {
                AnnotationsAttribute annAttr = (AnnotationsAttribute) attr;
                for (Annotation annotation : annAttr.getAnnotations()) {
                    methodCalls.put(annotation.getTypeName(), new HashSet<>());
                }
            }
        }
    }

    /**
     * Extract the primitive variable of the class
     *
     * @param fields         List of all fields of the class
     * @param fieldReference collection to store all the field types extracted from the class
     */
    private static void extractPrimitiveAndStringFields(List<FieldInfo> fields, Map<String, Set<String>> fieldReference) {
        for (FieldInfo field : fields) {
            String fieldName = field.getName();
            String descriptor = field.getDescriptor();
            String type = decodeDescriptor(descriptor);
            if (isPrimitiveOrString(type)) {
                fieldReference
                        .computeIfAbsent(type, k -> new HashSet<>())
                        .add(fieldName);
            }
        }
    }

    /**
     * Decode the variable type to a human-readable format
     *
     * @param descriptor the descriptor generated by the JVM
     * @return the decoded descriptor
     */
    private static String decodeDescriptor(String descriptor) {
        switch (descriptor) {
            case "S":
                return "short";
            case "I":
                return "int";
            case "J":
                return "long";
            case "F":
                return "float";
            case "D":
                return "double";
            case "B":
                return "byte";
            case "C":
                return "char";
            case "Z":
                return "boolean";
            case "Ljava/lang/String;":
                return "java.lang.String";
            default:
                if (descriptor.startsWith("L") && descriptor.endsWith(";")) {
                    return descriptor.substring(1, descriptor.length() - 1).replace('/', '.');
                } else {
                    return descriptor; // TODO: need to check for Arrays could have different class types
                }
        }
    }

    /**
     * Verify if the field type is a primitive or string type
     *
     * @param type The field type
     * @return true or false based on the field type
     */
    private static boolean isPrimitiveOrString(String type) {
        return type.equals("byte") ||
                type.equals("short") ||
                type.equals("int") ||
                type.equals("long") ||
                type.equals("float") ||
                type.equals("double") ||
                type.equals("char") ||
                type.equals("boolean") ||
                type.equals("java.lang.String");
    }
}
