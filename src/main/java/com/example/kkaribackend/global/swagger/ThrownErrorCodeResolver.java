package com.example.kkaribackend.global.swagger;

import com.example.kkaribackend.global.error.exception.ErrorProperty;
import com.example.kkaribackend.global.error.exception.KkariException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.asm.ClassReader;
import org.springframework.asm.ClassVisitor;
import org.springframework.asm.Handle;
import org.springframework.asm.MethodVisitor;
import org.springframework.asm.Opcodes;
import org.springframework.asm.SpringAsmInfo;
import org.springframework.asm.Type;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Component
public class ThrownErrorCodeResolver {
    private static final String BASE_PACKAGE = "com/example/kkaribackend/";
    private static final int MAX_DEPTH = 8;

    private final ClassLoader classLoader = ThrownErrorCodeResolver.class.getClassLoader();
    private final Map<String, byte[]> classBytesCache = new HashMap<>();
    private final Map<String, Optional<Class<?>>> classCache = new HashMap<>();
    private final Map<String, Set<ErrorProperty>> exceptionClassCache = new HashMap<>();

    public Set<ErrorProperty> resolve(Method handlerMethod) {
        Set<ErrorProperty> result = new LinkedHashSet<>();
        Set<String> visited = new HashSet<>();
        Deque<MethodRef> queue = new ArrayDeque<>();

        queue.add(new MethodRef(
            Type.getInternalName(handlerMethod.getDeclaringClass()),
            handlerMethod.getName(),
            Type.getMethodDescriptor(handlerMethod),
            0
        ));

        while (!queue.isEmpty()) {
            MethodRef ref = queue.poll();
            if (ref.depth() > MAX_DEPTH || !visited.add(ref.key())) {
                continue;
            }
            scanMethod(ref, result, queue);
        }

        return result;
    }

    private void scanMethod(MethodRef ref, Set<ErrorProperty> result, Deque<MethodRef> queue) {
        byte[] bytes = readClass(ref.owner());
        if (bytes == null) {
            return;
        }

        new ClassReader(bytes).accept(new ClassVisitor(SpringAsmInfo.ASM_VERSION) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                if (!name.equals(ref.name()) || !descriptor.equals(ref.descriptor())) {
                    return null;
                }
                return new CollectingMethodVisitor(ref, result, queue);
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    }

    private class CollectingMethodVisitor extends MethodVisitor {
        private final MethodRef current;
        private final Set<ErrorProperty> result;
        private final Deque<MethodRef> queue;

        CollectingMethodVisitor(MethodRef current, Set<ErrorProperty> result, Deque<MethodRef> queue) {
            super(SpringAsmInfo.ASM_VERSION);
            this.current = current;
            this.result = result;
            this.queue = queue;
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
            if (opcode == Opcodes.GETSTATIC) {
                addErrorProperty(owner, name, result);
            }
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
            if (opcode == Opcodes.NEW && isKkariException(type)) {
                result.addAll(errorPropertiesOfExceptionClass(type));
            }
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            if (isProjectClass(owner) && !isKkariException(owner)) {
                queue.add(new MethodRef(owner, name, descriptor, current.depth() + 1));
            }
        }

        @Override
        public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle,
                                           Object... bootstrapMethodArguments) {
            for (Object arg : bootstrapMethodArguments) {
                if (!(arg instanceof Handle handle) || !isProjectClass(handle.getOwner())) {
                    continue;
                }
                if (isKkariException(handle.getOwner())) {
                    result.addAll(errorPropertiesOfExceptionClass(handle.getOwner()));
                } else {
                    queue.add(new MethodRef(handle.getOwner(), handle.getName(), handle.getDesc(),
                        current.depth() + 1));
                }
            }
        }
    }

    private Set<ErrorProperty> errorPropertiesOfExceptionClass(String internalName) {
        return exceptionClassCache.computeIfAbsent(internalName, owner -> {
            Set<ErrorProperty> properties = new LinkedHashSet<>();
            byte[] bytes = readClass(owner);
            if (bytes == null) {
                return properties;
            }
            new ClassReader(bytes).accept(new ClassVisitor(SpringAsmInfo.ASM_VERSION) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    return new MethodVisitor(SpringAsmInfo.ASM_VERSION) {
                        @Override
                        public void visitFieldInsn(int opcode, String fOwner, String fName, String fDesc) {
                            if (opcode == Opcodes.GETSTATIC) {
                                addErrorProperty(fOwner, fName, properties);
                            }
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return properties;
        });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void addErrorProperty(String owner, String name, Set<ErrorProperty> target) {
        if (!isProjectClass(owner)) {
            return;
        }
        loadClass(owner)
            .filter(clazz -> clazz.isEnum() && ErrorProperty.class.isAssignableFrom(clazz))
            .ifPresent(clazz -> {
                try {
                    target.add((ErrorProperty) Enum.valueOf((Class) clazz, name));
                } catch (IllegalArgumentException ignored) {
                    // $VALUES 같은 enum 내부 필드
                }
            });
    }

    private boolean isProjectClass(String internalName) {
        return internalName.startsWith(BASE_PACKAGE);
    }

    private boolean isKkariException(String internalName) {
        return isProjectClass(internalName) && loadClass(internalName)
            .map(KkariException.class::isAssignableFrom)
            .orElse(false);
    }

    private Optional<Class<?>> loadClass(String internalName) {
        return classCache.computeIfAbsent(internalName, name -> {
            try {
                return Optional.of(Class.forName(name.replace('/', '.'), false, classLoader));
            } catch (ClassNotFoundException | LinkageError e) {
                return Optional.empty();
            }
        });
    }

    private byte[] readClass(String internalName) {
        return classBytesCache.computeIfAbsent(internalName, name -> {
            try (InputStream in = classLoader.getResourceAsStream(name + ".class")) {
                return in == null ? null : in.readAllBytes();
            } catch (IOException e) {
                log.warn("클래스 바이트코드를 읽지 못했습니다: {}", name, e);
                return null;
            }
        });
    }

    private record MethodRef(String owner, String name, String descriptor, int depth) {
        String key() {
            return owner + "." + name + descriptor;
        }
    }
}
