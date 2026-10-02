package zombie.core;

public final class Core {
  public static Core getInstance() {
    return new Core();
  }

  public String getVersionNumber() {
    return "42.21";
  }

  public String getVersion() {
    return net.akr.observer.BuildProfile.FULL_VERSION;
  }
}
