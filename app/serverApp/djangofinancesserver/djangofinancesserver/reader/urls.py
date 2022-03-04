from django.conf.urls import url
from reader import views

urlpatterns = [
    url(r'^api/test$', views.test),
    url(r'^api/csvtest1$', views.file_test_1),
    url(r'^api/csvtest2$', views.file_test_2),
    url(r'^api/csvtest3$', views.file_test_3),
]