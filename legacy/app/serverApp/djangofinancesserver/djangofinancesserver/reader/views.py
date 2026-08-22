import json

from django.http import HttpResponse, JsonResponse
from rest_framework import status
from rest_framework.decorators import api_view
from rest_framework.exceptions import ParseError
from rest_framework.response import Response
from reader.reader import cacu_csv_reader1
from creader.reader import cacu_csv_reader2
from reader.reader_chase import chase_csv_reader1


def test(request):
    return HttpResponse("Hello, World!")


@api_view(['POST'])
def file_test_1(request):
    if request.method == 'POST':
        if 'file' not in request.data:
            raise ParseError("Empty content")

        file = request.FILES['file']

        cacu_csv_reader1(file)

        return Response(status=status.HTTP_201_CREATED)


@api_view(['POST'])
def file_test_2(request):
    if request.method == 'POST':
        if 'file' not in request.data:
            raise ParseError("Empty content")

        file = request.FILES['file']

        data = request.data['data']

        json_request_data = json.loads(data)
        print(json_request_data['usersId'])
        # print(request_data)

        response_data = cacu_csv_reader2(file, json_request_data)

        return JsonResponse(response_data)
        # return Response(status=status.HTTP_201_CREATED)


@api_view(['POST'])
def file_test_3(request):
    if request.method == 'POST':
        if 'file' not in request.data:
            raise ParseError("Empty content, no file passed into view.")

        file = request.FILES['file']

        data = request.data['data']

        json_request_data = json.loads(data)
        print(json_request_data['usersId'])
        # print(request_data)

        response_data = chase_csv_reader1(file, json_request_data)

        return JsonResponse(response_data)
        # return Response(status=status.HTTP_201_CREATED)
