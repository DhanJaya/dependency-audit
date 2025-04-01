package org.callsite;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.bytecode.*;

import java.io.*;
import java.util.HashSet;
import java.util.Set;

public class CallSiteFinder {

    public static void main(String[] args) throws NotFoundException, IOException {
        String className = "D:\\PhD\\workspace\\Dependency-Audit-New\\dependency-audit\\DependencyGraph\\target\\classes\\org\\dep\\analyzer\\GraphAnalyzer.class";
        extractExternalMethods(className);
    }

    public static void extractExternalMethods(String className) throws IOException {
        BufferedInputStream fin
                = new BufferedInputStream(new FileInputStream(className));
        ClassFile cf = new ClassFile(new DataInputStream(fin));
        ConstPool constPool = cf.getConstPool();

        Set<String> methodCalls = new HashSet<>();
        Set<String> fieldReferences = new HashSet<>();
        Set<String> classReferences = new HashSet<>();
        Set<String> invokedDynamicMethods = new HashSet<>();
        Set<String> interfaces = new HashSet<>();
        Set<String> annotations = new HashSet<>();

        // Extract method calls, field references, and class references
        for (int i = 1; i < constPool.getSize(); i++) {
            switch (constPool.getTag(i)) {
                case ConstPool.CONST_Methodref:
                case ConstPool.CONST_InterfaceMethodref:
                    methodCalls.add(constPool.getMethodrefClassName(i) + "." + constPool.getMethodrefName(i) + "." +constPool.getMethodrefType(i));
                    constPool.getMethodrefType(i);
                    break;

                case ConstPool.CONST_Fieldref:
                    fieldReferences.add(constPool.getFieldrefClassName(i) + "." + constPool.getFieldrefName(i));
                    break;

                case ConstPool.CONST_Class:
                    classReferences.add(constPool.getClassInfo(i));
                    break;
            }
        }
        // Extract interfaces implemented
//        for (CtClass intf : ctClass.getInterfaces()) {
//            interfaces.add(intf.getName());
//        }
//
//        // Extract annotations used
//        Object[] annotationsArray = ctClass.getAvailableAnnotations();
//        for (Object annotation : annotationsArray) {
//            annotations.add(annotation.toString());
//        }


    }
}
