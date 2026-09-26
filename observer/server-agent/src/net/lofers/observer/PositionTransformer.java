package net.lofers.observer;

import java.lang.classfile.*;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.instrument.ClassFileTransformer;
import java.lang.reflect.AccessFlag;
import java.security.MessageDigest;
import java.security.ProtectionDomain;
import java.util.HexFormat;

/** An in-memory hook; never rewrites the installed game jar or any Lua file. */
public final class PositionTransformer implements ClassFileTransformer {
    static final String TARGET = "zombie/network/RCONServer";
    // Dedicated Build 42.20.4. Unknown versions fail open for the game, closed for export.
    static final String SHA256 = "c31a83c6868d6c88da96db66db10a3d3414ae39947b13e3d800afbb2c034c645";

    @Override
    public byte[] transform(ClassLoader loader, String name, Class<?> redefining,
                            ProtectionDomain domain, byte[] bytes) {
        if (!TARGET.equals(name) || redefining != null) return null;
        try {
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            if (!SHA256.equals(digest)) {
                PositionAgent.log("Unsupported game class; position exporter disabled.");
                return null;
            }
            byte[] result = instrument(bytes, loader);
            PositionAgent.log("Installed position hook for Build 42.20.4; client mods unchanged.");
            return result;
        } catch (Throwable error) {
            PositionAgent.log("Could not install position hook; game continues without export.");
            return null;
        }
    }

    static byte[] instrument(byte[] bytes, ClassLoader loader) {
        ClassFile cf = ClassFile.of(ClassFile.ClassHierarchyResolverOption.of(
            ClassHierarchyResolver.ofResourceParsing(loader)));
        ClassModel model = cf.parse(bytes);
        long matches = model.methods().stream().filter(PositionTransformer::targetMethod).count();
        if (matches != 1) throw new IllegalArgumentException("Unexpected update method");
        // RCONServer.update() is invoked once per dedicated game-loop iteration,
        // including when RCON is disabled or the empty server's game clock pauses.
        return cf.transformClass(model, ClassTransform.transformingMethodBodies(
            PositionTransformer::targetMethod, new CodeTransform() {
                @Override public void atStart(CodeBuilder builder) {
                    builder.invokestatic(ClassDesc.of("net.lofers.observer.PositionAgent"),
                        "tick", MethodTypeDesc.ofDescriptor("()V"));
                }
                @Override public void accept(CodeBuilder builder, CodeElement element) {
                    builder.with(element);
                }
            }));
    }

    private static boolean targetMethod(MethodModel method) {
        return method.methodName().equalsString("update") && method.methodType().equalsString("()V")
            && method.flags().has(AccessFlag.STATIC) && method.code().isPresent();
    }
}
