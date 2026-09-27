package net.lofers.scenario;

import java.util.Properties;
import zombie.iso.IsoObject;

/** One explicitly marked sacrificial object in a disposable crash experiment. */
record ProbeImpactTarget(int x,int y,String token,String sprite,boolean slopedRock) {
    static final String SPRITE="appliances_com_01_94", BOULDER="boulders_0", STOVE="appliances_cooking_01_16", TAG="LofersPoleFixture";
    static ProbeImpactTarget read(Properties p,ProbeRoute route,boolean road,boolean bypass,boolean stops){
        String value=p.getProperty("vehicle_probe.impact_target","").strip();
        if(value.isEmpty())return null;
        if(!road||bypass||stops||!TrafficBypass.straight(route))throw new IllegalArgumentException("Impact test requires a straight Bezier road without bypass or stops");
        String[] fields=value.split(",",-1);if(fields.length!=2)throw new IllegalArgumentException("Impact target requires tile x,y");
        int x=Integer.parseInt(fields[0]),y=Integer.parseInt(fields[1]);
        String token=p.getProperty("vehicle_probe.impact_token","");
        if(!token.matches("impact-[a-f0-9]{32}"))throw new IllegalArgumentException("Impact fixture token required");
        double offset=Double.parseDouble(p.getProperty("vehicle_probe.impact_lateral_offset","0"));
        if(!Double.isFinite(offset)||Math.abs(offset)>1.1||(!route.extendedImpact&&offset!=0))
            throw new IllegalArgumentException("Glancing offset requires an extended impact course and at most 1.1 tiles");
        String sprite=p.getProperty("vehicle_probe.impact_sprite",SPRITE);
        if(!SPRITE.equals(sprite)&&!(route.extendedImpact&&(BOULDER.equals(sprite)||STOVE.equals(sprite))))throw new IllegalArgumentException("Unsupported impact fixture sprite");
        boolean slopedRock=Boolean.parseBoolean(p.getProperty("vehicle_probe.impact_rock_mesh","false"));
        if(slopedRock&&(!route.extendedImpact||!BOULDER.equals(sprite)))throw new IllegalArgumentException("Sloped rock requires reviewed boulder scene");
        var projection=route.project(x+.5,y+.5);
        var origin=route.points.getFirst();
        double lateral=(x+.5-origin.x())*Math.cos(route.heading())-(y+.5-origin.y())*Math.sin(route.heading());
        if(Math.abs(lateral-offset)>.05||projection.progress()<15||route.length-projection.progress()<15)
            throw new IllegalArgumentException("Impact target requires clear approach and runout");
        return new ProbeImpactTarget(x,y,token,sprite,slopedRock);
    }
    boolean tile(int tx,int ty){return tx==x&&ty==y;}
    boolean matches(IsoObject object){
        return object!=null&&object.sprite!=null&&sprite.equals(object.sprite.name)
            &&token.equals(object.getModData().rawget(TAG));
    }
}
