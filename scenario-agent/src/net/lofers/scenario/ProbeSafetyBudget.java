package net.lofers.scenario;

/** A missed scan brakes immediately. Only a new complete scan permits motion. */
final class ProbeSafetyBudget {
    private double waiting;
    private long pauses;
    String update(String problem,double dt){
        if(!ProbeRoute.finite(dt)||dt<=0||dt>1)return "invalid_safety_interval";
        if(problem.equals("road_check_budget")||problem.equals("vehicle_check_budget")){
            waiting+=dt;pauses++;
            return waiting>=2?"safety_scan_starved":"safety_refresh_pending";
        }
        waiting=0;return problem;
    }
    long pauses(){return pauses;}
}
