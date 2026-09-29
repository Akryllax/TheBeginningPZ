package net.akr.scenario;

import java.nio.file.Path;

final class NativeGaitSpeedFixture {
  static void run() {
    var profile = NativeGaitSpeeds.load(Path.of("data/game-files/media"));
    double walk = profile.speed(false, 0, .8f), run = profile.speed(true, 0, .65f);
    if (walk < 2 || walk > 2.4 || run < 4 || run > 4.5 || run / walk < 1.8)
      throw new AssertionError("installed gait calibration:" + walk + ":" + run);
    if (profile.speed(true, .5f, .5f) >= run || profile.speed(false, 1, .8f) >= walk)
      throw new AssertionError("injury not reflected in root motion");
    System.out.println(
        "Installed native blend speed: WALK="
            + walk
            + " RUN="
            + run
            + " tiles/s; injury blends reduce movement");
  }
}
