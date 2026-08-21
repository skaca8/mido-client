package io.github.hyunjun.mido.aop;

import lombok.extern.slf4j.Slf4j;
import org.springframework.asm.ClassReader;
import org.springframework.asm.ClassVisitor;
import org.springframework.asm.MethodVisitor;
import org.springframework.asm.Opcodes;
import org.springframework.asm.SpringAsmInfo;
import org.springframework.asm.Type;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Internal. Finds calls to a {@code @ChannelAction} method from an unannotated method of the same
 * class — the self-invocation that silently bypasses the Spring proxy.
 *
 * <p>Uses the ASM bundled inside spring-core rather than adding an ASM dependency that consumers
 * would inherit. That package is Spring-internal, so every failure path here degrades to "no
 * findings" instead of breaking startup: a missing class file, an unsupported class-file version, or
 * any ASM incompatibility results in an empty result and a debug log, never an exception.
 *
 * <p>The receiver is not proven to be {@code this}: {@code other.annotatedMethod()} where
 * {@code other} is another instance of the same class produces the same bytecode as
 * {@code this.annotatedMethod()}. That is why callers treat the result as a warning.
 */
@Slf4j
final class SelfInvocationScanner {

    private SelfInvocationScanner() {
    }

    /**
     * @param caller name of the unannotated method containing the call
     * @param callee name of the {@code @ChannelAction} method being called
     */
    record Finding(String caller, String callee) {
    }

    static List<Finding> scan(Class<?> beanType, List<Method> actionMethods) {
        if (actionMethods.isEmpty()) return List.of();

        Set<String> actionKeys = new HashSet<>();
        actionMethods.forEach(method -> actionKeys.add(key(method.getName(), Type.getMethodDescriptor(method))));

        try (InputStream classFile = openClassFile(beanType)) {
            if (classFile == null) return List.of();

            List<Finding> findings = new ArrayList<>();
            new ClassReader(classFile).accept(
                    new SelfInvocationVisitor(Type.getInternalName(beanType), actionKeys, findings),
                    ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return findings;
        } catch (Exception | LinkageError e) {
            log.debug("Skipping self-invocation scan of {}: {}", beanType.getName(), e.toString());
            return List.of();
        }
    }

    private static InputStream openClassFile(Class<?> beanType) {
        return beanType.getResourceAsStream("/" + Type.getInternalName(beanType) + ".class");
    }

    private static String key(String name, String descriptor) {
        return name + descriptor;
    }

    private static final class SelfInvocationVisitor extends ClassVisitor {

        private final String internalName;
        private final Set<String> actionKeys;
        private final List<Finding> findings;

        private SelfInvocationVisitor(String internalName, Set<String> actionKeys, List<Finding> findings) {
            super(SpringAsmInfo.ASM_VERSION);
            this.internalName = internalName;
            this.actionKeys = actionKeys;
            this.findings = findings;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            // 호출하는 쪽도 @ChannelAction이면 컨텍스트가 이미 바인딩되어 있으므로 프록시를 우회해도 문제가 없다.
            if (actionKeys.contains(key(name, descriptor))) return null;

            return new MethodVisitor(SpringAsmInfo.ASM_VERSION) {
                @Override
                public void visitMethodInsn(int opcode, String owner, String calleeName,
                                            String calleeDescriptor, boolean isInterface) {
                    boolean callsOwnActionMethod = (opcode == Opcodes.INVOKEVIRTUAL || opcode == Opcodes.INVOKESPECIAL)
                            && internalName.equals(owner)
                            && actionKeys.contains(key(calleeName, calleeDescriptor));

                    if (callsOwnActionMethod) {
                        findings.add(new Finding(name, calleeName));
                    }
                }
            };
        }
    }
}
