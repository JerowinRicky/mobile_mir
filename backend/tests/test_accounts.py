import unittest
from django.test import TestCase
from rest_framework.test import APIClient
from rest_framework import status
from apps.accounts.models import User
from apps.accounts.authentication import generate_access_token

class AccountsAuthTestCase(TestCase):
    def setUp(self):
        self.client = APIClient()
        self.register_url = '/api/v1/auth/register/'
        self.login_url = '/api/v1/auth/login/'
        self.refresh_url = '/api/v1/auth/refresh/'
        self.me_url = '/api/v1/auth/me/'

        self.user_data = {
            'email': 'security_test@example.com',
            'password': 'StrongPassword2026!',
            'first_name': 'Alice',
            'last_name': 'Dev'
        }

    def test_user_registration_success(self):
        response = self.client.post(self.register_url, self.user_data, format='json')
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
        self.assertIn('access_token', response.data)
        self.assertIn('refresh_token', response.data)
        self.assertEqual(response.data['user']['email'], 'security_test@example.com')

    def test_duplicate_user_registration_fails(self):
        self.client.post(self.register_url, self.user_data, format='json')
        response = self.client.post(self.register_url, self.user_data, format='json')
        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)

    def test_user_login_success(self):
        User.objects.create_user(
            email='alice@example.com',
            password='StrongPassword2026!'
        )
        response = self.client.post(self.login_url, {
            'email': 'alice@example.com',
            'password': 'StrongPassword2026!'
        }, format='json')

        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertIn('access_token', response.data)
        self.assertIn('refresh_token', response.data)

    def test_user_login_invalid_password_fails(self):
        User.objects.create_user(
            email='alice@example.com',
            password='StrongPassword2026!'
        )
        response = self.client.post(self.login_url, {
            'email': 'alice@example.com',
            'password': 'WrongPassword!'
        }, format='json')

        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)

    def test_refresh_token_rotation(self):
        user = User.objects.create_user(
            email='alice@example.com',
            password='StrongPassword2026!'
        )
        login_res = self.client.post(self.login_url, {
            'email': 'alice@example.com',
            'password': 'StrongPassword2026!'
        }, format='json')

        refresh_token = login_res.data['refresh_token']
        refresh_res = self.client.post(self.refresh_url, {'refresh_token': refresh_token}, format='json')

        self.assertEqual(refresh_res.status_code, status.HTTP_200_OK)
        self.assertIn('access_token', refresh_res.data)
        self.assertIn('refresh_token', refresh_res.data)

    def test_me_endpoint_requires_auth(self):
        unauth_res = self.client.get(self.me_url)
        self.assertIn(unauth_res.status_code, [status.HTTP_401_UNAUTHORIZED, status.HTTP_403_FORBIDDEN])

        user = User.objects.create_user(email='alice@example.com', password='Password123!')
        token = generate_access_token(user)
        self.client.credentials(HTTP_AUTHORIZATION=f'Bearer {token}')

        auth_res = self.client.get(self.me_url)
        self.assertEqual(auth_res.status_code, status.HTTP_200_OK)
        self.assertEqual(auth_res.data['email'], 'alice@example.com')
