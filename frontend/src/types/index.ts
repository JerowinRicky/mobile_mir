export interface User {
  id: string;
  email: string;
  first_name: string;
  last_name: string;
  is_active: boolean;
  is_mfa_enabled: boolean;
  created_at: string;
}

export interface Device {
  id: string;
  device_name: string;
  device_model: string;
  android_version: string;
  app_version: string;
  status: 'UNPAIRED' | 'PENDING_APPROVAL' | 'PAIRED' | 'REVOKED';
  last_seen_at: string | null;
  registered_at: string;
}

export interface IceServer {
  urls: string[];
  username?: string;
  credential?: string;
}

export interface TurnConfig {
  ice_servers: IceServer[];
  ttl_seconds: number;
}

export interface RemoteSession {
  id: string;
  device: string;
  device_name: string;
  session_token: string;
  status: 'INITIALIZING' | 'CONNECTING' | 'ACTIVE' | 'TERMINATED' | 'FAILED';
  started_at: string;
  ended_at: string | null;
  duration_seconds: number | null;
  termination_reason: string;
}

export interface NetworkStats {
  rttMs: number;
  bitrateKbps: number;
  packetsLost: number;
  packetLossPercent: number;
  fps: number;
  candidateType: 'host' | 'srflx' | 'relay' | 'unknown';
}

export type InputCommand =
  | { action: 'TAP'; x: number; y: number; duration_ms?: number }
  | { action: 'LONG_PRESS'; x: number; y: number; duration_ms?: number }
  | { action: 'SWIPE'; start_x: number; start_y: number; end_x: number; end_y: number; duration_ms?: number }
  | { action: 'SCROLL'; start_x: number; start_y: number; end_x: number; end_y: number; duration_ms?: number }
  | { action: 'GLOBAL_NAV'; target: 'BACK' | 'HOME' | 'RECENTS' | 'NOTIFICATIONS' | 'QUICK_SETTINGS' | 'LOCK_SCREEN' }
  | { action: 'INPUT_TEXT'; text: string };
