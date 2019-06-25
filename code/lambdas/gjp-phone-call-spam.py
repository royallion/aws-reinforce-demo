from __future__  import print_function  # Python 2/3 compatibility

import boto3
import json
import decimal
from boto3.dynamodb.conditions import Key, Attr
from botocore.exceptions import ClientError

def lambda_handler(event, context):
    message = event['Records'][0]['Sns']['Message']
    json_msg = json.loads(message)
    body = json_msg['messageBody']
    messageId = json_msg['previousPublishedMessageId']
    
    # Find the original caller
    dynamodb = boto3.resource('dynamodb')
    tableName = 'gjp-phone-call-sms'
    table = dynamodb.Table(tableName)    
    
    # Lookup phone number in dynamoDB and if there, set spam variable based on entry
    response = table.get_item(
        Key={
            'messageId': messageId
        }
    )
    item = response['Item']
    caller = item['caller']

    # Lookup phone number in dynamoDB and if there, set spam variable based on entry
    response = table.delete_item(
        Key={
            'messageId': messageId
        }
    )
    print(response)       

    # Update the table with SMS response to identify SPAM
    tableName = 'gjp-phone-call-list'
    table = dynamodb.Table(tableName)
    table.update_item(    
        Key={
            'phone-number': caller
            
        },
        UpdateExpression="set spam = :r",
        ExpressionAttributeValues={
            ':r': body,
        },
        ReturnValues="UPDATED_NEW"
    )
    

    print(event)
    print(body, caller)
    # Update dynamodb with the body
    
    return {'result': "success" }
