from apps.audit.models import AuditLog

def get_client_ip(request):
    if not request:
        return None
    x_forwarded_for = request.META.get('HTTP_X_FORWARDED_FOR')
    if x_forwarded_for:
        return x_forwarded_for.split(',')[0].strip()
    return request.META.get('REMOTE_ADDR')

def log_audit_event(event_type, user=None, device_id=None, request=None, severity='INFO', metadata=None):
    ip = get_client_ip(request)
    ua = request.META.get('HTTP_USER_AGENT', '') if request else ''

    try:
        AuditLog.objects.create(
            user=user if user and user.is_authenticated else None,
            device_id=device_id,
            event_type=event_type,
            severity=severity,
            ip_address=ip,
            user_agent=ua,
            metadata=metadata or {}
        )
    except Exception as e:
        # Prevent audit log failures from interrupting application flow
        print(f"Error recording audit log: {e}")
