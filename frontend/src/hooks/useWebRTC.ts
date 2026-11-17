import { useState, useEffect, useRef, useCallback } from 'react';
import { SignalingService } from '../services/signaling';
import { TurnConfig, NetworkStats, InputCommand } from '../types';

interface UseWebRTCOptions {
  sessionId: string;
  token: string;
  turnConfig: TurnConfig;
  onDisconnected?: () => void;
}

export function useWebRTC({ sessionId, token, turnConfig, onDisconnected }: UseWebRTCOptions) {
  const [connectionState, setConnectionState] = useState<RTCPeerConnectionState>('new');
  const [iceState, setIceState] = useState<RTCIceConnectionState>('new');
  const [stats, setStats] = useState<NetworkStats>({
    rttMs: 0,
    bitrateKbps: 0,
    packetsLost: 0,
    packetLossPercent: 0,
    fps: 0,
    candidateType: 'unknown',
  });
  const [remoteStream, setRemoteStream] = useState<MediaStream | null>(null);

  const pcRef = useRef<RTCPeerConnection | null>(null);
  const signalingRef = useRef<SignalingService | null>(null);
  const dataChannelRef = useRef<RTCDataChannel | null>(null);
  const lastBytesRef = useRef<number>(0);
  const lastTimestampRef = useRef<number>(0);

  // Initialize WebRTC and Signaling
  useEffect(() => {
    const signaling = new SignalingService();
    signalingRef.current = signaling;

    const pc = new RTCPeerConnection({
      iceServers: turnConfig.ice_servers,
      bundlePolicy: 'max-bundle',
      rtcpMuxPolicy: 'require',
    });
    pcRef.current = pc;

    // Monitor PeerConnection state
    pc.onconnectionstatechange = () => {
      setConnectionState(pc.connectionState);
      if (pc.connectionState === 'disconnected' || pc.connectionState === 'failed') {
        console.warn('[WebRTC] Connection state degraded:', pc.connectionState);
        attemptIceRestart();
      } else if (pc.connectionState === 'closed') {
        onDisconnected?.();
      }
    };

    pc.oniceconnectionstatechange = () => {
      setIceState(pc.iceConnectionState);
    };

    // Gather local ICE candidates and send via signaling
    pc.onicecandidate = (event) => {
      if (event.candidate) {
        signaling.sendIceCandidate(event.candidate.toJSON());
      }
    };

    // Remote Track received (Android Screen Video)
    pc.ontrack = (event) => {
      console.log('[WebRTC] Remote track received:', event.track.kind);
      if (event.streams && event.streams[0]) {
        setRemoteStream(event.streams[0]);
      } else {
        const stream = new MediaStream([event.track]);
        setRemoteStream(stream);
      }
    };

    // DataChannel for remote control inputs
    pc.ondatachannel = (event) => {
      console.log('[WebRTC] DataChannel received from phone:', event.channel.label);
      setupDataChannel(event.channel);
    };

    // Create local DataChannel if not offered
    const localDc = pc.createDataChannel('input-channel', {
      ordered: true,
      maxRetransmits: 0,
    });
    setupDataChannel(localDc);

    // Signaling event listener
    const unsubscribeSignaling = signaling.onMessage(async (msg) => {
      switch (msg.type) {
        case 'sdp_offer': {
          console.log('[WebRTC] Received SDP Offer from phone. Setting remote description...');
          await pc.setRemoteDescription(new RTCSessionDescription({ type: 'offer', sdp: msg.sdp }));
          const answer = await pc.createAnswer();
          await pc.setLocalDescription(answer);
          console.log('[WebRTC] Created SDP Answer. Transmitting to phone...');
          signaling.sendSdpAnswer(answer.sdp!);
          break;
        }
        case 'ice_candidate': {
          try {
            await pc.addIceCandidate(new RTCIceCandidate(msg.candidate));
          } catch (e) {
            console.error('[WebRTC] Error adding remote ICE candidate:', e);
          }
          break;
        }
        case 'peer_disconnected':
        case 'session_terminated': {
          console.log('[WebRTC] Peer disconnected or session terminated.');
          onDisconnected?.();
          break;
        }
      }
    });

    // Connect WebSocket signaling
    const wsUrl = import.meta.env.VITE_WS_URL || 'ws://127.0.0.1:8000';
    signaling.connect(wsUrl, sessionId, token).catch((err) => {
      console.error('[WebRTC] Failed to connect signaling socket:', err);
    });

    // Stats monitoring ticker (1000ms)
    const statsInterval = setInterval(async () => {
      if (pc.connectionState === 'connected') {
        const report = await pc.getStats();
        let rtt = 0;
        let bitrate = 0;
        let lost = 0;
        let fps = 0;
        let candType: NetworkStats['candidateType'] = 'unknown';

        report.forEach((stat) => {
          if (stat.type === 'inbound-rtp' && stat.kind === 'video') {
            const now = stat.timestamp;
            const bytes = stat.bytesReceived || 0;
            if (lastTimestampRef.current && lastBytesRef.current) {
              const deltaMs = now - lastTimestampRef.current;
              const deltaBytes = bytes - lastBytesRef.current;
              bitrate = Math.round((deltaBytes * 8) / (deltaMs / 1000) / 1000);
            }
            lastBytesRef.current = bytes;
            lastTimestampRef.current = now;
            lost = stat.packetsLost || 0;
            fps = stat.framesPerSecond || 0;
          }
          if (stat.type === 'candidate-pair' && stat.state === 'succeeded') {
            rtt = Math.round((stat.currentRoundTripTime || 0) * 1000);
            const remoteCand = report.get(stat.remoteCandidateId);
            if (remoteCand) {
              candType = remoteCand.candidateType || 'unknown';
            }
          }
        });

        setStats({
          rttMs: rtt,
          bitrateKbps: bitrate,
          packetsLost: lost,
          packetLossPercent: 0,
          fps,
          candidateType: candType,
        });
      }
    }, 1000);

    return () => {
      clearInterval(statsInterval);
      unsubscribeSignaling();
      signaling.disconnect();
      pc.close();
    };
  }, [sessionId, token, turnConfig]);

  const setupDataChannel = (dc: RTCDataChannel) => {
    dataChannelRef.current = dc;
    dc.onopen = () => console.log('[WebRTC] DataChannel open & ready for input dispatch');
    dc.onclose = () => console.log('[WebRTC] DataChannel closed');
  };

  const attemptIceRestart = useCallback(async () => {
    if (!pcRef.current) return;
    try {
      console.log('[WebRTC] Initiating ICE restart...');
      const offer = await pcRef.current.createOffer({ iceRestart: true });
      await pcRef.current.setLocalDescription(offer);
      signalingRef.current?.sendSdpAnswer(offer.sdp!);
    } catch (e) {
      console.error('[WebRTC] ICE restart failed:', e);
    }
  }, []);

  const sendInputCommand = useCallback((command: InputCommand) => {
    if (dataChannelRef.current && dataChannelRef.current.readyState === 'open') {
      dataChannelRef.current.send(JSON.stringify(command));
    }
  }, []);

  return {
    remoteStream,
    connectionState,
    iceState,
    stats,
    sendInputCommand,
    reconnect: attemptIceRestart,
  };
}
