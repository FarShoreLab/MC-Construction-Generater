"""Semantic checks the JSON Schema cannot express: occupancy, layers and entrances."""
import copy
import json
from pathlib import Path
import unittest
from compile_building_preset import compile_preset

EXAMPLE=Path(__file__).resolve().parents[1]/'handoff/building-preset-standard-v1/example.layout.json'

class CompilerTests(unittest.TestCase):
    def setUp(self):self.source=json.loads(EXAMPLE.read_text(encoding='utf-8'))
    def test_example_roundtrip(self):
        expected=json.loads(EXAMPLE.with_name('example.native.json').read_text(encoding='utf-8'))
        self.assertEqual(expected,compile_preset(self.source))
        self.assertEqual({'main','annex','courtyard'},{p['role'] for p in expected['components']})
    def test_rejects_invalid_layout(self):
        def overlap(s):s['components'].append(copy.deepcopy(s['components'][0]));s['components'][-1]['id']='overlap'
        def outside(s):s['components'][0]['origin']=[31,31]
        def missing_yard(s):s['components'].pop()
        def inward_door(s):s['entrances'][0]['facing']='NORTH'
        def oversized(s):s['size'][0]=48
        def disconnected(s):s['reservedMask'][9]='.'*19
        def blocked_door(s):
            part=s['components'][-1];part['layers'].append(['.'*19 for _ in range(19)]);part['layers'][1][18]='.'*10+'g'+'.'*8
        def unknown_symbol(s):s['components'][0]['layers'][0][0]='?'*len(s['components'][0]['mask'][0])
        for mutate in [overlap,outside,missing_yard,inward_door,oversized,disconnected,blocked_door,unknown_symbol]:
            with self.subTest(mutate=mutate.__name__):
                s=copy.deepcopy(self.source);mutate(s)
                with self.assertRaises(ValueError):compile_preset(s)

if __name__=='__main__':unittest.main()
