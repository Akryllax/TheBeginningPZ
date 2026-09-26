"""Geometry and traffic policy boundaries, without a game world."""
import sys
import math
from pathlib import Path
import pytest
sys.path.insert(0,str(Path(__file__).parents[1]/'scripts'))
from scenario_lanes import footprint_cells, swept_lane_cells, bezier_samples, bezier_cells
from vehicle_probe_ops import traffic_settings


def test_oriented_footprint_keeps_northbound_car_inside_lane():
    cells=set(footprint_cells(10820.5,9845.5,0,-1))
    assert {x for x,y in cells}=={10819,10820,10821}
    assert {y for x,y in cells}==set(range(9843,9848))
    assert (10822,9845) not in cells


def test_sweep_contains_both_endpoint_footprints_and_corner_rotation():
    points=[(0,0),(5,0),(5,-5)]
    cells=swept_lane_cells(points)
    for p,heading in [((0,0),(1,0)),((5,0),(1,0)),((5,0),(0,-1)),((5,-5),(0,-1))]:
        assert set(footprint_cells(*p,*heading))<=cells


@pytest.mark.parametrize('settings',[{'speed_kmh':15},{'lane_mode':'true'},{'lane_mode':True,'speed_kmh':16},
 {'lane_mode':True,'stops':[{'progress':float('nan'),'hold_seconds':2}]},
 {'stops':[{'progress':5,'hold_seconds':0}]},
 {'stops':[{'progress':5,'hold_seconds':2},{'progress':4,'hold_seconds':2}]}])
def test_invalid_traffic_options(settings):
    with pytest.raises(ValueError):traffic_settings(settings,[(0,0),(20,0)])


def test_default_course_stays_slow_and_lane_stop_is_explicit():
    assert traffic_settings({},[(0,0),(20,0)])==(False,4,[])
    assert traffic_settings({'lane_mode':True,'speed_kmh':15,'stops':[{'progress':5,'hold_seconds':2}]},[(0,0),(20,0)])==(True,15,[(5,2)])


def line(a,b):
    return [{'x':a[0]+(b[0]-a[0])*t,'y':a[1]+(b[1]-a[1])*t} for t in (0,1/3,2/3,1)]


def test_bezier_quarter_turn_tangents_length_and_footprint():
    k=6*4/3*math.tan(math.pi/8)
    curve=[[{'x':0,'y':6},{'x':k,'y':6},{'x':6,'y':k},{'x':6,'y':0}]]
    samples=list(bezier_samples(curve))
    assert samples[0]==pytest.approx((0,6,1,0))
    assert samples[-1]==pytest.approx((6,0,0,-1))
    assert samples[64][2:]==pytest.approx((math.sqrt(.5),-math.sqrt(.5)))
    length=sum(math.dist(a[:2],b[:2]) for a,b in zip(samples,samples[1:]))
    assert length==pytest.approx(3*math.pi,abs=.003)
    cells=bezier_cells(curve)
    assert set(footprint_cells(0,6,1,0))<=cells
    assert set(footprint_cells(6,0,0,-1))<=cells


@pytest.mark.parametrize('curves',[
    [], [line((0,0),(10,0)),line((11,0),(20,0))],
    [line((0,0),(10,0)),line((10,0),(10,10))],
    [[{'x':0,'y':0},{'x':float('nan'),'y':0},{'x':2,'y':0},{'x':3,'y':0}]],
    [[{'x':0,'y':0},{'x':3,'y':0},{'x':2,'y':0},{'x':4,'y':0}]],
    [line((0,0),(70,0))], [line((0,0),(1,0))],
])
def test_bezier_rejects_unsafe_or_unbounded_geometry(curves):
    with pytest.raises(ValueError):list(bezier_samples(curves))
