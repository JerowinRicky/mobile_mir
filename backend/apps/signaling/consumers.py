import json
import urllib.parse
from channels.generic.websocket import AsyncJsonWebsocketConsumer
from channels.db import database_sync_to_async
from django.conf import settings
import jwt
from apps.accounts.models import User
from apps.devices.models import Device
from apps.sessions.models import RemoteSession, SessionStatus

class SignalingConsumer(AsyncJsonWebsocketConsumer):

    async def connect(self):
        query_string = self.scope.get('query_string', b'').decode('utf-8')
        params = urllib.parse.parse_qs(query_string)

        self.session_id = params.get('session_id', [None])[0]
        self.auth_token = params.get('token', [None])[0]
        self.device_token = params.get('device_token', [None])[0]

        if not self.session_id:
            await self.close(code=4001) # Missing session ID
            return

        is_authorized = await self.authenticate_and_authorize()
        if not is_authorized:
            await self.close(code=4003) # Unauthorized
            return

        self.room_group_name = f"session_{self.session_id}"

        # Join session group
        await self.channel_layer.group_add(
            self.room_group_name,
            self.channel_name
        )

        await self.accept()

        # Update session status to CONNECTING/ACTIVE
        await self.update_session_status(SessionStatus.CONNECTING)

        await self.send_json({
            'type': 'connection_established',
            'session_id': self.session_id,
            'role': self.role
        })

    async def disconnect(self, close_code):
        if hasattr(self, 'room_group_name'):
            # Notify peer of disconnect
            await self.channel_layer.group_send(
                self.room_group_name,
                {
                    'type': 'signaling_message',
                    'sender_channel': self.channel_name,
                    'message': {
                        'type': 'peer_disconnected',
                        'role': getattr(self, 'role', 'unknown')
                    }
                }
            )

            await self.channel_layer.group_discard(
                self.room_group_name,
                self.channel_name
            )

    async def receive_json(self, content):
        message_type = content.get('type')

        if message_type in ['sdp_offer', 'sdp_answer', 'ice_candidate']:
            # Forward payload to other peer in the group
            await self.channel_layer.group_send(
                self.room_group_name,
                {
                    'type': 'signaling_message',
                    'sender_channel': self.channel_name,
                    'message': content
                }
            )
            if message_type == 'sdp_answer':
                await self.update_session_status(SessionStatus.ACTIVE)

        elif message_type == 'session_terminate':
            await self.update_session_status(SessionStatus.TERMINATED)
            await self.channel_layer.group_send(
                self.room_group_name,
                {
                    'type': 'signaling_message',
                    'sender_channel': self.channel_name,
                    'message': {'type': 'session_terminated', 'reason': content.get('reason', 'Terminated')}
                }
            )

    async def signaling_message(self, event):
        # Don't echo message back to the sender
        if event.get('sender_channel') != self.channel_name:
            await self.send_json(event['message'])

    @database_sync_to_async
    def authenticate_and_authorize(self) -> bool:
        try:
            session = RemoteSession.objects.select_related('user', 'device').get(id=self.session_id)
        except Exception:
            return False

        if session.status == SessionStatus.TERMINATED:
            return False

        # Authenticate user via JWT
        if self.auth_token:
            try:
                payload = jwt.decode(self.auth_token, settings.JWT_SECRET, algorithms=['HS256'])
                if payload.get('sub') == str(session.user_id):
                    self.role = 'web_client'
                    return True
            except Exception:
                pass

        # Authenticate device via Device Token Hash
        if self.device_token:
            import hashlib
            token_hash = hashlib.sha256(self.device_token.encode('utf-8')).hexdigest()
            if session.device.device_token_hash == token_hash:
                self.role = 'android_device'
                return True

        return False

    @database_sync_to_async
    def update_session_status(self, new_status: SessionStatus):
        try:
            session = RemoteSession.objects.get(id=self.session_id)
            if new_status == SessionStatus.TERMINATED:
                session.terminate(reason='Signaling closed')
            else:
                session.status = new_status
                session.save(update_fields=['status'])
        except Exception:
            pass
