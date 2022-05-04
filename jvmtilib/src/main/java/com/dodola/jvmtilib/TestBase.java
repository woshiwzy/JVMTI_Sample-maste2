package com.dodola.jvmtilib;

import java.util.*;
import java.util.stream.Collectors;

public abstract class TestBase {

    private static final int DEFAULT_LIMIT = 6000;
    private static Map<Integer, String> referenceDescription = new HashMap<>();

    static {
        referenceDescription.put(1, "CLASS");
        referenceDescription.put(2, "FIELD");
        referenceDescription.put(3, "ARRAY_ELEMENT");
        referenceDescription.put(4, "CLASS_LOADER");
        referenceDescription.put(5, "SIGNERS");
        referenceDescription.put(6, "PROTECTION_DOMAIN");
        referenceDescription.put(7, "INTERFACE");
        referenceDescription.put(8, "STATIC_FIELD");
        referenceDescription.put(9, "CONSTANT_POOL");
        referenceDescription.put(10, "SUPERCLASS");
        referenceDescription.put(21, "JNI_GLOBAL");
        referenceDescription.put(22, "SYSTEM_CLASS");
        referenceDescription.put(23, "MONITOR");
        referenceDescription.put(24, "STACK_LOCAL");
        referenceDescription.put(25, "JNI_LOCAL");
        referenceDescription.put(26, "THREAD");
        referenceDescription.put(27, "OTHER");
    }

    protected static void printSize(Object object) {
        // 没有调用
    }


    private static int indexOfReference(Object[] array, Object value) {
        int result = -1;
        for (int i = 0; i < array.length; i++) {
            if (array[i] == value) {
                if (result != -1) {
                    fail("Object " + value.toString() + " appeared twice in the objects list");
                }
                result = i;
            }
        }

        return result;
    }

    public static void printGcRoots(Object object) {
        printGcRoots(object, DEFAULT_LIMIT);
    }

    protected static void printGcRoots(Object result, int limit) {
        Object[] arrayResult = (Object[]) result;   // result 为object 数组
        Object[] objects = (Object[]) arrayResult[0];   // 取数组第0个元素
        Object[] links = (Object[]) arrayResult[1];     // 取数组第1个元素
        Object[] sortedObjects = Arrays.stream(objects).sorted(Comparator.comparing(TestBase::asString)).toArray();
        int[] oldIndexToNewIndex = new int[objects.length];
        int[] newIndexToOldIndex = new int[objects.length];
        for (int i = 0; i < sortedObjects.length; i++) {
            int old = indexOfReference(objects, sortedObjects[i]);
            oldIndexToNewIndex[old] = i;
            newIndexToOldIndex[i] = old;

        }
        for (int i = 0; i < sortedObjects.length; i++) {
            Object obj = sortedObjects[i];
            Object[] objLinks = (Object[]) links[newIndexToOldIndex[i]];
            int[] indices = (int[]) objLinks[0];
            int[] kinds = (int[]) objLinks[1];
            Object[] infos = (Object[]) objLinks[2];
            int[] moreLinks = (int[]) objLinks[3];

            String referencedFrom = buildReferencesString(indices, kinds, infos, oldIndexToNewIndex, moreLinks[0]);

            String dist = asString(obj);

            if (dist != null && dist.contains("com.dodola.jvmti")) {
                System.out.println(i + ": " + dist + " <- " + referencedFrom);
            }

        }
    }

    private static String interpretInfo(int kind, Object info) {
        if (kind == 2 || kind == 8 // field or static field
                || kind == 3 // array element
                || kind == 9) { // constant pool element
            return "index = " + ((int[]) info)[0];
        }

        if (kind == 24 || kind == 25) { // stack local (jni)
            Object[] infos = (Object[]) info;
            assertEquals(2, infos.length);
            long[] stackInfo = (long[]) infos[0];
            return "thread id = " + stackInfo[0] + " depth = " + stackInfo[1] + " slot = " + stackInfo[2];
        }

        return "no details";
    }

    private static String buildReferencesString(int[] indices, int[] kinds, Object[] infos, int[] indicesMap, int moreLinks) {
        assertEquals(indices.length, kinds.length);
        assertEquals(kinds.length, infos.length);

        List<String> references = new ArrayList<>();
        for (int i = 0; i < indices.length; i++) {
            int index = indices[i];
            String refFrom = index == -1 ? "root" : Integer.toString(indicesMap[index]);
            String kind = referenceDescription.get(kinds[i]);
            references.add(String.format("[%s :: %s :: %s]", refFrom, kind, interpretInfo(kinds[i], infos[i])));
        }

        references.sort(String::compareTo);

        String refs = String.join(", ", references);
        assertTrue(moreLinks >= 0);
        if (moreLinks != 0) {
            refs += "(more " + moreLinks + " found)";
        }

        return refs;
    }

    protected static Object createTestObject(String name) {
        return new Object() {
            @Override
            public String toString() {
                return name;
            }
        };
    }

    protected static Object createTestObject() {
        return createTestObject("test object");
    }

    protected static void fail(String message) {
        throw new AssertionError(message);
    }

    protected static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    protected static void assertTrue(boolean condition) {
        assertTrue(condition, "Expected: true, but actual false");
    }

    protected static void assertEquals(Object expected, Object actual) {
        if (expected == null) {
            if (actual != null) {
                throw new AssertionError("Expected: null, but actual: " + actual.toString());
            }
        } else if (!expected.equals(actual)) {
            throw new AssertionError("Expected: " + expected.toString() + ", but actual: " + Objects.toString(actual));
        }
    }


    private static String asString(Object obj) {
        return asStringImpl(obj, new HashMap<>());
    }

    private static String asStringImpl(Object obj, Map<Object, Integer> visited) {
        if (visited.containsKey(obj)) {
            return "#recursive" + visited.get(obj) + "#";
        }

        visited.put(obj, visited.size());
        if (obj == null) return "null";
        if (obj instanceof Object[]) {
            return Arrays.stream((Object[]) obj).map(x -> asStringImpl(x, visited)).collect(Collectors.joining(", ", "[", "]"));
        }
        return obj.toString();
    }
}