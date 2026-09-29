package zombie.network;

import java.util.ArrayList;
import zombie.characters.IsoPlayer;

public final class GameServer {
  public static boolean server = true;
  public static String serverName = "ObserverFixture";
  public static ArrayList<IsoPlayer> online = new ArrayList<>();

  public static ArrayList<IsoPlayer> getPlayers() {
    return new ArrayList<>(online);
  }
}
