import React from 'react';
import { Wifi, Activity, Gauge, RefreshCw } from 'lucide-react';
import { NetworkStats } from '../types';

interface NetworkQualityHudProps {
  stats: NetworkStats;
  connectionState: RTCPeerConnectionState;
  onReconnect: () => void;
}

export const NetworkQualityHud: React.FC<NetworkQualityHudProps> = ({ stats, connectionState, onReconnect }) => {
  const getRouteBadge = () => {
    switch (stats.candidateType) {
      case 'host':
        return <span className="bg-emerald-950 text-emerald-400 border border-emerald-800 text-xs px-2 py-0.5 rounded font-mono">P2P LAN</span>;
      case 'srflx':
        return <span className="bg-blue-950 text-blue-400 border border-blue-800 text-xs px-2 py-0.5 rounded font-mono">P2P WAN (STUN)</span>;
      case 'relay':
        return <span className="bg-amber-950 text-amber-400 border border-amber-800 text-xs px-2 py-0.5 rounded font-mono">Relayed (TURN)</span>;
      default:
        return <span className="bg-slate-800 text-slate-400 border border-slate-700 text-xs px-2 py-0.5 rounded font-mono">Negotiating</span>;
    }
  };

  const getLatencyColor = () => {
    if (stats.rttMs < 50) return 'text-emerald-400';
    if (stats.rttMs < 120) return 'text-amber-400';
    return 'text-red-400';
  };

  return (
    <div className="bg-slate-900 border border-slate-800 rounded-xl p-3 flex flex-wrap items-center justify-between gap-4 text-xs">
      <div className="flex items-center space-x-6">
        {/* Connection State */}
        <div className="flex items-center space-x-2">
          <span className={`w-2.5 h-2.5 rounded-full ${connectionState === 'connected' ? 'bg-emerald-500 animate-pulse' : 'bg-amber-500'}`} />
          <span className="font-semibold text-slate-200 capitalize">{connectionState}</span>
        </div>

        {/* Route Type */}
        <div className="flex items-center space-x-1.5">
          <Wifi className="w-3.5 h-3.5 text-slate-400" />
          <span className="text-slate-400">Route:</span>
          {getRouteBadge()}
        </div>

        {/* Latency (RTT) */}
        <div className="flex items-center space-x-1.5">
          <Activity className="w-3.5 h-3.5 text-slate-400" />
          <span className="text-slate-400">RTT:</span>
          <span className={`font-mono font-bold ${getLatencyColor()}`}>{stats.rttMs} ms</span>
        </div>

        {/* Bitrate */}
        <div className="flex items-center space-x-1.5">
          <Gauge className="w-3.5 h-3.5 text-slate-400" />
          <span className="text-slate-400">Bitrate:</span>
          <span className="font-mono font-bold text-slate-200">{stats.bitrateKbps} kbps</span>
        </div>

        {/* Framerate */}
        <div className="flex items-center space-x-1.5">
          <span className="text-slate-400">FPS:</span>
          <span className="font-mono font-bold text-slate-200">{stats.fps}</span>
        </div>
      </div>

      <button
        onClick={onReconnect}
        title="Trigger ICE Restart"
        className="flex items-center space-x-1 text-slate-400 hover:text-white bg-slate-800 hover:bg-slate-700 px-2.5 py-1.5 rounded-lg border border-slate-700 transition"
      >
        <RefreshCw className="w-3.5 h-3.5" />
        <span>Reconnect</span>
      </button>
    </div>
  );
};
