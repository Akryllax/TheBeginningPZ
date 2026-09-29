package net.lofers.scenario;

import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import zombie.core.skinnedmodel.advancedanimation.*;

/** Mean translation from installed animation assets, using the engine's own blend picker.
 * Cached at prewarm. No animation assets or reconstructed engine code are packaged here. */
final class NativeGaitSpeeds {
    private static NativeGaitSpeeds installed;
    static NativeGaitSpeeds installed(){if(installed==null)installed=load(Path.of("media"));return installed;}
    private final AnimNode walk,run;private final double motionScale;
    private final Map<String,Double> rates=new HashMap<>();
    private final Anim2DBlendPicker.PickResults pick=new Anim2DBlendPicker.PickResults();
    private NativeGaitSpeeds(AnimNode walk,AnimNode run,double scale){this.walk=walk;this.run=run;motionScale=scale;}
    static NativeGaitSpeeds load(Path media){
        try {
            String defaults=Files.readString(media.resolve("AnimSets/Defaults.xml"));
            var scale=Pattern.compile("<MotionScale>([^<]+)</MotionScale>").matcher(defaults);
            if(!scale.find())throw new IllegalStateException("motion_scale_missing");
            var w=node(media.resolve("AnimSets/player/movement/defaultWalk.xml"));
            var r=node(media.resolve("AnimSets/player/run/defaultRun.xml"));
            if(w==null||r==null||w.blend2dPicker==null||r.blend2dPicker==null)throw new IllegalStateException("gait_blends_missing");
            var result=new NativeGaitSpeeds(w,r,Double.parseDouble(scale.group(1)));
            for(var node:List.of(w,r))for(var blend:node.blends2d)
                if(!result.rates.containsKey(blend.animName))result.rates.put(blend.animName,clipRate(media.resolve("anims_X/Bob/"+blend.animName+".x")));
            if(!(result.motionScale>0&&result.motionScale<=4))throw new IllegalStateException("motion_scale_bounds");
            return result;
        }catch(Exception error){throw new IllegalStateException("native_gait_profile",error);}
    }
    private static String text(Element e,String name){return e.getElementsByTagName(name).item(0).getTextContent().trim();}
    private static AnimNode node(Path path) throws Exception {
        var factory=DocumentBuilderFactory.newInstance();factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        var root=factory.newDocumentBuilder().parse(path.toFile()).getDocumentElement();
        var node=new AnimNode();node.speedScale=text(root,"m_SpeedScale");
        var refs=new HashMap<String,Anim2DBlend>();var blends=root.getElementsByTagName("m_2DBlends");
        for(int i=0;i<blends.getLength();i++){
            var e=(Element)blends.item(i);var b=new Anim2DBlend();b.animName=text(e,"m_AnimName");
            b.posX=Float.parseFloat(text(e,"m_XPos"));b.posY=Float.parseFloat(text(e,"m_YPos"));
            refs.put(e.getAttribute("referenceID"),b);node.blends2d.add(b);
        }
        var triangles=root.getElementsByTagName("m_2DBlendTri");
        for(int i=0;i<triangles.getLength();i++){
            var e=(Element)triangles.item(i);var t=new Anim2DBlendTriangle();
            t.node1=Objects.requireNonNull(refs.get(text(e,"node1")));t.node2=Objects.requireNonNull(refs.get(text(e,"node2")));t.node3=Objects.requireNonNull(refs.get(text(e,"node3")));
            node.blendTris.add(t);
        }
        node.blend2dPicker=new Anim2DBlendPicker();node.blend2dPicker.SetPickTriangles(node.blendTris);return node;
    }
    private static double clipRate(Path path) throws Exception {
        if(Files.size(path)>2_000_000)throw new IllegalStateException("gait_asset_size");
        String source=Files.readString(path);
        var hz=Pattern.compile("AnimTicksPerSecond\\s*\\{\\s*(\\d+)").matcher(source);
        int bone=source.lastIndexOf("{ Translation_Data }");
        if(!hz.find()||bone<0)throw new IllegalStateException("gait_asset_structure:"+path.getFileName());
        var track=Pattern.compile("AnimationKey T\\s*\\{([^}]+)}").matcher(source.substring(bone));
        if(!track.find())throw new IllegalStateException("gait_translation_missing");
        var keys=Pattern.compile("(\\d+);3;([^;]+)").matcher(track.group(1));
        double[] first=null,last=null;double begin=0,end=0;
        while(keys.find()){
            last=Arrays.stream(keys.group(2).split(",")).mapToDouble(Double::parseDouble).toArray();end=Double.parseDouble(keys.group(1));
            if(first==null){first=last;begin=end;}
        }
        if(first==null||last.length!=3||end<=begin)throw new IllegalStateException("gait_translation_keys");
        double distance=0;for(int i=0;i<3;i++)distance+=Math.pow(last[i]-first[i],2);
        double rate=Math.sqrt(distance)*Double.parseDouble(hz.group(1))/(end-begin);
        if(!Double.isFinite(rate)||rate<=0||rate>8)throw new IllegalStateException("gait_rate_bounds");return rate;
    }
    double speed(boolean running,float injury,float blend){
        if(!Float.isFinite(injury)||!Float.isFinite(blend))throw new IllegalArgumentException("gait_input");
        var node=running?run:walk;node.blend2dPicker.Pick(injury,blend,pick);
        if(pick.numNodes<1||pick.numNodes>3)throw new IllegalStateException("gait_pick");
        double rate=rate(pick.node1,pick.scale1)+rate(pick.node2,pick.scale2)+rate(pick.node3,pick.scale3);
        double speed=rate*Double.parseDouble(node.speedScale)*motionScale;
        if(!Double.isFinite(speed)||speed<=0||speed>8)throw new IllegalStateException("gait_speed_bounds");return speed;
    }
    private double rate(Anim2DBlend node,float weight){return node==null?0:rates.get(node.animName)*weight;}
}
