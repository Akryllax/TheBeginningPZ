package net.lofers.scenario;

import java.util.SplittableRandom;

/** Stable resident preferences; never randomize emergency safety every frame. */
record TrafficTemperament(double patienceSeconds,double hornChance,double hornDelaySeconds,double hornSeconds,
                          double stoppedGap,double retrySeconds) {
    TrafficTemperament {
        if(!ProbeRoute.finite(patienceSeconds,hornChance,hornDelaySeconds,hornSeconds,stoppedGap,retrySeconds)||
           patienceSeconds<8||patienceSeconds>40||hornChance<0||hornChance>1||hornDelaySeconds<3||hornDelaySeconds>10||
           hornSeconds<.3||hornSeconds>1.2||stoppedGap<1||stoppedGap>2||retrySeconds<3||retrySeconds>10)
            throw new IllegalArgumentException("Invalid traffic temperament");
    }
    static TrafficTemperament forResident(long identity){
        var random=new SplittableRandom(identity);
        return new TrafficTemperament(random.nextDouble(12,32),random.nextDouble(.25,.85),random.nextDouble(3,8),
            random.nextDouble(.4,.9),random.nextDouble(1.25,1.9),random.nextDouble(4,8));
    }
}
