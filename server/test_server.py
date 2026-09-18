import json
import os
import threading
import unittest
import urllib.error
import urllib.request
from http.server import ThreadingHTTPServer
from unittest.mock import patch
from server import validate, extract_text, RateLimit, Handler

class GatewayTest(unittest.TestCase):
    def test_multilingual_payload(self):
        self.assertEqual(validate({'draft':'നാളെ meet at 4', 'instruction':'Polish'})[0], 'നാളെ meet at 4')
    def test_rejects_invalid_inputs(self):
        for draft in ['', ' '*8, 'a'*8001, 42, None]:
            with self.assertRaises(ValueError): validate({'draft':draft,'instruction':'Polish'})
        with self.assertRaises(ValueError): validate([])
    def test_limits_instruction(self):
        with self.assertRaises(ValueError): validate({'draft':'Hello','instruction':'a'*1001})
    def test_does_not_return_truncated_or_blocked_output(self):
        for reason in ['MAX_TOKENS','SAFETY']:
            with self.assertRaises(ValueError): extract_text({'candidates':[{'finishReason':reason,'content':{'parts':[{'text':'Partial'}]}}]})
    def test_ignores_reasoning_output(self):
        self.assertEqual(extract_text({'candidates':[{'finishReason':'STOP','content':{'parts':[{'thought':True,'text':'secret'},{'text':'Hello.'}]}}]}),'Hello.')
    def test_sliding_rate_limit(self):
        limiter=RateLimit(2,60)
        self.assertTrue(limiter.allow(0));self.assertTrue(limiter.allow(1));self.assertFalse(limiter.allow(59));self.assertTrue(limiter.allow(60))

class HttpTest(unittest.TestCase):
    def setUp(self):
        self.env=patch.dict(os.environ, {'TYPER_ACCESS_TOKEN':'a'*32});self.env.start()
        Handler.limiter=RateLimit()
        self.server=ThreadingHTTPServer(('127.0.0.1',0),Handler)
        self.thread=threading.Thread(target=self.server.serve_forever,daemon=True);self.thread.start()
        self.url=f'http://127.0.0.1:{self.server.server_port}/rewrite'
    def tearDown(self):
        self.server.shutdown();self.server.server_close();self.thread.join();self.env.stop()
    def post(self,token='a'*32,body=None):
        data=json.dumps(body or {'draft':'um Hello','instruction':'Polish'}).encode()
        req=urllib.request.Request(self.url,data=data,headers={'Content-Type':'application/json','Authorization':'Bearer '+token})
        try:
            with urllib.request.urlopen(req) as r:return r.status,json.load(r)
        except urllib.error.HTTPError as e:return e.code,json.load(e)
    def test_auth_before_provider(self):
        with patch('server.rewrite') as call:
            self.assertEqual(self.post('wrong')[0],401);call.assert_not_called()
    def test_returns_provider_text(self):
        with patch('server.rewrite',return_value='Hello.'):
            self.assertEqual(self.post(),(200,{'text':'Hello.'}))
    def test_hides_provider_errors(self):
        with patch('server.rewrite',side_effect=RuntimeError('private draft or key')):
            code,body=self.post();self.assertEqual(code,502);self.assertNotIn('private',json.dumps(body))
    def test_rejects_oversized_draft(self):
        with patch('server.rewrite') as call:
            self.assertEqual(self.post(body={'draft':'x'*8001,'instruction':'Polish'})[0],400);call.assert_not_called()

if __name__=='__main__':unittest.main()
