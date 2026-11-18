import React, { useState, useEffect } from 'react';
import { Smartphone, Plus, Play, Trash2, Key, CheckCircle, AlertCircle, Clock } from 'lucide-react';
import { api } from '../services/api';
import { Device, RemoteSession, TurnConfig } from '../types';

interface DashboardPageProps {
  onStartSession: (session: RemoteSession, turnConfig: TurnConfig, device: Device) => void;
}

export const DashboardPage: React.FC<DashboardPageProps> = ({ onStartSession }) => {
  const [devices, setDevices] = useState<Device[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [showPairModal, setShowPairModal] = useState(false);
  const [pin, setPin] = useState('');
  const [pairError, setPairError] = useState<string | null>(null);
  const [isPairing, setIsPairing] = useState(false);
  const [pendingApprovalId, setPendingApprovalId] = useState<string | null>(null);

  const loadDevices = async () => {
    try {
      const data = await api.getDevices();
      setDevices(data);
    } catch (err) {
      console.error('Failed to load devices:', err);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    loadDevices();
  }, []);

  // Poll pairing approval status when waiting for physical confirmation from device
  useEffect(() => {
    if (!pendingApprovalId) return;

    const interval = setInterval(async () => {
      try {
        const res = await api.checkPairingStatus(pendingApprovalId);
        if (res.status === 'APPROVED' || res.status === 'PAIRED') {
          clearInterval(interval);
          setPendingApprovalId(null);
          setShowPairModal(false);
          setPin('');
          loadDevices();
        } else if (res.status === 'FAILED' || res.status === 'EXPIRED') {
          clearInterval(interval);
          setPendingApprovalId(null);
          setPairError(`Pairing ${res.status.toLowerCase()}: Please try again.`);
        }
      } catch {
        // Continue polling until expiration
      }
    }, 2000);

    return () => clearInterval(interval);
  }, [pendingApprovalId]);

  const handleVerifyPin = async (e: React.FormEvent) => {
    e.preventDefault();
    setPairError(null);
    setIsPairing(true);

    try {
      const res = await api.verifyPin(pin);
      setPendingApprovalId(res.pairing_session_id);
    } catch (err: unknown) {
      if (err instanceof Error) {
        setPairError(err.message);
      } else {
        setPairError('PIN verification failed.');
      }
    } finally {
      setIsPairing(false);
    }
  };

  const handleConnect = async (device: Device) => {
    try {
      const { session, turn_config } = await api.createSession(device.id);
      onStartSession(session, turn_config, device);
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : 'Failed to connect to device');
    }
  };

  const handleRevoke = async (deviceId: string) => {
    if (!confirm('Are you sure you want to revoke this device? You will need to re-pair it.')) return;
    try {
      await api.revokeDevice(deviceId);
      loadDevices();
    } catch (err) {
      console.error('Revoke failed:', err);
    }
  };

  return (
    <div className="max-w-6xl mx-auto p-6 space-y-8">
      {/* Header & Pair Action */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-2xl font-bold text-white tracking-tight">Registered Android Devices</h2>
          <p className="text-sm text-slate-400">View live screen stream and control paired smartphones</p>
        </div>

        <button
          onClick={() => {
            setShowPairModal(true);
            setPairError(null);
            setPendingApprovalId(null);
          }}
          className="flex items-center space-x-2 bg-blue-600 hover:bg-blue-500 text-white font-medium px-4 py-2.5 rounded-xl transition shadow-lg shadow-blue-600/20"
        >
          <Plus className="w-4 h-4" />
          <span>Pair New Device</span>
        </button>
      </div>

      {/* Devices Grid */}
      {isLoading ? (
        <div className="text-center py-12 text-slate-500">Loading registered devices...</div>
      ) : devices.length === 0 ? (
        <div className="bg-slate-900 border border-slate-800 rounded-2xl p-12 text-center flex flex-col items-center">
          <Smartphone className="w-12 h-12 text-slate-600 mb-3" />
          <h3 className="text-lg font-semibold text-white">No Paired Devices Yet</h3>
          <p className="text-sm text-slate-400 max-w-sm mt-1 mb-6">
            Open the Remote Device app on your Android phone, generate a temporary pairing PIN, and enter it here.
          </p>
          <button
            onClick={() => setShowPairModal(true)}
            className="bg-blue-600 hover:bg-blue-500 text-white text-sm font-medium px-4 py-2 rounded-xl transition"
          >
            Enter Pairing PIN
          </button>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {devices.map((device) => (
            <div
              key={device.id}
              className="bg-slate-900 border border-slate-800 rounded-2xl p-5 hover:border-slate-700 transition flex flex-col justify-between"
            >
              <div>
                <div className="flex items-start justify-between mb-4">
                  <div className="p-2.5 bg-blue-950/60 border border-blue-800/60 rounded-xl text-blue-400">
                    <Smartphone className="w-6 h-6" />
                  </div>
                  <span
                    className={`text-xs px-2.5 py-1 rounded-full font-semibold ${
                      device.status === 'PAIRED'
                        ? 'bg-emerald-950 text-emerald-400 border border-emerald-800'
                        : 'bg-amber-950 text-amber-400 border border-amber-800'
                    }`}
                  >
                    {device.status}
                  </span>
                </div>

                <h4 className="font-bold text-white text-lg">{device.device_name}</h4>
                <p className="text-xs text-slate-400 font-mono mt-0.5">{device.device_model || 'Android Device'}</p>

                <div className="mt-4 pt-4 border-t border-slate-800 space-y-1.5 text-xs text-slate-400">
                  <div className="flex justify-between">
                    <span>Android Version:</span>
                    <span className="text-slate-200 font-medium">{device.android_version || '14.0'}</span>
                  </div>
                  <div className="flex justify-between">
                    <span>Registered:</span>
                    <span className="text-slate-200 font-medium">
                      {new Date(device.registered_at).toLocaleDateString()}
                    </span>
                  </div>
                </div>
              </div>

              <div className="mt-6 pt-4 flex items-center space-x-3">
                <button
                  onClick={() => handleConnect(device)}
                  disabled={device.status !== 'PAIRED'}
                  className="flex-1 flex items-center justify-center space-x-2 bg-blue-600 hover:bg-blue-500 disabled:bg-slate-800 disabled:text-slate-600 text-white font-medium py-2 rounded-xl transition text-sm"
                >
                  <Play className="w-4 h-4 fill-current" />
                  <span>Connect Screen</span>
                </button>

                <button
                  onClick={() => handleRevoke(device.id)}
                  title="Revoke Device"
                  className="p-2 hover:bg-red-950/40 text-slate-500 hover:text-red-400 rounded-xl border border-transparent hover:border-red-900 transition"
                >
                  <Trash2 className="w-4 h-4" />
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Pairing Modal */}
      {showPairModal && (
        <div className="fixed inset-0 bg-black/70 backdrop-blur-sm flex items-center justify-center p-4 z-50">
          <div className="bg-slate-900 border border-slate-800 rounded-2xl max-w-md w-full p-6 shadow-2xl space-y-6">
            <div className="flex items-center space-x-3">
              <div className="p-2 bg-blue-600 text-white rounded-xl">
                <Key className="w-5 h-5" />
              </div>
              <div>
                <h3 className="font-bold text-lg text-white">Enter Temporary Pairing PIN</h3>
                <p className="text-xs text-slate-400">Enter the 6-digit code shown on your smartphone</p>
              </div>
            </div>

            {pairError && (
              <div className="p-3 bg-red-950 border border-red-800 rounded-xl text-red-300 text-xs flex items-center space-x-2">
                <AlertCircle className="w-4 h-4 shrink-0 text-red-400" />
                <span>{pairError}</span>
              </div>
            )}

            {pendingApprovalId ? (
              <div className="bg-slate-800/80 border border-slate-700 rounded-xl p-5 text-center space-y-3">
                <Clock className="w-8 h-8 text-amber-400 mx-auto animate-spin" />
                <h4 className="font-semibold text-white">Awaiting Smartphone Confirmation</h4>
                <p className="text-xs text-slate-300">
                  Please tap <span className="font-bold text-emerald-400">"Approve"</span> on the connection request
                  prompt displayed on your Android phone.
                </p>
              </div>
            ) : (
              <form onSubmit={handleVerifyPin} className="space-y-4">
                <div>
                  <input
                    type="text"
                    required
                    maxLength={7}
                    value={pin}
                    onChange={(e) => setPin(e.target.value)}
                    placeholder="849-201"
                    className="w-full bg-slate-950 border border-slate-700 rounded-xl py-3 text-center font-mono text-2xl font-bold tracking-widest text-emerald-400 placeholder-slate-600 focus:outline-none focus:border-blue-500 transition"
                  />
                  <p className="text-[11px] text-slate-500 text-center mt-1.5">
                    PIN expires in 5 minutes. Maximum 3 failed attempts before lockout.
                  </p>
                </div>

                <div className="flex space-x-3 pt-2">
                  <button
                    type="button"
                    onClick={() => setShowPairModal(false)}
                    className="flex-1 py-2.5 bg-slate-800 hover:bg-slate-700 text-slate-300 rounded-xl text-sm font-medium transition"
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    disabled={isPairing || pin.length < 6}
                    className="flex-1 py-2.5 bg-blue-600 hover:bg-blue-500 disabled:opacity-50 text-white rounded-xl text-sm font-semibold transition"
                  >
                    {isPairing ? 'Verifying...' : 'Verify PIN'}
                  </button>
                </div>
              </form>
            )}
          </div>
        </div>
      )}
    </div>
  );
};
