package net.lofers.scenario;
import java.lang.classfile.*;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.ReturnInstruction;
import java.util.Arrays;
import java.util.zip.ZipFile;
final class ProbeRockColliderFixture {
    static void run(ZipFile game)throws Exception{
        int[] original={ProbeRockCollider.SOLID,ProbeRockCollider.FLOOR,-1,-1};
        int[] shapes=original.clone();ScenarioFixture.check(ProbeRockCollider.replace(shapes,ProbeRockCollider.FIRST_MESH),"Rock substitution rejected");
        ScenarioFixture.check(shapes[1]==ProbeRockCollider.FLOOR,"Rock removed road floor");
        shapes=new int[]{ProbeRockCollider.SOLID,ProbeRockCollider.FLOOR,1,-1};int[] before=shapes.clone();
        ScenarioFixture.check(!ProbeRockCollider.replace(shapes,ProbeRockCollider.FIRST_MESH)&&Arrays.equals(shapes,before),"Rock erased unrelated wall");
        shapes=new int[]{ProbeRockCollider.FLOOR,-1,-1,-1};
        ScenarioFixture.check(!ProbeRockCollider.replace(shapes,ProbeRockCollider.FIRST_MESH),"Missing rock gained collider");
        float[] points=ProbeRockCollider.points();ScenarioFixture.check(points.length==24,"Unbounded rock geometry");
        for(int i=0;i<points.length;i++)ScenarioFixture.check(Float.isFinite(points[i])&&Math.abs(points[i])<=.61,"Invalid original rock extent");
        byte[] bytes=game.getInputStream(game.getEntry("zombie/iso/IsoChunk.class")).readAllBytes();
        var changed=ClassFile.of().parse(ScenarioTransformer.instrument("zombie/iso/IsoChunk",bytes,ProbeRockColliderFixture.class.getClassLoader()));
        int hooks=0;
        for(var method:changed.methods())if(method.code().isPresent())for(var element:method.code().orElseThrow())if(element instanceof InvokeInstruction call&&call.owner().asInternalName().equals("net/lofers/scenario/ProbeRockCollider")){
            ScenarioFixture.check(method.methodName().equalsString("calcPhysics")&&method.methodType().equalsString("(III[I)V")&&call.name().equalsString("apply"),"Terrain hook escaped exact calculator");hooks++;
        }
        int returns=0;for(var method:ClassFile.of().parse(bytes).methods())if(method.methodName().equalsString("calcPhysics")&&method.methodType().equalsString("(III[I)V"))for(var element:method.code().orElseThrow())if(element instanceof ReturnInstruction)returns++;
        ScenarioFixture.check(returns>0&&hooks==returns,"Terrain return path hook count mismatch");
        System.out.println("Rock collider fixtures passed: exact hook, bounded original hull, preserved floor and unrelated shapes");
    }
}
