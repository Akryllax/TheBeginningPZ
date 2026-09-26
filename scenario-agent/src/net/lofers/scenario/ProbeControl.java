package net.lofers.scenario;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/** Private operator files and detached diagnostics only; this thread sees no game objects. */
final class ProbeControl implements Runnable {
    record Config(Path directory,double x,double y,double yaw,double distance,double speed,ProbeRoute route,boolean roadMode,boolean driverModel) {
        Config(Path directory,double x,double y,double yaw,double distance,double speed){this(directory,x,y,yaw,distance,speed,ProbeRoute.straight(x,y,yaw,distance),false,false);}
        double deadlineSeconds(){return roadMode?Math.min(120,Math.max(45,route.length/speed*3.6*2+15)):30;}
        static Config read(Properties p,String world,boolean server) {
            if(!Boolean.parseBoolean(p.getProperty("vehicle_probe.enabled","false")))return null;
            if(!server||!world.matches("LofersVehicleProbe_[A-Za-z0-9_-]{1,64}"))throw new IllegalArgumentException("Vehicle probe requires a disposable LofersVehicleProbe_ world on server");
            Path dir=Path.of(p.getProperty("vehicle_probe.directory","")).normalize();
            if(!dir.isAbsolute()||dir.getParent()==null)throw new IllegalArgumentException("Private absolute probe directory required");
            double x=bounded(p,"x",Double.NaN,-20000,60000),y=bounded(p,"y",Double.NaN,-20000,60000);
            if(bounded(p,"z",0,0,0)!=0)throw new IllegalArgumentException("Ground-level probe only");
            String points=p.getProperty("vehicle_probe.waypoints","").strip();boolean roadMode=!points.isEmpty();
            double yaw=bounded(p,"heading_degrees",90,0,360),distance=bounded(p,"distance",10,2,12),speed=bounded(p,"speed_kmh",4,1,5);
            ProbeRoute route=roadMode?ProbeRoute.parse(points):ProbeRoute.straight(x,y,yaw,distance);
            if(Math.hypot(route.points.getFirst().x()-x,route.points.getFirst().y()-y)>0.01)throw new IllegalArgumentException("Route must start at configured spawn");
            if(Math.abs(ProbeRoute.wrap(route.heading()-Math.toRadians(yaw)))>Math.toRadians(10))throw new IllegalArgumentException("Spawn heading differs from route");
            String script=p.getProperty("vehicle_probe.script","Base.SmallCar");
            if(!Set.of("Base.SmallCar","Base.LofersSmallCar").contains(script))throw new IllegalArgumentException("Unsupported probe vehicle script");
            return new Config(dir,x,y,yaw,roadMode?route.length:distance,speed,route,roadMode,script.equals("Base.LofersSmallCar"));
        }
        private static double bounded(Properties p,String name,double fallback,double min,double max) {
            double n=Double.parseDouble(p.getProperty("vehicle_probe."+name,Double.toString(fallback)));
            if(!Double.isFinite(n)||n<min||n>max)throw new IllegalArgumentException("Invalid vehicle_probe."+name);return n;
        }
    }
    record Command(long id,String action) {}
    final AtomicReference<Command> command=new AtomicReference<>();
    final AtomicReference<Map<String,String>> status=new AtomicReference<>();
    volatile String ioError="";
    private final Path directory;
    private final String epoch;
    private long lastCommand;
    ProbeControl(Config config,String epoch)throws IOException {
        directory=config.directory();this.epoch=epoch;
        if(!Files.isDirectory(directory,LinkOption.NOFOLLOW_LINKS)||Files.isSymbolicLink(directory))throw new IOException("Probe directory must already exist and not be a symlink");
    }
    static Command parse(byte[] bytes,String epoch,long last) throws IOException {
        if(bytes.length>4096)throw new IOException("Control file exceeds 4096 bytes");
        Properties p=new Properties();p.load(new ByteArrayInputStream(bytes));
        if(!epoch.equals(p.getProperty("server_epoch")))throw new IOException("Control server_epoch mismatch");
        long id;try{id=Long.parseLong(p.getProperty("command_id",""));}catch(NumberFormatException e){throw new IOException("Invalid command_id");}
        if(id<=last)return null;
        String action=p.getProperty("action","");if(!Set.of("start","stop").contains(action))throw new IOException("Action must be start or stop");
        return new Command(id,action);
    }
    public void run() {
        while(!Thread.currentThread().isInterrupted()) {
            try {
                Path input=directory.resolve("control.properties");
                if(Files.isRegularFile(input,LinkOption.NOFOLLOW_LINKS)) {
                    byte[] bytes;try(var in=Files.newInputStream(input,LinkOption.NOFOLLOW_LINKS)){bytes=in.readNBytes(4097);}
                    Command c=parse(bytes,epoch,lastCommand);if(c!=null){lastCommand=c.id();command.set(c);}ioError="";
                }
            }catch(Exception e){ioError=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();}
            try {
                Map<String,String> copy=status.get();
                if(copy!=null){
                    Properties p=new Properties();p.putAll(copy);p.setProperty("control_error",ioError);
                    Path temp=directory.resolve("status.properties.tmp"),target=directory.resolve("status.properties");
                    try(var writer=Files.newBufferedWriter(temp,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING,LinkOption.NOFOLLOW_LINKS)){p.store(writer,"Lofers server-only empty-car probe; no client observation implied");}
                    Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
                }
            }catch(Exception e){ioError="status_write_failed:"+e.getClass().getSimpleName();}
            try{Thread.sleep(250);}catch(InterruptedException e){Thread.currentThread().interrupt();}
        }
    }
}
