"""Behavioral tests for the NDJSON event reader; no running Minecraft needed."""
import importlib.util
import io
import json
from pathlib import Path
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('mcp_client', Path(__file__).resolve().parents[1] / 'skills/mythosscript-mcp/scripts/client.py')
client = importlib.util.module_from_spec(spec)
spec.loader.exec_module(client)

class FakeClient:
    def __init__(self, replies):
        self.replies = iter(replies)
        self.requests = []
    def send(self, request):
        self.requests.append(dict(request['params']['arguments']))
        return {'result': {'structuredContent': next(self.replies)}}

class WatchTest(unittest.TestCase):
    def result(self, **extra):
        return dict(sessionId='world-a', nextAfterId=11, connected=True, events=[],
                    hasMore=False, sessionChanged=False, cursorExpired=False, cursorAhead=False, **extra)
    def test_tail_uses_baseline_and_emits_events_plus_checkpoint(self):
        baseline = self.result()
        next_page = self.result()
        next_page.update(nextAfterId=12, events=[{'id':12, 'type':'health_changed'}])
        fake = FakeClient([baseline,next_page]); output=io.StringIO()
        with patch.object(client.time,'monotonic',side_effect=[0,0.1,0.1,2]):
            client.follow_events(fake,{'groups':['entities','gui']},1,output)
        self.assertEqual(fake.requests[1]['afterId'],11)
        self.assertEqual(fake.requests[1]['sessionId'],'world-a')
        self.assertEqual(fake.requests[1]['groups'],['entities','gui'])
        rows=[json.loads(s) for s in output.getvalue().splitlines()]
        self.assertEqual(rows[1]['id'],12)
        self.assertEqual(rows[2]['nextAfterId'],12)
    def test_explicit_cursor_does_not_skip_history_and_reports_gaps(self):
        page=self.result();page.update(cursorExpired=True,sessionChanged=True)
        fake=FakeClient([page]);output=io.StringIO()
        with patch.object(client.time,'monotonic',side_effect=[0,0.1,0.1,2]):
            client.follow_events(fake,{'afterId':3,'sessionId':'old'},1,output)
        self.assertEqual(len(fake.requests),1)
        self.assertEqual(fake.requests[0]['afterId'],3)
        self.assertTrue(json.loads(output.getvalue())['cursorExpired'])

if __name__ == '__main__':
    unittest.main()
