package org.reference;

import javassist.NotFoundException;
import javassist.bytecode.*;
import javassist.bytecode.annotation.Annotation;

import org.dep.model.Reference;

import java.io.*;
import java.util.*;

public class ReferenceFinder {

    public static void main(String[] args) throws IOException, BadBytecode {

        String className = "D:\\PhD\\workspace\\Dependency-Audit-New\\dependency-audit\\DependencyGraph\\target\\classes\\org\\callsite\\ClassLevel.class";
        BufferedInputStream fin
                = new BufferedInputStream(new FileInputStream(className));
        ClassFile cf = new ClassFile(new DataInputStream(fin));
        Map<String, Set<String>> methodCalls = new HashMap<>();
        Map<String, Set<String>> fieldReferences = new HashMap<>();
        detectReferences(cf);
    }

    public static Map<String, Set<Reference>> extractReferences(String classFileLocation) throws IOException, BadBytecode {
        BufferedInputStream fin
                = new BufferedInputStream(new FileInputStream(classFileLocation));
        ClassFile cf = new ClassFile(new DataInputStream(fin));
        Map<String, Set<String>> methodCalls = new HashMap<>();
        Map<String, Set<String>> fieldReferences = new HashMap<>();
        return detectReferences(cf);
    }

    /**
     * Extract all call sites of a class
     *
     * @param cf              The ClassFile object of the analyzed class
     * @param methodCalls     collection to store all the class level invocations with its methods
     * @param fieldReferences collection to store all the field level references
     * @throws IOException
     * @throws NotFoundException
     */
//    public static void detectReferences(ClassFile cf, Map<String, Set<String>> methodCalls, Map<String, Set<String>> fieldReferences) throws BadBytecode {
//        // Map<String, Set<Reference>> classReferences = new HashMap<>();
//        ConstPool constPool = cf.getConstPool();
//        // Extract class annotations
//        extractAnnotations1(cf.getAttributes(), methodCalls);
//        // Extract method annotations
//        for (MethodInfo method : cf.getMethods()) {
//
//            SignatureAttribute sigAttr = (SignatureAttribute) method.getAttribute(SignatureAttribute.tag);
//            if (sigAttr != null) {
//                String signature = sigAttr.getSignature();
//                SignatureAttribute.MethodSignature parsedSig =
//                        SignatureAttribute.toMethodSignature(signature);
//                extractReturnAndParamTypes1(parsedSig.getReturnType(), methodCalls);
//
//                // Parameter types
//                for (SignatureAttribute.Type paramType : parsedSig.getParameterTypes()) {
//                    extractReturnAndParamTypes1(paramType, methodCalls);
//                }
//            }
//            extractAnnotations1(method.getAttributes(), methodCalls);
//        }
//        // Extract field annotations
//        for (FieldInfo fieldInfo : cf.getFields()) {
//            extractAnnotations1(fieldInfo.getAttributes(), methodCalls);
//        }
//        // Extract primitive fields TODO: not sure if we need this as they are inbuilt Java variable types
//        extractPrimitiveAndStringFields1(cf.getFields(), methodCalls);
//        // Extract method calls, field references, and class references
//        for (int i = 1; i < constPool.getSize(); i++) {
//            switch (constPool.getTag(i)) {
//                case ConstPool.CONST_Methodref:
//                case ConstPool.CONST_InterfaceMethodref:
//                    methodCalls
//                            .computeIfAbsent(constPool.getMethodrefClassName(i), k -> new HashSet<>())
//                            .add(constPool.getMethodrefName(i) + constPool.getMethodrefType(i));
//                    break;
//                case ConstPool.CONST_Fieldref:
//                    fieldReferences
//                            .computeIfAbsent(constPool.getFieldrefClassName(i), k -> new HashSet<>())
//                            .add(constPool.getFieldrefName(i));
//                    break;
//                case ConstPool.CONST_Class:
//                    if (!methodCalls.containsKey(constPool.getClassInfo(i))) {
//                        methodCalls.put(constPool.getClassInfo(i), new HashSet<>());
//                    }
//                    break;
//            }
//        }
//    }

    /**
     * Extract the parameter types and the return types for the method signature
     *
     * @param type            the parameter or return type value
     * @param classReferences collection to store all the type variables
     */
    private static void extractReturnAndParamTypes(SignatureAttribute.Type type, Map<String, Set<Reference>> classReferences) {
        if (type instanceof SignatureAttribute.ClassType classType) {
            if (!classReferences.containsKey(classType.getName())) {
                classReferences.computeIfAbsent(classType.getName(), k -> new HashSet<>());
            }
            SignatureAttribute.TypeArgument[] args = classType.getTypeArguments();
            if (args != null) {
                for (SignatureAttribute.TypeArgument arg : args) {
                    if (arg.getType() != null) {
                        extractReturnAndParamTypes(arg.getType(), classReferences);
                    }
                }
            }
        } else if (type instanceof SignatureAttribute.ArrayType) {
            extractReturnAndParamTypes(((SignatureAttribute.ArrayType) type).getComponentType(), classReferences);
        }
    }

    /**
     * Extract all references of a class
     *
     * @param cf              The ClassFile object of the analyzed class
     *
     * @throws IOException
     * @throws NotFoundException
     */
    public static Map<String, Set<Reference>> detectReferences(ClassFile cf) throws BadBytecode {

        Map<String, Set<Reference>> classReferences = new HashMap<>();
        ConstPool constPool = cf.getConstPool();
        extractSuperTypes(cf, classReferences);

        // Extract class annotations
        extractAnnotations(cf.getAttributes(), classReferences);
        for (MethodInfo method : cf.getMethods()) {

            extractMethodSignatureAndDescriptor(classReferences, method);

            //TODO have to check for parameter and return type annotations

            // extract method annotations
            extractAnnotations(method.getAttributes(), classReferences);

            // extract exceptions
            ExceptionsAttribute exceptionsAttr = method.getExceptionsAttribute();
            if (exceptionsAttr != null) {
                for (String thrownExceptions : exceptionsAttr.getExceptions()) {
                    classReferences.computeIfAbsent(thrownExceptions, k -> new HashSet<>());
                }
            }
            CodeAttribute codeAttr = method.getCodeAttribute();
            if (codeAttr == null) continue;

            CodeIterator ci = codeAttr.iterator();
            while (ci.hasNext()) {
                methodAndLocalInvocations(classReferences, constPool, ci);
            }
            // Local variable declarations — get types used in locals
            LocalVariableAttribute lvt = (LocalVariableAttribute) codeAttr.getAttribute(LocalVariableAttribute.tag);
            if (lvt != null) {
                for (int i = 0; i < lvt.tableLength(); i++) {
                    String desc = lvt.descriptor(i);
                    if (desc != null) {
                        decodeFieldTypeDescriptor(desc, classReferences);
                    }
                }
            }
            // caught exceptions
            ExceptionTable exceptionTable = codeAttr.getExceptionTable();
            for (int i = 0; i < exceptionTable.size(); i++) {
                int catchTypeIndex = exceptionTable.catchType(i);
                if (catchTypeIndex != 0) {  // 0 means "finally" block
                    String exType = constPool.getClassInfo(catchTypeIndex).replace('/', '.');
                    classReferences
                            .computeIfAbsent(exType, k -> new HashSet<>());
                }
            }
        }
        // extract field annotations
        for (FieldInfo fieldInfo : cf.getFields()) {
            extractAnnotations(fieldInfo.getAttributes(), classReferences);
            decodeFieldTypeDescriptor(fieldInfo.getDescriptor(), classReferences);
        }
        return classReferences;
    }

    private static void methodAndLocalInvocations(Map<String, Set<Reference>> classReferences, ConstPool constPool, CodeIterator ci) throws BadBytecode {
        int index = ci.next();
        int opcode = ci.byteAt(index);
        String invocationType = OpcodeMapper.getMethodOpcodeInstruction(opcode);

        String className = null;
        String methodName = null;

        if (invocationType != null) {
            int methodIndex = ci.u16bitAt(index + 1);
            className = (opcode == Opcode.INVOKEINTERFACE)
                    ? constPool.getInterfaceMethodrefClassName(methodIndex)
                    : constPool.getMethodrefClassName(methodIndex);
            methodName = (opcode == Opcode.INVOKEINTERFACE)
                    ? constPool.getInterfaceMethodrefName(methodIndex)
                    : constPool.getMethodrefName(methodIndex);
            String methodSignature = (opcode == Opcode.INVOKEINTERFACE)
                    ? constPool.getInterfaceMethodrefType(methodIndex)
                    : constPool.getMethodrefType(methodIndex);

            Reference ref = new Reference(methodName + methodSignature, invocationType);
            classReferences
                    .computeIfAbsent(className, k -> new HashSet<>())
                    .add(ref);
        } else {
            invocationType = OpcodeMapper.getFieldOpcodeInstruction(opcode);
            if (invocationType != null) {
                // Detect field access
                int fieldIndex = ci.u16bitAt(index + 1);
                String fieldClass = constPool.getFieldrefClassName(fieldIndex);
                String fieldName = constPool.getFieldrefName(fieldIndex);
                Reference ref = new Reference(fieldName, invocationType);
                classReferences
                        .computeIfAbsent(fieldClass, k -> new HashSet<>())
                        .add(ref);
            }
            // include cast types
            if (opcode == Opcode.CHECKCAST) {
                int classIndex = ci.u16bitAt(index + 1);
                classReferences
                        .computeIfAbsent(constPool.getClassInfo(classIndex), k -> new HashSet<>());
            }
        }
    }

    private static void extractSuperTypes(ClassFile cf, Map<String, Set<Reference>> classReferences) {
        classReferences.computeIfAbsent(cf.getSuperclass(), k -> new HashSet<>());
        for (String classInterface: cf.getInterfaces()) {

            classReferences.computeIfAbsent(classInterface, k -> new HashSet<>());
        }
    }

    private static void decodeFieldTypeDescriptor(String desc, Map<String, Set<Reference>> classReferences) {
        if (desc.startsWith("[")) {
            // Array type
            int dim = desc.lastIndexOf('[') + 1;
            String base = desc.substring(dim);
            if (base.startsWith("L") && base.endsWith(";")) {
                classReferences
                        .computeIfAbsent(base.replace('/', '.').replace("L", "").replace(";", ""), k -> new HashSet<>());
//            } else {
//                // Primitive array
//                results.add(desc); // optionally convert I -> int[] etc.
            }
        } else if (desc.startsWith("L")) {
            classReferences.computeIfAbsent(desc.substring(1, desc.length() - 1).replace('/', '.'), k -> new HashSet<>());
        }
    }

    private static void extractMethodSignatureAndDescriptor(Map<String, Set<Reference>> classReferences, MethodInfo method) throws BadBytecode {
        // get generic types of parameters
        SignatureAttribute sigAttr = (SignatureAttribute) method.getAttribute(SignatureAttribute.tag);
        if (sigAttr != null) {
            String signature = sigAttr.getSignature();
            SignatureAttribute.MethodSignature parsedSig = SignatureAttribute.toMethodSignature(signature);
            extractReturnAndParamTypes(parsedSig.getReturnType(), classReferences);
            //Parameter types
            for (SignatureAttribute.Type paramType : parsedSig.getParameterTypes()) {
                extractReturnAndParamTypes(paramType, classReferences);
            }
        } else {
            String desc = method.getDescriptor();  // always present
            SignatureAttribute.MethodSignature parsedSig = SignatureAttribute.toMethodSignature(desc);
            extractReturnAndParamTypes(parsedSig.getReturnType(), classReferences);
            //Parameter types
            for (SignatureAttribute.Type paramType : parsedSig.getParameterTypes()) {
                extractReturnAndParamTypes(paramType, classReferences);
            }
        }
    }

    /**
     * Extract all the attributes at the class, field and method level
     *
     * @param attributes      list of attributes of either the class, fields or methods
     * @param classReferences collection to store all the class level invocations
     */
    private static void extractAnnotations(List<AttributeInfo> attributes, Map<String, Set<Reference>> classReferences) {
        for (AttributeInfo attr : attributes) {
            if (attr instanceof AnnotationsAttribute) {
                AnnotationsAttribute annAttr = (AnnotationsAttribute) attr;
                for (Annotation annotation : annAttr.getAnnotations()) {
                    classReferences.computeIfAbsent(annotation.getTypeName(), k -> new HashSet<>());
                }
            }
        }
    }

    /**
     * Extract the primitive variable of the class
     *
     * @param fields          List of all fields of the class
     * @param classReferences collection to store all the field types extracted from the class
     */
    private static void extractPrimitiveAndStringFields(List<FieldInfo> fields, Map<String, Set<Reference>> classReferences) {
        for (FieldInfo field : fields) {
            String fieldName = field.getName();
            String descriptor = field.getDescriptor();
            String type = decodeDescriptor(descriptor);
            if (isPrimitiveOrString(type)) {
                classReferences
                        .computeIfAbsent(type, k -> new HashSet<>())
                        .add(new Reference(fieldName, "Field")); // TODO have to change this
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
