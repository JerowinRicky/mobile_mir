import unittest
from unittest.mock import AsyncMock, patch, MagicMock
from apps.signaling.consumers import SignalingConsumer
from apps.sessions.models import SessionStatus

class SignalingConsumerUnitTestCase(unittest.IsolatedAsyncioTestCase):
    async def test_connect_unauthorized_closes_with_4003(self):
        consumer = SignalingConsumer()
        consumer.scope = {
            'query_string': b'session_id=11111111-2222-3333-4444-555555555555&token=mock_jwt'
        }
        with patch.object(SignalingConsumer, 'authenticate_and_authorize', new=AsyncMock(return_value=False)), \
             patch.object(SignalingConsumer, 'close', new=AsyncMock()) as mock_close:
            await consumer.connect()
            mock_close.assert_called_once_with(code=4003)

    async def test_sdp_offer_routed_to_group(self):
        consumer = SignalingConsumer()
        consumer.channel_name = "test_channel_123"
        consumer.room_group_name = 'session_11111111-2222-3333-4444-555555555555'
        mock_channel_layer = MagicMock()
        mock_channel_layer.group_send = AsyncMock()

        with patch('channels.consumer.get_channel_layer', return_value=mock_channel_layer):
            sdp_offer_msg = {'type': 'sdp_offer', 'sdp': 'v=0...'}
            await consumer.receive_json(sdp_offer_msg)
            mock_channel_layer.group_send.assert_called_once()

    async def test_sdp_answer_routed_and_activates_session(self):
        consumer = SignalingConsumer()
        consumer.channel_name = "test_channel_123"
        consumer.room_group_name = 'session_11111111-2222-3333-4444-555555555555'
        mock_channel_layer = MagicMock()
        mock_channel_layer.group_send = AsyncMock()

        with patch('channels.consumer.get_channel_layer', return_value=mock_channel_layer), \
             patch.object(SignalingConsumer, 'update_session_status', new=AsyncMock()) as mock_update:
            sdp_answer_msg = {'type': 'sdp_answer', 'sdp': 'v=0...'}
            await consumer.receive_json(sdp_answer_msg)
            mock_update.assert_called_once_with(SessionStatus.ACTIVE)

    async def test_signaling_message_filters_self_echo(self):
        consumer = SignalingConsumer()
        consumer.channel_name = "test_channel_123"

        with patch.object(SignalingConsumer, 'send_json', new=AsyncMock()) as mock_send_json:
            event_from_self = {
                'sender_channel': 'test_channel_123',
                'message': {'type': 'ice_candidate', 'candidate': '...'}
            }
            await consumer.signaling_message(event_from_self)
            mock_send_json.assert_not_called()

            event_from_peer = {
                'sender_channel': 'peer_channel_456',
                'message': {'type': 'ice_candidate', 'candidate': '...'}
            }
            await consumer.signaling_message(event_from_peer)
            mock_send_json.assert_called_once_with({'type': 'ice_candidate', 'candidate': '...'})
