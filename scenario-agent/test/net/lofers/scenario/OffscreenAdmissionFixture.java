package net.lofers.scenario;

import java.util.*;

final class OffscreenAdmissionFixture {
  static void run() {
    var a = new OffscreenAdmission.Observer(1, 0, 0);
    var b = new OffscreenAdmission.Observer(2, 10, 0);
    var ar = new OffscreenAdmission.Report(1, 1000, 3, 0, 0, true, true);
    var br = new OffscreenAdmission.Report(2, 1000, 3, 10, 0, true, true);
    assert OffscreenAdmission.owner(List.of(a, b), List.of(ar, br), 1200, 3, 9, 0) == 2;
    assert OffscreenAdmission.owner(List.of(a, b), List.of(ar), 1200, 3, 9, 0)
        == -1; // missing observer
    assert OffscreenAdmission.owner(List.of(a), List.of(ar), 1751, 3, 9, 0) == -1; // stale
    assert OffscreenAdmission.owner(List.of(a), List.of(ar), 999, 3, 9, 0) == -1; // clock reversal
    assert OffscreenAdmission.owner(List.of(a), List.of(ar), 1200, 4, 9, 0) == -1; // stale proposal
    assert OffscreenAdmission.owner(
            List.of(new OffscreenAdmission.Observer(1, 2, 0)), List.of(ar), 1200, 3, 9, 0)
        == -1;
    assert OffscreenAdmission.owner(
            List.of(a, b),
            List.of(ar, new OffscreenAdmission.Report(2, 1000, 3, 10, 0, false, true)),
            1200,
            3,
            9,
            0)
        == -1; // any viewer vetoes
    assert OffscreenAdmission.owner(
            List.of(a),
            List.of(new OffscreenAdmission.Report(1, 1000, 3, 0, 0, true, false)),
            1200,
            3,
            9,
            0)
        == -1; // no loaded owner
    assert OffscreenAdmission.owner(List.of(a, b), List.of(ar, ar), 1200, 3, 9, 0)
        == -1; // duplicate cannot satisfy quorum
    assert OffscreenAdmission.owner(
            List.of(a),
            List.of(new OffscreenAdmission.Report(1, 1000, 3, Double.NaN, 0, true, true)),
            1200,
            3,
            9,
            0)
        == -1;
    System.out.println(
        "Offscreen admission: fresh all-observer quorum, loaded nearest owner,"
            + " movement/revision/visibility vetoes passed");
  }
}
