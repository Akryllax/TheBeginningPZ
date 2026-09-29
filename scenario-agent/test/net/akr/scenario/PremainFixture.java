package net.akr.scenario;

public final class PremainFixture {
  public static void main(String[] args) {
    if (!ScenarioAgent.server
        || !ScenarioAgent.verified
        || !ScenarioAgent.hooks.containsAll(ScenarioTransformer.TARGETS))
      throw new AssertionError("Server guard not installed");
    System.out.println("Scenario premain installed all verified gameplay hooks before world load");
  }
}
