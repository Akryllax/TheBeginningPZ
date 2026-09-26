package net.lofers.scenario;

/** Hard operation bounds for the synchronous safety scan, independent of JVM pauses. */
final class ProbeSafetyWork {
    static final int MAX_TILES=256,MAX_VEHICLES=64;
    private int tiles,vehicles;
    void reset(){tiles=vehicles=0;}
    boolean tile(){return ++tiles<=MAX_TILES;}
    boolean vehicle(){return ++vehicles<=MAX_VEHICLES;}
    int tiles(){return tiles;}
    int vehicles(){return vehicles;}
}
