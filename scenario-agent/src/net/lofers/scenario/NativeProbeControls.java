package net.lofers.scenario;

import java.lang.invoke.*;
import zombie.core.physics.CarController;
import zombie.vehicles.*;

/** Calls the installed, hash-guarded drivetrain; never copies/replaces engine classes. */
final class NativeProbeControls {
    private static final MethodHandle FORWARD;
    private static final MethodHandle BRAKING;
    static {
        try {
            var lookup=MethodHandles.privateLookupIn(CarController.class,MethodHandles.lookup());
            FORWARD=lookup.findVirtual(CarController.class,"control_ForwardNew",MethodType.methodType(void.class,float.class));
            BRAKING=lookup.findVirtual(CarController.class,"control_Braking",MethodType.methodType(void.class));
        }catch(ReflectiveOperationException e){throw new ExceptionInInitializerError(e);}
    }
    record Applied(float force,float brake,float steering,double availableForce,double availableBrake) {}
    static ProbeCarLimits limits(BaseVehicle car,double speed){
        var script=car.getScript();double mass=car.getMass(),grip=Double.POSITIVE_INFINITY;
        for(String name:new String[]{"TireFrontLeft","TireFrontRight","TireRearLeft","TireRearRight"}){
            VehiclePart part=car.getPartById(name);
            if(part==null||part.getInventoryItem()==null||part.getCondition()<=0)throw new IllegalStateException("probe_requires_four_working_tyres");
            grip=Math.min(grip,part.getWheelFriction());
        }
        if(!car.isEngineRunning()||car.getEnginePower()<=0||car.getBrakingForce()<=0||!Double.isFinite(grip)||grip<=0)
            throw new IllegalStateException("vehicle_capabilities_unavailable");
        double traction=Math.min(1,grip/1.6);
        // Conservative planning estimates. Native brake/tyre forces and actual
        // loaded mass remain authoritative; these are not SI conversions of
        // Bullet's engine-specific brake units.
        double deceleration=Math.min(1.6,car.getBrakingForce()*20/mass)*traction;
        return new ProbeCarLimits(mass,Math.min(car.getEnginePower(),mass*1.5),car.getMaxSpeed(),
            Math.min(.65,script.getSteeringClamp((float)speed)),Math.min(1.2,script.getSteeringIncrement()*30),
            deceleration,2.5*traction);
    }
    static Applied apply(BaseVehicle car,double speed,double requestedForce,double brakePercent,double steering)throws Throwable {
        CarController controller=car.getController();float force=0,brake=0;
        double availableForce=0,availableBrake=car.getBrakingForce();
        if(brakePercent>0){
            controller.clientControls.brake=false;
            BRAKING.invokeExact(controller);
            availableBrake=controller.brakingForce;
            brake=(float)(availableBrake*Math.min(1,Math.max(0,brakePercent/100)));
        }else if(requestedForce>0&&car.isEngineRunning()){
            controller.clientControls.wasUsingParkingBrakes=false;
            FORWARD.invokeExact(controller,(float)speed);
            availableForce=Math.max(0,controller.engineForce);
            force=(float)Math.min(requestedForce,availableForce);
        }else controller.control_NoControl();
        if(!ProbeRoute.finite(force,brake,availableForce,availableBrake)||availableBrake<=0)throw new IllegalStateException("native_controls_invalid");
        float angle=(float)Math.max(-car.getScript().getSteeringClamp((float)speed),Math.min(car.getScript().getSteeringClamp((float)speed),steering));
        return new Applied(force,brake,angle,availableForce,availableBrake);
    }
}
