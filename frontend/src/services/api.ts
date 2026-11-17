import { User, Device, TurnConfig, RemoteSession } from '../types';

const BASE_URL = import.meta.env.VITE_API_URL || 'http://127.0.0.1:8000/api/v1';

class ApiService {
  private getAccessToken(): string | null {
    return localStorage.getItem('access_token');
  }

  private async request<T>(endpoint: string, options: RequestInit = {}): Promise<T> {
    const token = this.getAccessToken();
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      ...(options.headers as Record<string, string>),
    };

    const isPublicAuthEndpoint = endpoint.includes('/auth/login') || endpoint.includes('/auth/register') || endpoint.includes('/auth/refresh');
    if (token && !isPublicAuthEndpoint) {
      headers['Authorization'] = `Bearer ${token}`;
    }

    const response = await fetch(`${BASE_URL}${endpoint}`, {
      ...options,
      headers,
    });

    if (response.status === 401 && !isPublicAuthEndpoint) {
      // Token might be expired, attempt refresh
      const refreshed = await this.refreshToken();
      if (refreshed) {
        return this.request<T>(endpoint, options);
      } else {
        localStorage.removeItem('access_token');
        localStorage.removeItem('refresh_token');
        window.location.href = '/login';
        throw new Error('Session expired');
      }
    }

    if (!response.ok) {
      const errorData = await response.json().catch(() => ({}));
      throw new Error(errorData.detail || errorData.message || `Request failed: ${response.status}`);
    }

    return response.json();
  }

  private async refreshToken(): Promise<boolean> {
    const refreshToken = localStorage.getItem('refresh_token');
    if (!refreshToken) return false;

    try {
      const res = await fetch(`${BASE_URL}/auth/refresh/`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refresh_token: refreshToken }),
      });
      if (res.ok) {
        const data = await res.json();
        localStorage.setItem('access_token', data.access_token);
        localStorage.setItem('refresh_token', data.refresh_token);
        return true;
      }
      return false;
    } catch {
      return false;
    }
  }

  // Auth endpoints
  async register(email: string, password: string): Promise<{ user: User; access_token: string; refresh_token: string }> {
    localStorage.removeItem('access_token');
    localStorage.removeItem('refresh_token');
    const data = await this.request<{ user: User; access_token: string; refresh_token: string }>('/auth/register/', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    });
    localStorage.setItem('access_token', data.access_token);
    localStorage.setItem('refresh_token', data.refresh_token);
    return data;
  }

  async login(email: string, password: string): Promise<{ user: User; access_token: string; refresh_token: string }> {
    localStorage.removeItem('access_token');
    localStorage.removeItem('refresh_token');
    const data = await this.request<{ user: User; access_token: string; refresh_token: string }>('/auth/login/', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    });
    localStorage.setItem('access_token', data.access_token);
    localStorage.setItem('refresh_token', data.refresh_token);
    return data;
  }

  async getMe(): Promise<User> {
    return this.request<User>('/auth/me/');
  }

  // Devices
  async getDevices(): Promise<Device[]> {
    return this.request<Device[]>('/devices/');
  }

  async revokeDevice(deviceId: string): Promise<{ message: string }> {
    return this.request<{ message: string }>(`/devices/${deviceId}/revoke/`, {
      method: 'POST',
    });
  }

  // Pairing
  async verifyPin(pin: string): Promise<{ pairing_session_id: string; device_id: string; device_name: string; status: string; message: string }> {
    return this.request('/pairing/verify/', {
      method: 'POST',
      body: JSON.stringify({ pin }),
    });
  }

  async checkPairingStatus(sessionId: string): Promise<{ status: string; device_id: string }> {
    return this.request(`/pairing/status/${sessionId}/`);
  }

  // Sessions & TURN
  async createSession(deviceId: string): Promise<{ session: RemoteSession; turn_config: TurnConfig }> {
    return this.request('/sessions/create/', {
      method: 'POST',
      body: JSON.stringify({ device_id: deviceId }),
    });
  }

  async terminateSession(sessionId: string, reason = 'User Closed'): Promise<{ message: string; duration_seconds: number }> {
    return this.request(`/sessions/${sessionId}/terminate/`, {
      method: 'POST',
      body: JSON.stringify({ reason }),
    });
  }

  async getTurnCredentials(): Promise<TurnConfig> {
    return this.request<TurnConfig>('/sessions/turn-credentials/');
  }
}

export const api = new ApiService();
