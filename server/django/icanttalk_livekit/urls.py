from django.urls import path

from .views import livekit_token

urlpatterns = [
    path("api-v2/command/livekit-token/", livekit_token, name="icanttalk-livekit-token-v2"),
    path("api/icanttalk/livekit-token/", livekit_token, name="icanttalk-livekit-token"),
]
