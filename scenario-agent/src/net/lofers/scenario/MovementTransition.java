package net.lofers.scenario;

/** Native animation blend-in has duration; authoritative movement must not start at cruise speed. */
final class MovementTransition {
    private long began;private boolean active;private double from;
    void reset(){active=false;from=0;began=0;}
    void begin(long now,double previous){active=true;began=now;from=Math.max(0,previous);}
    double progress(long now,boolean running){return active?Math.clamp((now-began)/(running?350_000_000.0:200_000_000.0),0,1):0;}
    double speed(long now,boolean running,double target){double t=progress(now,running);// A WALK packet immediately selects the walking clip. Never retain RUN translation
        // above that clip's native rate while the visual blend settles.
        return Math.min(target,from+(target-from)*t);}
}
