import csv
from decimal import Decimal


def chase_csv_reader1(file, json_request_data):
    decoded_file = file.read().decode('utf-8').splitlines()
    reader = csv.DictReader(decoded_file)
    line_count = 0
    expense_list = []

    for row in reader:
        if line_count == 0:
            print(f'Column names are {", ".join(row)}')

            line_count += 1
        else:
            if row.get('Type') == 'Payment':
                # Row has no amount credit, must be an expense
                expense_item = map_expense_item(row, json_request_data, True)
                # Map row to expense item
                expense_list.append(dict(expense_item))

            else:
                # Else, must be payment
                expense_item = map_expense_item(row, json_request_data, False)
                # Map row to expense item
                expense_list.append(dict(expense_item))

            line_count += 1

    response_data = {
        'expenseItemList': expense_list
    }

    return response_data


def map_expense_item(row, json_request_data, paymentToCreditAccount):
    expense_item = {
        'budgetExpenseCategoryId': '?',
        'name': row.get('Description'),
        'transactionDate': row.get('Transaction Date'),
        'amount': abs(Decimal(row.get('Amount'))),
        'paidWithCredit': True,
        'paymentToCreditAccount': paymentToCreditAccount,
        'interestPaymentToCreditAccount': False,
        'accountId': json_request_data['accountId'],
        'usersId': json_request_data['usersId']
    }
    return expense_item