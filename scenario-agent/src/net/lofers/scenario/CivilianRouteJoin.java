package net.lofers.scenario;
import static net.lofers.scenario.CivilianNavigation.*;
/** A replacement planned from the committed endpoint waits for the moving body to join it. */
final class CivilianRouteJoin {
    static boolean approaching(Result candidate,Result committed,boolean moving,Tile origin,Tile current){
        return candidate!=null&&candidate.status()==Status.FOUND&&moving&&committed!=null
            &&committed.route().getLast().at().equals(origin)
            &&candidate.route().stream().noneMatch(s->s.at().equals(current));
    }
}
