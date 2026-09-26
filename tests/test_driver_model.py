"""Original asset regeneration, normals, and isolated packaging (no game art)."""
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).parents[1] / 'scripts'))
import build_driver_model as driver
import vehicle_probe_ops as ops


def test_original_mesh_has_consistent_outward_winding():
    vertices, faces, normals, uvs = driver.geometry()
    assert len(faces) == 204
    assert len(vertices) == len(normals) == len(uvs) == 408
    for a, b, c in faces:
        u = [vertices[b][i] - vertices[a][i] for i in range(3)]
        v = [vertices[c][i] - vertices[a][i] for i in range(3)]
        cross = [u[1]*v[2]-u[2]*v[1], u[2]*v[0]-u[0]*v[2], u[0]*v[1]-u[1]*v[0]]
        assert sum(cross[i]*normals[a][i] for i in range(3)) > 0
    assert all(0 <= u <= 1 and 0 <= v <= 1 for u, v in uvs)


def test_committed_assets_match_original_generator():
    assert (driver.MEDIA / 'models_X/Lofers/SeatedDriver.x').read_text() == driver.mesh_text()
    assert (driver.MEDIA / 'textures/Lofers/DriverPalette.png').read_bytes() == driver.palette()


def test_probe_package_contains_only_original_visual_assets(tmp_path):
    ops.package_driver_assets(driver.ROOT, tmp_path / 'driver')
    files = {str(p.relative_to(tmp_path / 'driver')) for p in (tmp_path / 'driver').rglob('*') if p.is_file()}
    assert files == {'42/mod.info', '42/media/models_X/Lofers/SeatedDriver.x',
                     '42/media/textures/Lofers/DriverPalette.png', '42/media/scripts/lofers_driver.txt'}
    assert 'require=' not in (tmp_path / 'driver/42/mod.info').read_text()
