package net.lofers.scenario;

import net.lofers.scenario.protocol.RuntimeControl.CivilianEncounterCase;

/** Immutable private test definition. Native randomness is recorded, never forged into success. */
record EncounterDefinition(CivilianEncounterCase.Scenario scenario,int actors,long seed,int timeoutSeconds,int holdSeconds) {
    EncounterDefinition {
        if(scenario==null||scenario==CivilianEncounterCase.Scenario.UNRECOGNIZED)throw new IllegalArgumentException("encounter_scenario");
        if(scenario==CivilianEncounterCase.Scenario.STRIDE_COMPARE){if(actors!=2)throw new IllegalArgumentException("stride_two_actors");}
        else if(actors!=1&&actors!=4)throw new IllegalArgumentException("encounter_actors");
        if(seed<0||timeoutSeconds<10||timeoutSeconds>120||holdSeconds<0||holdSeconds>30)throw new IllegalArgumentException("encounter_limits");
        if(scenario!=CivilianEncounterCase.Scenario.DEFENSE_ESCAPE&&scenario!=CivilianEncounterCase.Scenario.STRIDE_COMPARE&&actors!=1)throw new IllegalArgumentException("single_actor_gate");
    }
    static EncounterDefinition from(CivilianEncounterCase value) {
        return new EncounterDefinition(value.getScenario(),value.getActors(),value.getSeed(),
            value.getTimeoutSeconds()==0?120:value.getTimeoutSeconds(),value.getHoldSeconds());
    }
}
