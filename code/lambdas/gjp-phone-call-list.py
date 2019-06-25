from __future__  import print_function  # Python 2/3 compatibility

import boto3
import json
import decimal
from boto3.dynamodb.conditions import Key, Attr
from botocore.exceptions import ClientError

def lambda_handler(event, context):
    dynamodb = boto3.resource('dynamodb')
    tableName = 'gjp-phone-call-list'
    table = dynamodb.Table(tableName)    
    spam = 2
    
    # Get phone number of caller
    phoneNumber = event['Details']['ContactData']['CustomerEndpoint']['Address']
    
    # Lookup phone number in dynamoDB and if there, set spam variable based on entry
    response = table.get_item(
        Key={
            'phone-number': phoneNumber
        }
    )
    try:
        response['Item']       
    except: 
        # Add called to database with spam 2, unknown caller
        response = table.put_item(Item={ 'phone-number': phoneNumber, 'spam': spam})
    else:
        item = response['Item']
        spam = item['spam']

    # return 0 (known), 1 (spam/robo), 2 (unknown) caller
    print(phoneNumber + " " + str(spam))
    return {'spam': str(spam) }
