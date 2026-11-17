from django.urls import path
from apps.pairing.views import InitiatePairingView, VerifyPinView, ApprovePairingView, CheckPairingStatusView

urlpatterns = [
    path('initiate/', InitiatePairingView.as_view(), name='pairing-initiate'),
    path('verify/', VerifyPinView.as_view(), name='pairing-verify'),
    path('approve/', ApprovePairingView.as_view(), name='pairing-approve'),
    path('status/<uuid:session_id>/', CheckPairingStatusView.as_view(), name='pairing-status'),
]
