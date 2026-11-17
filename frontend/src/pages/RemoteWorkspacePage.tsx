import React, { useState } from 'react';
import { ArrowLeft, Home, RotateCcw, Power, Send, Type, Bell, Sliders, Lock, Info } from 'lucide-react';
import { useWebRTC } from '../hooks/useWebRTC';
import { NetworkQualityHud } from '../components/NetworkQualityHud';
import { VideoPlayer } from '../components/VideoPlayer';
import { Device, RemoteSession, TurnConfig } from '../types';
import { api } from '../services/api';

interface RemoteWorkspacePageProps {
  session: RemoteSession;
  turnConfig: TurnConfig;
  device: Device;
  onExit: () => void;
}

export const RemoteWorkspacePage: React.FC<RemoteWorkspacePageProps> = ({
  session,
  turnConfig,
  device,
  onExit,
}) => {
  const [textInput, setTextInput] = useState('');
  const accessToken = localStorage.getItem('access_token') || '';

  const { remoteStream, connectionState, stats, sendInputCommand, reconnect } = useWebRTC({
    sessionId: session.id,
    token: accessToken,
    turnConfig,
    onDisconnected: () => {
      console.log('WebRTC session ended');
    },
  });

  const handleDisconnect = async () => {
    try {
      await api.terminateSession(session.id, 'User Terminated from Web Dashboard');
    } catch (e) {
      console.error('Error terminating session:', e);
    } finally {
      onExit();
    }
  };

  const handleGlobalNav = (
    target: 'BACK' | 'HOME' | 'RECENTS' | 'NOTIFICATIONS' | 'QUICK_SETTINGS' | 'LOCK_SCREEN'
  ) => {
    sendInputCommand({
      action: 'GLOBAL_NAV',
      target,
    });
  };

  const handleSendText = (e: React.FormEvent) => {
    e.preventDefault();
    if (!textInput.trim()) return;

    sendInputCommand({
      action: 'INPUT_TEXT',
      text: textInput,
    });
    setTextInput('');
  };

  return (
    <div className="max-w-5xl mx-auto p-4 sm:p-6 space-y-4">
      {/* Top Header & Disconnect */}
      <div className="flex flex-col sm:flex-row items-center justify-between gap-4 bg-slate-900 border border-slate-800 p-4 rounded-2xl">
        <div className="flex items-center space-x-3">
          <button
            onClick={handleDisconnect}
            className="flex items-center space-x-1.5 text-slate-400 hover:text-white bg-slate-800 hover:bg-slate-700 px-3 py-1.5 rounded-xl border border-slate-700 text-xs font-medium transition"
          >
            <ArrowLeft className="w-4 h-4" />
            <span>Devices</span>
          </button>
          <div>
            <h3 className="font-bold text-white text-sm leading-none">{device.device_name}</h3>
            <span className="text-[11px] text-slate-400 font-mono">Interactive Remote Control Mode</span>
          </div>
        </div>

        <button
          onClick={handleDisconnect}
          className="flex items-center space-x-1.5 bg-red-600 hover:bg-red-500 text-white px-3.5 py-1.5 rounded-xl text-xs font-semibold transition shadow-lg shadow-red-600/20"
        >
          <Power className="w-3.5 h-3.5" />
          <span>Disconnect</span>
        </button>
      </div>

      <NetworkQualityHud stats={stats} connectionState={connectionState} onReconnect={reconnect} />

      {/* Main Video Viewport with Pointer Tracking */}
      <VideoPlayer stream={remoteStream} onSendCommand={sendInputCommand} deviceName={device.device_name} />

      {/* Remote Navigation Control Dock */}
      <div className="bg-slate-900 border border-slate-800 rounded-2xl p-4 flex flex-col md:flex-row items-center justify-between gap-4">
        {/* Android Navigation Keys */}
        <div className="flex flex-wrap items-center gap-2">
          <span className="text-xs text-slate-400 mr-1 font-medium">System Controls:</span>

          <button
            onClick={() => handleGlobalNav('BACK')}
            title="Android Back"
            className="flex items-center space-x-1 bg-slate-800 hover:bg-slate-700 border border-slate-700 px-3 py-1.5 rounded-xl text-slate-200 text-xs font-semibold transition"
          >
            <RotateCcw className="w-3.5 h-3.5 text-blue-400" />
            <span>Back</span>
          </button>

          <button
            onClick={() => handleGlobalNav('HOME')}
            title="Android Home"
            className="flex items-center space-x-1 bg-slate-800 hover:bg-slate-700 border border-slate-700 px-3 py-1.5 rounded-xl text-slate-200 text-xs font-semibold transition"
          >
            <Home className="w-3.5 h-3.5 text-emerald-400" />
            <span>Home</span>
          </button>

          <button
            onClick={() => handleGlobalNav('RECENTS')}
            title="Android Recents (Overview)"
            className="flex items-center space-x-1 bg-slate-800 hover:bg-slate-700 border border-slate-700 px-3 py-1.5 rounded-xl text-slate-200 text-xs font-semibold transition"
          >
            <span className="w-3 h-3 border-2 border-amber-400 rounded-sm inline-block" />
            <span>Recents</span>
          </button>

          <button
            onClick={() => handleGlobalNav('NOTIFICATIONS')}
            title="Pull Down Notifications Shade"
            className="flex items-center space-x-1 bg-slate-800 hover:bg-slate-700 border border-slate-700 px-3 py-1.5 rounded-xl text-slate-200 text-xs font-semibold transition"
          >
            <Bell className="w-3.5 h-3.5 text-purple-400" />
            <span>Notifications</span>
          </button>

          <button
            onClick={() => handleGlobalNav('QUICK_SETTINGS')}
            title="Open Quick Settings"
            className="flex items-center space-x-1 bg-slate-800 hover:bg-slate-700 border border-slate-700 px-3 py-1.5 rounded-xl text-slate-200 text-xs font-semibold transition"
          >
            <Sliders className="w-3.5 h-3.5 text-cyan-400" />
            <span>Settings</span>
          </button>

          <button
            onClick={() => handleGlobalNav('LOCK_SCREEN')}
            title="Turn Off / Lock Screen"
            className="flex items-center space-x-1 bg-slate-800 hover:bg-slate-700 border border-slate-700 px-3 py-1.5 rounded-xl text-slate-200 text-xs font-semibold transition"
          >
            <Lock className="w-3.5 h-3.5 text-rose-400" />
            <span>Lock</span>
          </button>
        </div>

        {/* Text Input Dispatcher */}
        <form onSubmit={handleSendText} className="flex items-center space-x-2 w-full md:w-auto">
          <div className="relative flex-1 md:w-64">
            <Type className="w-4 h-4 absolute left-3 top-2.5 text-slate-500" />
            <input
              type="text"
              value={textInput}
              onChange={(e) => setTextInput(e.target.value)}
              placeholder="Send text to focused field..."
              className="w-full bg-slate-950 border border-slate-700 rounded-xl py-2 pl-9 pr-3 text-xs text-white placeholder-slate-500 focus:outline-none focus:border-blue-500 transition"
            />
          </div>
          <button
            type="submit"
            disabled={!textInput.trim()}
            className="bg-blue-600 hover:bg-blue-500 disabled:opacity-40 text-white p-2 rounded-xl transition"
            title="Inject Text"
          >
            <Send className="w-4 h-4" />
          </button>
        </form>
      </div>

      {/* Interaction Tips Banner */}
      <div className="flex items-center space-x-2 text-[11px] text-slate-500 bg-slate-900/60 border border-slate-800/80 px-4 py-2 rounded-xl">
        <Info className="w-3.5 h-3.5 shrink-0 text-slate-400" />
        <span>
          <strong className="text-slate-300">Gesture Guide:</strong> Click = Tap • Click & Hold = Long Press • Click &
          Drag = Swipe • Mouse Wheel = Smooth Scroll • Backspace/Esc = Back/Home.
        </span>
      </div>
    </div>
  );
};
