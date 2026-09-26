package net.lofers.scenario;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.jar.*;

/** Search order, changed native ABI and opt-in platform guards; no native code is loaded. */
final class NativeGuardFixture {
    static void run(Path gameRoot)throws Exception {
        Path nativeDir=gameRoot.resolve("linux64");
        Path actual=nativeDir.resolve(BuildGuard.SERVER_PHYSICS_LIBRARY).toRealPath();
        ScenarioFixture.check(BuildGuard.verifyNativeSearchPath("",nativeDir.toString()).equals(actual),
            "Pinned native library rejected");
        Path temp=Files.createTempDirectory(Path.of("artifacts/scenario-agent"),"native-guard-");
        try {
            Path bad=Files.createDirectory(temp.resolve("bad")),empty=Files.createDirectory(temp.resolve("empty"));
            Files.write(bad.resolve(BuildGuard.SERVER_PHYSICS_LIBRARY),new byte[]{1,2,3});
            ScenarioFixture.rejects(()->BuildGuard.verifyNativeSearchPath("",empty.toString()),"Missing native library accepted");
            ScenarioFixture.rejects(()->BuildGuard.verifyNativeSearchPath("",bad.toString()),"Changed native ABI accepted");
            ScenarioFixture.rejects(()->BuildGuard.verifyNativeSearchPath("",bad+File.pathSeparator+nativeDir),
                "Bad first library ignored in favor of later pinned copy");
            ScenarioFixture.rejects(()->BuildGuard.verifyNativeSearchPath(nativeDir.toString(),nativeDir.toString()),
                "Boot-library shadow accepted");

            Path agent=agentJar(temp);
            subprocess(agent,nativeDir,null,"accept",actual,Map.of());
            // Current properties must not conceal a wrong bootstrap search path.
            subprocess(agent,bad,nativeDir,"reject:Unsupported server physics library:",actual,Map.of());
            subprocess(agent,empty,nativeDir,"reject:Pinned server physics library missing",actual,Map.of());
            // Nor should changing a current property falsify a correct bootstrap path.
            subprocess(agent,nativeDir,bad,"accept",actual,Map.of());
            subprocess(agent,nativeDir,null,"reject:Debug Bullet library",actual,
                Map.of("zomboid.debuglibs.bullet","1"));
            subprocess(agent,nativeDir,null,"reject:Server vehicle physics requires",actual,
                Map.of("lofers.fixture.mask.arch","aarch64"));
            subprocess(agent,nativeDir,null,"accept",actual,
                Map.of("lofers.fixture.mask.boot",bad.toString()));
            String realBoot=System.getProperty("sun.boot.library.path");
            subprocess(agent,nativeDir,null,"reject:Unexpected Bullet library on the JVM boot search path",actual,
                Map.of("sun.boot.library.path",realBoot+File.pathSeparator+bad.toAbsolutePath(),
                    "lofers.fixture.mask.boot",realBoot));
        } finally {
            try(var files=Files.walk(temp)){for(Path p:files.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}
        }
    }
    private static Path agentJar(Path dir)throws Exception {
        Path jar=dir.resolve("startup-guard-fixture.jar").toAbsolutePath();
        Manifest manifest=new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION,"1.0");
        manifest.getMainAttributes().putValue("Premain-Class",Agent.class.getName());
        String resource=Agent.class.getName().replace('.','/')+".class";
        try(var out=new JarOutputStream(Files.newOutputStream(jar),manifest);
            var in=NativeGuardFixture.class.getClassLoader().getResourceAsStream(resource)) {
            if(in==null)throw new AssertionError("Missing native guard agent fixture");
            out.putNextEntry(new JarEntry(resource));in.transferTo(out);out.closeEntry();
        }
        return jar;
    }
    private static void subprocess(Path agent,Path startup,Path mask,String expected,Path actual,Map<String,String> extra)throws Exception {
        var command=new ArrayList<String>(List.of(Path.of(System.getProperty("java.home"),"bin","java").toString(),
            "-XX:-CreateCoredumpOnCrash","-Djava.library.path="+startup.toAbsolutePath(),
            "-javaagent:"+agent,"-Dlofers.fixture.expected="+expected,"-Dlofers.fixture.actual="+actual));
        if(mask!=null)command.add("-Dlofers.fixture.mask.path="+mask.toAbsolutePath());
        extra.forEach((key,value)->command.add("-D"+key+"="+value));
        command.addAll(List.of("-cp",System.getProperty("java.class.path"),NativeGuardFixture.class.getName()));
        Process process=new ProcessBuilder(command).redirectErrorStream(true).start();
        if(!process.waitFor(15,TimeUnit.SECONDS)) {
            process.destroyForcibly();process.waitFor();
            throw new AssertionError("Native startup guard fixture timed out");
        }
        String output=new String(process.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        if(process.exitValue()!=0)throw new AssertionError("Native startup guard fixture failed: "+output);
    }
    public static void main(String[] args) {
        String expected=System.getProperty("lofers.fixture.expected");
        String result=System.getProperty("lofers.fixture.result", "missing");
        if(!result.startsWith(expected))throw new AssertionError("Expected "+expected+"; got "+result);
        if("accept".equals(expected)&&!System.getProperty("lofers.fixture.actual").equals(System.getProperty("lofers.fixture.path")))
            throw new AssertionError("Guard selected an unexpected native library");
    }
    /** Exercises the production module export in premain, without launching the game. */
    public static final class Agent {
        public static void premain(String args,Instrumentation instrumentation)throws Exception {
            mask("lofers.fixture.mask.path","java.library.path");
            mask("lofers.fixture.mask.boot","sun.boot.library.path");
            mask("lofers.fixture.mask.arch","os.arch");
            try {
                Path path=BuildGuard.verifyServerPhysics(instrumentation);
                System.setProperty("lofers.fixture.path",path.toString());
                System.setProperty("lofers.fixture.result","accept");
            } catch(IllegalStateException expected) {
                System.setProperty("lofers.fixture.result","reject:"+expected.getMessage());
            }
            for(Class<?> loaded:instrumentation.getAllLoadedClasses()) {
                if(loaded.getName().startsWith("zombie.core.physics."))
                    throw new AssertionError("Native guard loaded game physics class: "+loaded.getName());
            }
        }
        private static void mask(String source,String target) {
            String value=System.getProperty(source);if(value!=null)System.setProperty(target,value);
        }
    }
}
