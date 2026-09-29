import tempfile
import unittest
from pathlib import Path
from parkour_snapshot import tiles, compact, capture_plan


class CapturePlanTest(unittest.TestCase):
    def test_negative_odd_and_large_volumes_partition_exactly(self):
        requests=list(tiles({'min': [-35, 1, -2], 'max': [35, 3, 2]}, [0, 0, 0]))
        seen=set()
        for request in requests:
            origin,size=request['origin'],request['size']
            self.assertTrue(all(1<=n<=32 for n in size))
            low=[origin[i]-size[i]//2 for i in range(3)]
            cells={(x,y,z) for x in range(low[0],low[0]+size[0])
                   for y in range(low[1],low[1]+size[1]) for z in range(low[2],low[2]+size[2])}
            self.assertFalse(seen & cells)
            seen |= cells
        self.assertEqual(seen,{(x,y,z) for x in range(-35,36) for y in range(1,4) for z in range(-2,3)})

    def test_origin_offset_and_unknown_cells(self):
        request=next(tiles({'offset': [-.5, 2, 1], 'size': [3, 4, 5]}, [-1, 20, -10]))
        self.assertEqual(request,{'origin': [-2,22,-9], 'size': [3,4,5]})
        with self.assertRaisesRegex(ValueError,'unloaded'):
            compact({'inWorld': True,'player': {},'world': {'palette': ['__unloaded__']}})

    def test_session_change_never_publishes_suite(self):
        with tempfile.TemporaryDirectory() as directory:
            output=Path(directory)/'capture'
            def call(name,args):
                return {'inWorld': True,'sessionId': 'changed' if 'includePhysics' in args else 'original',
                        'player': {'name': 'test', 'dimension': 0, 'originBlock': [0,20,0]}}
            with self.assertRaisesRegex(ValueError,'session changed'):
                capture_plan({'regions': [{'id':'one'}]},output,call)
            self.assertFalse((output/'suite.json').exists())


if __name__ == '__main__':
    unittest.main()
