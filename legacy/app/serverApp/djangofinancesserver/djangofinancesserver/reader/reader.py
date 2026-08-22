import csv


def cacu_csv_reader1(file):
    decoded_file = file.read().decode('utf-8').splitlines()
    # reader = csv.DictReader(decoded_file, delimiter=',')
    reader = csv.DictReader(decoded_file)
    line_count = 0
    income_list = []
    expense_list = []

    for row in reader:
        if line_count == 0:
            print(f'Column names are {", ".join(row)}')

            line_count += 1
        else:
            if not row.get('Amount Credit'):
                # Row has no amount credit, must be an expense
                expense_item = map_expense_item(row)
                # Map row to expense item
                expense_list.append(dict(expense_item))

            else:
                # Else, must be income
                income_item = map_income_item(row)
                # Map row to income item
                income_list.append(dict(income_item))

            line_count += 1

    print('income_list -------------------------------------')
    print(income_list)
    print('expense_list --------------------------------------')
    print(expense_list)

def cacu_csv_reader2(file, json_request_data):
    decoded_file = file.read().decode('utf-8').splitlines()
    # reader = csv.DictReader(decoded_file, delimiter=',')
    reader = csv.DictReader(decoded_file)
    line_count = 0
    income_list = []
    expense_list = []

    for row in reader:
        if line_count == 0:
            print(f'Column names are {", ".join(row)}')

            line_count += 1
        else:
            if not row.get('Amount Credit'):
                # Row has no amount credit, must be an expense
                expense_item = map_expense_item(row, json_request_data)
                # Map row to expense item
                expense_list.append(dict(expense_item))

            else:
                # Else, must be income
                income_item = map_income_item(row, json_request_data)
                # Map row to income item
                income_list.append(dict(income_item))

            line_count += 1

    # print(income_list)

    response_data = {
        'incomeItemList': income_list,
        'expenseItemList': expense_list
    }

    # print(response_data)

    return response_data


def map_income_item(row, json_request_data):
    income_item = {
        'budgetIncomeCategoryId': '?',
        'name': row.get('Description'),
        'receivedDate': row.get('Date'),
        'amountExpected': abs(row.get('Amount Credit')),
        'amountReceived': abs(row.get('Amount Credit')),
        'accountId': json_request_data['accountId'],
        'usersId': json_request_data['usersId']
    }
    return income_item


def map_expense_item(row, json_request_data):
    expense_item = {
        'budgetExpenseCategoryId': '?',
        'name': row.get('Description'),
        'transactionDate': row.get('Date'),
        'amount': abs(row.get('Amount Debit')),
        'paidWithCredit': False,
        'paymentToCreditAccount': False,
        'interestPaymentToCreditAccount': False,
        'accountId': json_request_data['accountId'],
        'usersId': json_request_data['usersId']
    }
    return expense_item