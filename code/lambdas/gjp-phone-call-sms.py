from __future__  import print_function  # Python 2/3 compatibility

import boto3
import json
import decimal
from boto3.dynamodb.conditions import Key, Attr
from botocore.exceptions import ClientError

def lambda_handler(event, context):
    # The AWS Region that you want to use to send the message. For a list of
    # AWS Regions where the Amazon Pinpoint API is available, see
    # https://docs.aws.amazon.com/pinpoint/latest/apireference/
    region = "us-east-1"

    # The phone number or short code to send the message from. The phone number
    # or short code that you specify has to be associated with your Amazon Pinpoint
    # account. For best results, specify long codes in E.164 format.
    #originationNumber = event['Details']['ContactData']['SystemEndpoint']['Address']
    originationNumber = '+13344544467'
    
    # The recipient's phone number.  For best results, you should specify the
    # phone number in E.164 format.
    caller = event['Details']['ContactData']['CustomerEndpoint']['Address']
    
    # GJP
    # Need to lookup the forwarding number given connect
    transferNumber = event['Details']['Parameters']['transferNumber']

    # The content of the SMS message.
    message = (caller + ": 1 for SPAM; 0 for valid caller")

    # The Amazon Pinpoint project/application ID to use when you send this message.
    # Make sure that the SMS channel is enabled for the project or application
    # that you choose.
    applicationId = "df013e40403b4b959661e31a8d6fac47"

    # The type of SMS message that you want to send. If you plan to send
    # time-sensitive content, specify TRANSACTIONAL. If you plan to send
    # marketing-related content, specify PROMOTIONAL.
    messageType = "TRANSACTIONAL"

    # The registered keyword associated with the originating short code.
    registeredKeyword = event['Details']['ContactData']['SystemEndpoint']['Address']

    # The sender ID to use when sending the message. Support for sender ID
    # varies by country or region. For more information, see
    # https://docs.aws.amazon.com/pinpoint/latest/userguide/channels-sms-countries.html
    #senderId = "MySenderID"

    #GJP
    print(caller, registeredKeyword, message, transferNumber)

    # Create a new client and specify a region.
    client = boto3.client('pinpoint',region_name=region)
    try:
        response = client.send_messages(
            ApplicationId=applicationId,
            MessageRequest={
                'Addresses': {
                    transferNumber: {
                        'ChannelType': 'SMS'
                    }
                },
                'MessageConfiguration': {
                    'SMSMessage': {
                        #'SenderId': senderId,
                        'Keyword': registeredKeyword,
                        'Body': message,
                        'MessageType': messageType,
                        'OriginationNumber': originationNumber
                    }
                }
            }
        )
    except ClientError as e:
        print(e.response['Error']['Message'])
    else:
        # Add messageID to dynamoDB
        messageId = response['MessageResponse']['Result'][transferNumber]['MessageId']
        dynamodb = boto3.resource('dynamodb')
        tableName = 'gjp-phone-call-sms'
        table = dynamodb.Table(tableName)    

        # Need to add messageId to record in dynamoDB
        response = table.put_item(
            Item={
                'messageId': messageId,
                'caller': caller
            }
        )

    #GJP
    print(event)
    print(caller, registeredKeyword, message, transferNumber, messageId)
    return {'result': "success" }
