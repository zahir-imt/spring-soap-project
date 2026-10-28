from django.conf import settings
from django.contrib.auth import get_user_model
from django.test import Client, TestCase, override_settings
import re

@override_settings(ALLOWED_HOSTS=['testserver', 'preview.example'], CSRF_TRUSTED_ORIGINS=['https://preview.example', 'http://localhost:8000'])
class SignupCsrfTests(TestCase):
    def setUp(self):
        self.client = Client(enforce_csrf_checks=True)

    def token(self):
        response = self.client.get('/signup/', HTTP_HOST='preview.example', secure=True)
        return re.search(r'name="csrfmiddlewaretoken" value="([^"]+)', response.content.decode())[1]

    def test_codespaces_rewritten_origin_accepts_fresh_form(self):
        token = self.token()
        response = self.client.post('/signup/', {'csrfmiddlewaretoken': token, 'username': 'csrf-test-customer', 'password1': 'Test-only-Strong-7381!', 'password2': 'Test-only-Strong-7381!'}, HTTP_HOST='preview.example', HTTP_ORIGIN='http://localhost:8000')
        self.assertRedirects(response, '/dashboard/')
        self.assertTrue(get_user_model().objects.filter(username='csrf-test-customer').exists())

    def test_public_https_origin_accepts_fresh_form(self):
        response = self.client.post('/signup/', {'csrfmiddlewaretoken': self.token()}, HTTP_HOST='preview.example', HTTP_ORIGIN='https://preview.example', secure=True)
        self.assertEqual(response.status_code, 200)

    def test_untrusted_origin_is_rejected(self):
        response = self.client.post('/signup/', {'csrfmiddlewaretoken': self.token()}, HTTP_HOST='preview.example', HTTP_ORIGIN='https://untrusted.example', secure=True)
        self.assertContains(response, 'Your form could not be verified.', status_code=403)
        self.assertIn('no-store', response.headers['Cache-Control'])

    def test_missing_cookie_is_rejected_and_can_recover(self):
        token = self.token()
        self.client.cookies.clear()
        response = self.client.post('/signup/', {'csrfmiddlewaretoken': token}, HTTP_HOST='preview.example', HTTP_ORIGIN='http://localhost:8000')
        self.assertEqual(response.status_code, 403)
        self.assertContains(response, 'Open fresh signup form', status_code=403)
        response = self.client.post('/signup/', {'csrfmiddlewaretoken': self.token()}, HTTP_HOST='preview.example', HTTP_ORIGIN='http://localhost:8000')
        self.assertEqual(response.status_code, 200)

    def test_mismatched_token_is_rejected_without_echoing_credentials(self):
        self.token()
        response = self.client.post('/signup/', {'csrfmiddlewaretoken': 'a'*64, 'password1': 'do-not-echo-this'}, HTTP_HOST='preview.example', HTTP_ORIGIN='http://localhost:8000')
        self.assertEqual(response.status_code, 403)
        self.assertNotContains(response, 'do-not-echo-this', status_code=403)
