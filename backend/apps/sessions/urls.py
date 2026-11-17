from django.urls import path
from apps.sessions.views import CreateSessionView, TerminateSessionView, TurnCredentialsView, SessionHistoryView

urlpatterns = [
    path('create/', CreateSessionView.as_view(), name='session-create'),
    path('<uuid:session_id>/terminate/', TerminateSessionView.as_view(), name='session-terminate'),
    path('turn-credentials/', TurnCredentialsView.as_view(), name='session-turn-credentials'),
    path('history/', SessionHistoryView.as_view(), name='session-history'),
]
