export type SignalingMessage =
  | { type: 'connection_established'; session_id: string; role: string }
  | { type: 'sdp_offer'; sdp: string }
  | { type: 'sdp_answer'; sdp: string }
  | { type: 'ice_candidate'; candidate: RTCIceCandidateInit }
  | { type: 'peer_disconnected'; role: string }
  | { type: 'session_terminated'; reason: string };

export class SignalingService {
  private ws: WebSocket | null = null;
  private messageHandlers: ((msg: SignalingMessage) => void)[] = [];

  connect(wsBaseUrl: string, sessionId: string, token: string): Promise<void> {
    return new Promise((resolve, reject) => {
      const url = `${wsBaseUrl}/ws/signaling/?session_id=${sessionId}&token=${token}`;
      this.ws = new WebSocket(url);

      this.ws.onopen = () => {
        console.log('[Signaling] WebSocket connected to session:', sessionId);
        resolve();
      };

      this.ws.onmessage = (event) => {
        try {
          const msg = JSON.parse(event.data) as SignalingMessage;
          this.messageHandlers.forEach((handler) => handler(msg));
        } catch (err) {
          console.error('[Signaling] Message parse error:', err);
        }
      };

      this.ws.onerror = (err) => {
        console.error('[Signaling] WebSocket error:', err);
        reject(err);
      };

      this.ws.onclose = (event) => {
        console.warn('[Signaling] WebSocket closed:', event.code, event.reason);
      };
    });
  }

  onMessage(handler: (msg: SignalingMessage) => void) {
    this.messageHandlers.push(handler);
    return () => {
      this.messageHandlers = this.messageHandlers.filter((h) => h !== handler);
    };
  }

  sendSdpAnswer(sdp: string) {
    this.send({ type: 'sdp_answer', sdp });
  }

  sendIceCandidate(candidate: RTCIceCandidateInit) {
    this.send({ type: 'ice_candidate', candidate });
  }

  sendTerminate(reason: string) {
    this.send({ type: 'session_terminate', reason } as unknown as SignalingMessage);
  }

  private send(msg: SignalingMessage) {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(msg));
    } else {
      console.warn('[Signaling] Cannot send, WebSocket not open');
    }
  }

  disconnect() {
    if (this.ws) {
      this.ws.close();
      this.ws = null;
    }
    this.messageHandlers = [];
  }
}
