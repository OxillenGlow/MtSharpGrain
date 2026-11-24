package com.mtsharpgrain.js.mainthread;

import org.codehaus.janino.SimpleCompiler;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * One Java compiler and compiled-class registry per mod pack.
 *
 * Java calls are made by that pack's Graal owner thread. The manager also
 * changes the enabled state on that same thread.
 */
public final class JavaCompilerApi implements AutoCloseable {

    private static final AtomicLong NEXT_ID = new AtomicLong(1);

    private static final class CompiledUnit {
        final Class<?> type;
        final ClassLoader loader;
        final SimpleCompiler compiler;

        CompiledUnit(Class<?> type, ClassLoader loader, SimpleCompiler compiler) {
            this.type = type;
            this.loader = loader;
            this.compiler = compiler;
        }
    }

    private record Conversion(Object value, int score) {}
    private record ConvertedArguments(Object[] values, int score) {}

    private final Map<Long, CompiledUnit> compiled = new HashMap<>();
    private boolean enabled;

    public JavaCompilerApi(boolean enabled) {
        this.enabled = enabled;
    }

    /** Called by the mod manager on this pack's owner thread. */
    public synchronized void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) compiled.clear();
    }

    public synchronized boolean isEnabled() {
        return enabled;
    }

    /**
     * Compiles one complete Java source file, including its package declaration.
     * className must be the fully-qualified name of the public class to load.
     *
     * Example:
     * Java.compile("package example; public class Helper { public static int add(int a, int b) { return a+b; } }",
     *              "example.Helper")
     */
    @HostAccess.Export
    public synchronized Long compile(String source, String className) {
        if (!enabled) return null;
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("Java source must not be empty");
        }
        if (className == null || className.isBlank()) {
            throw new IllegalArgumentException("className must be a fully-qualified class name");
        }

        try {
            SimpleCompiler compiler = new SimpleCompiler();
            ClassLoader parent = Thread.currentThread().getContextClassLoader();
            compiler.setParentClassLoader(parent != null ? parent : JavaCompilerApi.class.getClassLoader());
            compiler.cook(className.replace('.', '/') + ".java", new StringReader(source));

            ClassLoader loader = compiler.getClassLoader();
            Class<?> type = loader.loadClass(className);
            long id = NEXT_ID.getAndIncrement();
            compiled.put(id, new CompiledUnit(type, loader, compiler));
            return id;
        } catch (Exception error) {
            throw new IllegalArgumentException(
                    "Could not compile " + className + ": " + error.getMessage(), error);
        }
    }

    /**
     * Invokes a public static method. Pass JS arguments as an array:
     * Java.call(id, "add", [2, 3]).
     *
     * Supported JS argument types: null, string, boolean, and numbers that fit
     * int, long, double, or float parameters. Ambiguous overloads are rejected.
     */
    @HostAccess.Export
    public synchronized Object call(long id, String methodName, Value arguments) {
        if (!enabled) return null;

        CompiledUnit unit = compiled.get(id);
        if (unit == null) throw new IllegalArgumentException("Unknown or freed Java class handle: " + id);
        if (methodName == null || methodName.isBlank()) {
            throw new IllegalArgumentException("Method name must not be empty");
        }

        int argumentCount;
        if (arguments == null || arguments.isNull()) {
            argumentCount = 0;
        } else {
            if (!arguments.hasArrayElements()) {
                throw new IllegalArgumentException("Arguments must be a JavaScript array");
            }
            long size = arguments.getArraySize();
            if (size > Integer.MAX_VALUE) throw new IllegalArgumentException("Too many arguments");
            argumentCount = (int) size;
        }

        Method selected = null;
        Object[] selectedArguments = null;
        int selectedScore = Integer.MAX_VALUE;
        boolean ambiguous = false;

        for (Method method : unit.type.getMethods()) {
            int modifiers = method.getModifiers();
            if (!method.getName().equals(methodName)
                    || !Modifier.isPublic(modifiers)
                    || !Modifier.isStatic(modifiers)
                    || method.isVarArgs()
                    || method.getDeclaringClass() != unit.type
                    || method.getParameterCount() != argumentCount) {
                continue;
            }

            ConvertedArguments converted = convertArguments(method, arguments, argumentCount);
            if (converted == null) continue;

            if (converted.score() < selectedScore) {
                selected = method;
                selectedArguments = converted.values();
                selectedScore = converted.score();
                ambiguous = false;
            } else if (converted.score() == selectedScore) {
                ambiguous = true;
            }
        }

        if (selected == null) {
            throw new IllegalArgumentException("No compatible public static method "
                    + methodName + " with " + argumentCount + " argument(s) in " + unit.type.getName());
        }
        if (ambiguous) {
            throw new IllegalArgumentException("Ambiguous overload for " + methodName
                    + "; use a method name with an unambiguous parameter signature");
        }

        try {
            return selected.invoke(null, selectedArguments);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause() == null ? error : error.getCause();
            throw new IllegalStateException("Java method " + methodName + " failed: "
                    + cause.getMessage(), cause);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Could not invoke Java method " + methodName, error);
        }
    }

    /** Frees one compiled class handle. Returns null while Java is disabled. */
    @HostAccess.Export
    public synchronized Boolean free(long id) {
        if (!enabled) return null;
        return compiled.remove(id) != null;
    }

    private static ConvertedArguments convertArguments(
            Method method, Value arguments, int argumentCount) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        Object[] converted = new Object[argumentCount];
        int score = 0;

        for (int i = 0; i < argumentCount; i++) {
            Conversion conversion = convert(arguments.getArrayElement(i), parameterTypes[i]);
            if (conversion == null) return null;
            converted[i] = conversion.value();
            score += conversion.score();
        }
        return new ConvertedArguments(converted, score);
    }

    private static Conversion convert(Value value, Class<?> target) {
        if (value == null || value.isNull()) {
            return target.isPrimitive() ? null : new Conversion(null, 0);
        }

        if (target == String.class && value.isString()) {
            return new Conversion(value.asString(), 0);
        }
        if ((target == boolean.class || target == Boolean.class) && value.isBoolean()) {
            return new Conversion(value.asBoolean(), 0);
        }
        if (!value.isNumber()) return null;

        if ((target == int.class || target == Integer.class) && value.fitsInInt()) {
            return new Conversion(value.asInt(), 0);
        }
        if ((target == long.class || target == Long.class) && value.fitsInLong()) {
            return new Conversion(value.asLong(), 1);
        }
        if (target == double.class || target == Double.class) {
            return new Conversion(value.asDouble(), 2);
        }
        if (target == float.class || target == Float.class) {
            double number = value.asDouble();
            if (Double.isFinite(number) && number >= -Float.MAX_VALUE && number <= Float.MAX_VALUE) {
                return new Conversion((float) number, 3);
            }
        }
        return null;
    }

    /** Releases all class-loader references; compiled classes can then be unloaded. */
    @Override
    public synchronized void close() {
        enabled = false;
        compiled.clear();
    }
}
