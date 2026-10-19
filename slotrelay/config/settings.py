import os, secrets
from pathlib import Path
BASE_DIR = Path(__file__).resolve().parent.parent
DATA_DIR = Path(os.environ.get('SLOTRELAY_DATA_DIR', BASE_DIR / 'data'))
DATA_DIR.mkdir(exist_ok=True)
secret = DATA_DIR / '.secret'
if not secret.exists():
    try:
        with secret.open('x') as f:
            f.write(secrets.token_urlsafe(64))
        secret.chmod(0o600)
    except FileExistsError:
        pass
SECRET_KEY = os.environ.get('SLOTRELAY_SECRET_KEY') or secret.read_text()
DEBUG = os.environ.get('SLOTRELAY_DEBUG') == '1'
cloud = os.environ.get('CODESPACE_NAME', '')
cloud_host = f'{cloud}-8000.app.github.dev' if cloud else ''
ALLOWED_HOSTS = ['localhost', '127.0.0.1', 'testserver'] + ([cloud_host] if cloud_host else []) + [h for h in os.environ.get('SLOTRELAY_HOSTS', '').split(',') if h]
CSRF_TRUSTED_ORIGINS = ['https://' + h for h in ALLOWED_HOSTS if h not in ['localhost', '127.0.0.1', 'testserver']]
INSTALLED_APPS = ['django.contrib.auth','django.contrib.contenttypes','django.contrib.sessions','django.contrib.messages','django.contrib.staticfiles','booking']
MIDDLEWARE = ['django.middleware.security.SecurityMiddleware','whitenoise.middleware.WhiteNoiseMiddleware','django.contrib.sessions.middleware.SessionMiddleware','django.middleware.common.CommonMiddleware','django.middleware.csrf.CsrfViewMiddleware','django.contrib.auth.middleware.AuthenticationMiddleware','django.contrib.messages.middleware.MessageMiddleware','django.middleware.clickjacking.XFrameOptionsMiddleware']
ROOT_URLCONF = 'config.urls'
TEMPLATES = [{'BACKEND':'django.template.backends.django.DjangoTemplates','DIRS':[BASE_DIR/'templates'],'APP_DIRS':True,'OPTIONS':{'context_processors':['django.template.context_processors.request','django.contrib.auth.context_processors.auth','django.contrib.messages.context_processors.messages', 'booking.context_processors.notification_count']}}]
WSGI_APPLICATION = 'config.wsgi.application'
DATABASES = {'default':{'ENGINE':'django.db.backends.sqlite3','NAME':DATA_DIR/'slotrelay.sqlite3','OPTIONS':{'timeout':20, 'transaction_mode':'IMMEDIATE'}}}
AUTH_PASSWORD_VALIDATORS = [{'NAME':'django.contrib.auth.password_validation.UserAttributeSimilarityValidator'},{'NAME':'django.contrib.auth.password_validation.MinimumLengthValidator','OPTIONS':{'min_length':12}},{'NAME':'django.contrib.auth.password_validation.CommonPasswordValidator'},{'NAME':'django.contrib.auth.password_validation.NumericPasswordValidator'}]
TIME_ZONE = 'America/Toronto'
USE_TZ = True
DEFAULT_AUTO_FIELD = 'django.db.models.BigAutoField'
STATIC_URL = '/static/'
STATIC_ROOT = BASE_DIR/'staticfiles'
STATICFILES_DIRS = [BASE_DIR/'static']
LOGIN_URL = '/login/'
LOGIN_REDIRECT_URL = '/dashboard/'
LOGOUT_REDIRECT_URL = '/'
SESSION_COOKIE_HTTPONLY = True
SESSION_COOKIE_SAMESITE = 'Lax'
SESSION_COOKIE_SECURE = bool(cloud) or os.environ.get('SLOTRELAY_SECURE') == '1'
CSRF_COOKIE_SECURE = SESSION_COOKIE_SECURE
SECURE_CONTENT_TYPE_NOSNIFF = True
X_FRAME_OPTIONS = 'DENY'

# Log rejection reasons without form contents or credentials.
LOGGING = {'version': 1, 'disable_existing_loggers': False, 'handlers': {'console': {'class': 'logging.StreamHandler'}}, 'loggers': {'django.security.csrf': {'handlers': ['console'], 'level': 'WARNING', 'propagate': False}}}
CSRF_FAILURE_VIEW = 'booking.views.csrf_failure'

# Codespaces rewrites the browser Origin to its loopback upstream.
# Trust only this exact development origin, and only inside Codespaces.
if cloud:
    CSRF_TRUSTED_ORIGINS.append('http://localhost:8000')
