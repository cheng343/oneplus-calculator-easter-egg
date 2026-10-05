package io.github.cheng343.calculator.easteregg;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

final class Reflect {
    private Reflect() {}

    static Method method(Class<?> owner, String name, Class<?>... arguments)
            throws NoSuchMethodException {
        for (Class<?> cursor = owner; cursor != null; cursor = cursor.getSuperclass()) {
            try {
                Method method = cursor.getDeclaredMethod(name, arguments);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                // Lifecycle methods can be inherited from Fragment.
            }
        }
        throw new NoSuchMethodException(owner.getName() + "." + name);
    }

    static Object call(Object target, String name, Class<?>[] types, Object... arguments)
            throws ReflectiveOperationException {
        return method(target.getClass(), name, types).invoke(target, arguments);
    }

    static Object call(Object target, String name) throws ReflectiveOperationException {
        return call(target, name, new Class<?>[0]);
    }

    static Object field(Object target, String name) throws ReflectiveOperationException {
        for (Class<?> cursor = target.getClass(); cursor != null; cursor = cursor.getSuperclass()) {
            try {
                Field field = cursor.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                // JADX renames are not used as DEX field names.
            }
        }
        throw new NoSuchFieldException(name);
    }
}
