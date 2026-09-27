package net.lofers.scenario;

import java.lang.classfile.*;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.ReturnInstruction;
import java.lang.constant.*;
import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.Set;

final class ScenarioTransformer implements ClassFileTransformer {
    static final ClassDesc AGENT=ClassDesc.of("net.lofers.scenario.ScenarioAgent");
    static final ClassDesc NATIVE=ClassDesc.of("net.lofers.scenario.GameHooks");
    static final ClassDesc CRASH=ClassDesc.of("net.lofers.scenario.ProbeCrashFeedback");
    static final Set<String> TARGETS=Set.of("zombie/network/RCONServer","zombie/Lua/LuaManager",
        "zombie/Lua/Event","zombie/VirtualZombieManager","zombie/iso/IsoWorld","zombie/core/physics/CarController",
        "zombie/vehicles/BaseVehicle");
    public byte[] transform(ClassLoader loader,String name,Class<?> redefining,ProtectionDomain domain,byte[] bytes) {
        if(!TARGETS.contains(name)||redefining!=null) return null;
        try { byte[] result=instrument(name,bytes,loader);ScenarioAgent.hooks.add(name);return result; }
        catch(Throwable ex) { ScenarioAgent.hookFailure=name+":"+ex.getClass().getSimpleName();return null; }
    }
    static byte[] instrument(String name,byte[] bytes,ClassLoader loader) {
        var cf=ClassFile.of(ClassFile.ClassHierarchyResolverOption.of(ClassHierarchyResolver.ofResourceParsing(loader)));
        return cf.transformClass(cf.parse(bytes),(cb,element)->{
            if(!(element instanceof MethodModel m)||m.code().isEmpty()){cb.with(element);return;}
            String method=m.methodName().stringValue(),descriptor=m.methodType().stringValue();
            cb.transformMethod(m,MethodTransform.transformingCode(new CodeTransform(){
            public void atStart(CodeBuilder b) {
                if(name.equals("zombie/vehicles/BaseVehicle")&&method.equals("crash")&&descriptor.equals("(FZ)V"))
                    b.aload(0).fload(1).iload(2).invokestatic(CRASH,"onCrash",MethodTypeDesc.ofDescriptor("(Lzombie/vehicles/BaseVehicle;FZ)V"));
                if(name.equals("zombie/network/RCONServer")&&method.equals("update")&&descriptor.equals("()V"))
                    b.invokestatic(AGENT,"tick",MethodTypeDesc.ofDescriptor("()V"));
                if(name.equals("zombie/Lua/Event")&&method.equals("trigger"))
                    b.invokestatic(AGENT,"bootstrap",MethodTypeDesc.ofDescriptor("()V"));
                if(name.equals("zombie/iso/IsoWorld")&&method.equals("getZombiesDisabled")) {
                    b.invokestatic(NATIVE,"inSpawnPermit",MethodTypeDesc.ofDescriptor("()Z"));
                    b.ifThen(x->x.iconst_0().ireturn());
                    b.invokestatic(NATIVE,"populationBlocked",MethodTypeDesc.ofDescriptor("()Z"));
                    b.ifThen(x->x.iconst_1().ireturn());
                }
                if(name.equals("zombie/VirtualZombieManager")&&method.equals("createRealZombieAlways")
                    && descriptor.equals("(Lzombie/iso/IsoDirections;ZI)Lzombie/characters/IsoZombie;")) {
                    b.invokestatic(NATIVE,"factoryBlocked",MethodTypeDesc.ofDescriptor("()Z"));
                    b.ifThen(x->x.aconst_null().areturn());
                }
                if(name.equals("zombie/core/physics/CarController")&&method.equals("update")&&descriptor.equals("()V")) {
                    b.aload(0).invokestatic(NATIVE,"vehicleStep",MethodTypeDesc.ofDescriptor("(Lzombie/core/physics/CarController;)Z"));
                    b.ifThen(x->x.return_());
                }
            }
            public void accept(CodeBuilder b,CodeElement e) {
                if(name.equals("zombie/Lua/LuaManager")&&method.equals("init")&&e instanceof ReturnInstruction)
                    b.invokestatic(AGENT,"bootstrap",MethodTypeDesc.ofDescriptor("()V"));
                if(name.equals("zombie/Lua/Event")&&method.equals("trigger")&& e instanceof InvokeInstruction call
                    &&call.owner().asInternalName().equals("se/krka/kahlua/integration/LuaCaller")
                    &&call.name().equalsString("protectedCallVoid")
                    &&call.type().equalsString("(Lse/krka/kahlua/vm/KahluaThread;Ljava/lang/Object;[Ljava/lang/Object;)V")) {
                    b.invokestatic(NATIVE,"eventCallback",MethodTypeDesc.ofDescriptor(
                        "(Lse/krka/kahlua/integration/LuaCaller;Lse/krka/kahlua/vm/KahluaThread;Ljava/lang/Object;[Ljava/lang/Object;)V"));
                } else b.with(e);
            }
            }));
        });
    }
}
