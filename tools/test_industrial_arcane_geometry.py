"""Geometry checks for the face-clipping fix; no Minecraft process is needed."""
import unittest
from industrial_arcane_models import exposed,model,subtract

class ExposedSurfaces(unittest.TestCase):
    def test_duplicate_box_has_six_faces(self):
        self.assertEqual(6,len(exposed([([0,0,0],[1,1,1],'stone')]*2)))
    def test_touching_boxes_hide_both_internal_faces(self):
        self.assertEqual(10,len(exposed([([0,0,0],[1,1,1],'stone'),([1,0,0],[2,1,1],'gold')])))
    def test_rectangle_clipping_preserves_exact_remaining_area(self):
        self.assertEqual(12,sum((c-a)*(d-b) for a,b,c,d in subtract((0,0,4,4),(1,1,3,3))))
    def test_thermal_and_nuclear_have_no_duplicate_coplanar_surfaces(self):
        for family in ['thermal_generator','nuclear_reactor']:
            groups={}
            for e in model(family,3)['elements']:
                face=next(iter(e['faces']));axis={'west':0,'east':0,'up':1,'down':1,'north':2,'south':2}[face]
                other=[i for i in range(3) if i!=axis];u,v=other
                key=(face,e['from'][axis]);r=(e['from'][u],e['from'][v],e['to'][u],e['to'][v])
                for a,b,c,d in groups.setdefault(key,[]):
                    self.assertFalse(max(a,r[0])<min(c,r[2]) and max(b,r[1])<min(d,r[3]),(family,key,r))
                groups[key].append(r)

if __name__=='__main__':unittest.main()
