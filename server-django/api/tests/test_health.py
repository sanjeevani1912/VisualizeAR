from django.test import SimpleTestCase


class HealthTests(SimpleTestCase):
    def test_health_is_ok_and_uncached(self):
        res = self.client.get("/api/health")
        self.assertEqual(res.status_code, 200)
        self.assertEqual(res.json(), {"ok": True})
        self.assertEqual(res["Cache-Control"], "no-store")
        self.assertEqual(res["X-Frame-Options"], "DENY")
        self.assertEqual(res["X-Content-Type-Options"], "nosniff")
