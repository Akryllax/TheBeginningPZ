package net.lofers.scenario;

import java.nio.file.*;
import java.util.*;
import zombie.GameTime;
import zombie.characters.IsoPlayer;
import zombie.iso.IsoWorld;
import zombie.network.*;
import zombie.network.chat.ChatServer;

/** One autonomous defense/escape case with the established observer setup and cleanup hold. */
final class WatchedSurvivalHarness {
    private final String world,epoch;private final Path directory;
    private NativeSurvivalBatch batch;private int phase;private long phaseAt,lastPosition,published;
    private boolean finished;private String failure="";
    private se.krka.kahlua.vm.KahluaTable config;
    WatchedSurvivalHarness(Properties p,String world,String epoch,boolean server,boolean runtime){
        if(!server||!runtime||!world.startsWith("AKR_DayOne_Test_")||world.contains("Headless"))throw new IllegalArgumentException("survival_disposable_only");
        for(String key:List.of("headless.enabled","watched.enabled","chase.enabled"))if(Boolean.parseBoolean(p.getProperty(key,"false")))throw new IllegalArgumentException("survival_exclusive");
        this.world=world;this.epoch=epoch;directory=Path.of(p.getProperty("combat.directory",""));
        if(!directory.isAbsolute()||!directory.normalize().startsWith("/run/lofers"))throw new IllegalArgumentException("survival_directory");
    }
    private void announce(String text){System.out.println("[CivilianSurvivalWatch] "+text);ChatServer.getInstance().sendServerAlertMessageToServerChat("[Civilian survival test] "+text);}
    private IsoPlayer observer(){for(var p:GameServer.Players)if(p!=null&&"akr".equals(p.getUsername())&&p.getSquare()!=null){var c=GameServer.getConnectionFromPlayer(p);if(c!=null&&c.isFullyConnected())return p;}return null;}
    private void next(long now){phase++;phaseAt=now;}
    void tick(){
        if(finished)return;long now=System.nanoTime();
        try {
            if(!GameServer.server||IsoWorld.instance==null||IsoWorld.instance.currentCell==null||ServerMap.instance==null||ServerMap.instance.cellMap==null)return;
            for(int y=10060;y<=10220;y+=40)ServerMap.instance.characterIn(Math.floorDiv(10589,8),Math.floorDiv(y,8),5);
            if(ServerMap.instance.getGridSquare(10589,10060,0)==null)return;
            if(batch==null)batch=new NativeSurvivalBatch(epoch,new CivilianNavigation.Tile(10589,10060,0),true);
            if(config==null){config=zombie.Lua.LuaManager.platform.newTable();config.rawset("epoch",epoch);config.rawset("actor",4096d);config.rawset("phase",3d);config.rawset("autonomous",true);zombie.Lua.LuaManager.env.rawset("AKRCombatWatch",config);}
            if(batch.targetId()>=0)config.rawset("target",(double)batch.targetId());
            var p=observer();
            if(phase>=2&&p==null)throw new IllegalStateException("observer_disconnected_resources_retained");
            if(p!=null){
                if(p.getRole()==null||!"admin".equalsIgnoreCase(p.getRole().getName())||p.getVehicle()!=null)throw new IllegalStateException("observer_admin_on_foot_required");
                if(!p.isGodMod()||!p.isInvisible()||!p.isGhostMode()){p.setGodMod(true,true);p.setInvisible(true,true);p.setGhostMode(true,true);GameServer.sendPlayerExtraInfo(p,null,true);}
            }
            switch(phase){
                case 0 -> {batch.tick(now);if(batch.prewarmed())next(now);}
                case 1 -> {if(p==null)return;GameTime.getInstance().setTimeOfDay(12);GameServer.sendWeather();
                    announce("One civilian: detect a real blocked exit, defend, then escape when the test opens the doors. Moving you to the viewing point.");
                    GameServer.sendTeleport(p,10596.5f,10060.5f,0);lastPosition=now;next(now);}
                case 2 -> {if(Math.hypot(p.getX()-10596.5,p.getY()-10060.5)>2){if(now-lastPosition>3_000_000_000L){GameServer.sendTeleport(p,10596.5f,10060.5f,0);lastPosition=now;}return;}
                    if(now-phaseAt<10_000_000_000L)return;announce("Starting the autonomous encounter. Watch the defense and escape; the zombie is stationary in this geometry test.");batch.beginObserved(now);next(now);}
                case 3 -> {batch.tick(now);if(batch.awaitingClearance()){announce("The civilian escaped. Holding for 20 seconds, then moving you away for verified cleanup.");next(now);}}
                case 4 -> {batch.hold();if(now-phaseAt<20_000_000_000L)return;GameServer.sendTeleport(p,10590.5f,10220.5f,0);next(now);}
                case 5 -> {if(batch.tick(now)){announce("TEST COMPLETE: native decision/contact/escape and actor cleanup passed. Visual feedback is still needed.");finished=true;next(now);}}
            }
        }catch(Throwable error){failure=error.getClass().getSimpleName()+":"+Objects.toString(error.getMessage(),"");error.printStackTrace();finished=true;try{announce("TEST STOPPED: "+failure);}catch(Exception ignored){}}
        finally {if(finished||now-published>1_000_000_000L){published=now;try{publish();}catch(Exception error){error.printStackTrace();}}}
    }
    private void publish()throws Exception{
        String json="{\"world\":\""+world+"\",\"epoch\":\""+epoch+"\",\"status\":\""+(failure.isEmpty()?(finished?"passed_visual_pending":"running"):"failed")+"\",\"phase\":"+phase+",\"failure\":\""+failure.replace("\\","/").replace("\"","'")+"\",\"case\":\"autonomous_defend_escape\"}\n";
        Files.createDirectories(directory);Path temp=directory.resolve("combat-report.tmp");Files.writeString(temp,json);Files.move(temp,directory.resolve("combat-report.json"),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
    }
}
