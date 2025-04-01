package org.callsite;

import javassist.*;
import javassist.expr.*;
import org.dep.analyzer.GraphAnalyzer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class CallSiteFinder2 {

    static Map<String, Set<String>> methodsInvoked = new HashMap<>();
    static Map<String, Set<String>> fieldsUsed = new HashMap<>();
    static Set<String> classesUsed = new HashSet<>();
    private static final Logger logger = LoggerFactory.getLogger(CallSiteFinder2.class);

    public static void main(String[] args) throws NotFoundException, CannotCompileException {

        ClassPool pool = ClassPool.getDefault();

        pool.insertClassPath("D:\\PhD\\workspace\\Dependency-Audit-New\\dependency-audit\\DependencyGraph\\target\\classes");
        CtClass ctClass = pool.getCtClass("org.dep.analyzer.GraphAnalyzer");

        // Get superclass
        if (ctClass.getSuperclass() != null) {
            classesUsed.add(ctClass.getSuperclass().getName());
        }

        // Get interfaces
        CtClass[] interfaces = ctClass.getInterfaces();
        classesUsed.addAll(Arrays.stream(interfaces).map(CtClass::getName).collect(Collectors.toSet()));


        // Get fields and their types
        for (CtField field : ctClass.getDeclaredFields()) {
            classesUsed.add(field.getType().getName());
            //System.out.println("Field: " + field.getName() + " - Type: " + field.getType().getName());
        }

        CtMethod[] methods = ctClass.getDeclaredMethods();
        for (int i = 0; i < methods.length; i++) {
            String methodName = methods[i].getLongName();
            // Get the return types
            classesUsed.add(methods[i].getReturnType().getName());
            // Get the parameter types
            classesUsed.addAll(Arrays.stream(methods[i].getParameterTypes()).map(CtClass::getName).collect(Collectors.toSet()));
            // Get exception types
            classesUsed.addAll(Arrays.stream(methods[i].getExceptionTypes()).map(CtClass::getName).collect(Collectors.toSet()));

            // Get annotations
            classesUsed.addAll(Arrays.stream(methods[i].getAvailableAnnotations()).map(Object::toString).collect(Collectors.toSet()));

            methods[i].instrument(new ExprEditor() {
                public void edit(MethodCall m) throws CannotCompileException {
                    methodsInvoked
                            .getOrDefault(m.getClassName(), methodsInvoked.put(m.getClassName(), new HashSet<>()))
                            .add(m.getMethodName() + "::" + m.getSignature());
                }
                public void edit(ConstructorCall c) throws CannotCompileException {
                    try {
                        classesUsed.add(c.getMethod().getDeclaringClass().getName());
                    } catch (NotFoundException e) {
                        logger.warn("Exception while extracting constructor invoked in method " + methodName);
                    }
                }

                public void edit(FieldAccess f) throws CannotCompileException {
                    fieldsUsed
                            .getOrDefault(f.getClassName(), fieldsUsed.put(f.getClassName(), new HashSet<>()))
                            .add(f.getFieldName());
                }

                public void edit(Instanceof i) throws CannotCompileException {
                    try {
                        classesUsed.add(i.getType().getName());
                    } catch (NotFoundException e) {
                        logger.warn("Exception while extracting a instance used in method " + methodName);
                    }
                }

                public void edit(Cast c) throws CannotCompileException {
                    try {
                        classesUsed.add(c.getType().getName());
                    } catch (NotFoundException e) {
                        logger.warn("Exception while extracting a cast type used in method " + methodName);
                    }
                }
            });

        }
        methodsInvoked.forEach((key, value) -> {
            System.out.println(key + ": ");
            value.forEach(System.out::println);
        });

        classesUsed.forEach(System.out::println);
    }
}
