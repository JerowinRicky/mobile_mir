import React, { useRef, useEffect, useState, useCallback } from 'react';
import { Maximize2, Minimize2, Smartphone } from 'lucide-react';
import { InputCommand } from '../types';

interface VideoPlayerProps {
  stream: MediaStream | null;
  onSendCommand: (cmd: InputCommand) => void;
  deviceName: string;
}

export const VideoPlayer: React.FC<VideoPlayerProps> = ({ stream, onSendCommand, deviceName }) => {
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const containerRef = useRef<HTMLDivElement | null>(null);
  const [isFullscreen, setIsFullscreen] = useState(false);
  const [touchIndicator, setTouchIndicator] = useState<{ x: number; y: number } | null>(null);

  // Drag / Swipe tracking state
  const isDraggingRef = useRef(false);
  const startPosRef = useRef<{ x: number; y: number; time: number } | null>(null);

  useEffect(() => {
    const video = videoRef.current;
    if (!video || !stream) return;

    video.srcObject = stream;

    const playVideo = () => {
      video.play().catch((err) => {
        console.warn('[VideoPlayer] Play interrupted/prevented:', err);
      });
    };

    video.onloadedmetadata = playVideo;
    playVideo();
  }, [stream]);

  const toggleFullscreen = () => {
    if (!containerRef.current) return;
    if (!document.fullscreenElement) {
      containerRef.current.requestFullscreen();
      setIsFullscreen(true);
    } else {
      document.exitFullscreen();
      setIsFullscreen(false);
    }
  };

  const getNormalizedCoordinates = useCallback((e: React.MouseEvent<HTMLVideoElement>) => {
    const video = videoRef.current;
    if (!video) return null;

    const rect = video.getBoundingClientRect();
    const x = (e.clientX - rect.left) / rect.width;
    const y = (e.clientY - rect.top) / rect.height;

    return {
      x: Math.max(0, Math.min(1, x)),
      y: Math.max(0, Math.min(1, y)),
      pixelX: e.clientX - rect.left,
      pixelY: e.clientY - rect.top,
    };
  }, []);

  const handleMouseDown = (e: React.MouseEvent<HTMLVideoElement>) => {
    e.preventDefault();
    const coords = getNormalizedCoordinates(e);
    if (!coords) return;

    isDraggingRef.current = true;
    startPosRef.current = {
      x: coords.x,
      y: coords.y,
      time: Date.now(),
    };

    setTouchIndicator({ x: coords.pixelX, y: coords.pixelY });
  };

  const handleMouseUp = (e: React.MouseEvent<HTMLVideoElement>) => {
    e.preventDefault();
    if (!isDraggingRef.current || !startPosRef.current) return;
    const coords = getNormalizedCoordinates(e);
    if (!coords) return;

    const start = startPosRef.current;
    const duration = Date.now() - start.time;
    const deltaX = Math.abs(coords.x - start.x);
    const deltaY = Math.abs(coords.y - start.y);

    if (deltaX < 0.02 && deltaY < 0.02) {
      // Tap or Long-press
      if (duration > 650) {
        onSendCommand({
          action: 'LONG_PRESS',
          x: coords.x,
          y: coords.y,
          duration_ms: duration,
        });
      } else {
        onSendCommand({
          action: 'TAP',
          x: coords.x,
          y: coords.y,
          duration_ms: Math.max(40, duration),
        });
      }
    } else {
      // Swipe / Drag gesture
      onSendCommand({
        action: 'SWIPE',
        start_x: start.x,
        start_y: start.y,
        end_x: coords.x,
        end_y: coords.y,
        duration_ms: Math.max(120, duration),
      });
    }

    isDraggingRef.current = false;
    startPosRef.current = null;
    setTimeout(() => setTouchIndicator(null), 250);
  };

  const handleWheel = (e: React.WheelEvent<HTMLVideoElement>) => {
    e.preventDefault();
    const video = videoRef.current;
    if (!video) return;

    const rect = video.getBoundingClientRect();
    const x = (e.clientX - rect.left) / rect.width;
    const y = (e.clientY - rect.top) / rect.height;

    // Simulate vertical scroll as a smooth swipe
    const scrollStep = e.deltaY > 0 ? 0.18 : -0.18;
    const endY = Math.max(0.05, Math.min(0.95, y - scrollStep));

    onSendCommand({
      action: 'SCROLL',
      start_x: x,
      start_y: y,
      end_x: x,
      end_y: endY,
      duration_ms: 120,
    });
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLDivElement>) => {
    if (e.key === 'Backspace') {
      onSendCommand({ action: 'GLOBAL_NAV', target: 'BACK' });
    } else if (e.key === 'Escape') {
      onSendCommand({ action: 'GLOBAL_NAV', target: 'HOME' });
    }
  };

  return (
    <div
      ref={containerRef}
      tabIndex={0}
      onKeyDown={handleKeyDown}
      className="relative flex flex-col items-center justify-center bg-slate-950 rounded-2xl border border-slate-800 p-4 overflow-hidden shadow-2xl focus:outline-none focus:ring-1 focus:ring-blue-500/50"
    >
      {/* Top Bar inside Viewport */}
      <div className="w-full flex items-center justify-between pb-3 text-slate-400 text-xs">
        <div className="flex items-center space-x-2">
          <Smartphone className="w-4 h-4 text-blue-500" />
          <span className="font-semibold text-slate-200">{deviceName}</span>
          <span className="bg-slate-800 text-slate-400 px-2 py-0.5 rounded text-[10px]">H.264 / 60 FPS</span>
          <span className="bg-emerald-950 text-emerald-400 border border-emerald-800 px-2 py-0.5 rounded text-[10px]">
            Touch Active
          </span>
        </div>

        <button
          onClick={toggleFullscreen}
          className="p-1.5 hover:bg-slate-800 rounded-lg text-slate-400 hover:text-white transition"
          title={isFullscreen ? 'Exit Fullscreen' : 'Fullscreen'}
        >
          {isFullscreen ? <Minimize2 className="w-4 h-4" /> : <Maximize2 className="w-4 h-4" />}
        </button>
      </div>

      {/* Screen Viewport with Smartphone Aspect Ratio Frame */}
      <div className="relative flex items-center justify-center max-h-[75vh] w-auto aspect-[9/19.5] bg-black rounded-3xl border-4 border-slate-800 shadow-inner overflow-hidden select-none">
        <video
          ref={videoRef}
          autoPlay
          playsInline
          muted
          onContextMenu={(e) => e.preventDefault()}
          onMouseDown={handleMouseDown}
          onMouseUp={handleMouseUp}
          onWheel={handleWheel}
          className="w-full h-full object-contain cursor-pointer"
        />

        {/* Visual touch feedback ripple */}
        {touchIndicator && (
          <div
            style={{ left: touchIndicator.x, top: touchIndicator.y }}
            className="pointer-events-none absolute w-8 h-8 -ml-4 -mt-4 rounded-full bg-blue-500/40 border-2 border-blue-400 animate-ping"
          />
        )}

        {!stream && (
          <div className="absolute inset-0 flex flex-col items-center justify-center p-6 text-center text-slate-500">
            <Smartphone className="w-12 h-12 mb-3 stroke-[1.5] text-slate-600 animate-pulse" />
            <p className="text-sm font-medium">Awaiting Screen Stream...</p>
            <p className="text-xs text-slate-600 mt-1">Approve "Start now" on your Android device.</p>
          </div>
        )}
      </div>
    </div>
  );
};
