package com.amazonaws.kvstranscribestreaming;

import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.transcribestreaming.model.Result;
import software.amazon.awssdk.services.transcribestreaming.model.TranscriptEvent;

import java.text.NumberFormat;
import java.time.Instant;
import java.util.List;

/**
 * TranscribedSegmentWriter writes the transcript segments to DynamoDB
 *
 * <p>Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.</p>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this
 * software and associated documentation files (the "Software"), to deal in the Software
 * without restriction, including without limitation the rights to use, copy, modify,
 * merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
public class TranscribedSegmentWriter {

    private String contactId;
    private DynamoDB ddbClient;
    private Boolean consoleLogTranscriptFlag;
    private static final String TABLE_CALLER_TRANSCRIPT = System.getenv("TABLE_CALLER_TRANSCRIPT");
    private static final boolean SAVE_PARTIAL_TRANSCRIPTS = Boolean.parseBoolean(System.getenv("SAVE_PARTIAL_TRANSCRIPTS"));
    private static final Logger logger = LoggerFactory.getLogger(TranscribedSegmentWriter.class);
    
    public TranscribedSegmentWriter(String contactId, DynamoDB ddbClient, Boolean consoleLogTranscriptFlag) {

        this.contactId = Validate.notNull(contactId);
        this.ddbClient = Validate.notNull(ddbClient);
        this.consoleLogTranscriptFlag = Validate.notNull(consoleLogTranscriptFlag);
    }

    public String getContactId() {

        return this.contactId;
    }

    public DynamoDB getDdbClient() {

        return this.ddbClient;
    }

    public void writeToDynamoDB(TranscriptEvent transcriptEvent) {

        List<Result> results = transcriptEvent.transcript().results();
        if (results.size() > 0) {

            Result result = results.get(0);

            if (SAVE_PARTIAL_TRANSCRIPTS || !result.isPartial()) {
                try {
                    Item ddbItem = toDynamoDbItem(result);
                    if (ddbItem != null) {
                        getDdbClient().getTable(TABLE_CALLER_TRANSCRIPT).putItem(ddbItem);
                    }

                } catch (Exception e) {
                    logger.error("Exception while writing to DDB: ", e);
                }
            }
        }
    }

    private Item toDynamoDbItem(Result result) {
    	String phraseConnect = result.alternatives().get(0).transcript(); //GJP
        String contactId = this.getContactId();
        Item ddbItem = null;
        boolean socialAttack = false;
    
        NumberFormat nf = NumberFormat.getInstance();
        nf.setMinimumFractionDigits(3);
        nf.setMaximumFractionDigits(3);

        if (result.alternatives().size() > 0) {
            if (!result.alternatives().get(0).transcript().isEmpty()) {
        		// result.alternatives().get(0).transcript() has the text we need to search for strings
        		// If we find a match we need to alert the recepient that this could be a social engineering attack
        		if ((phraseConnect.toLowerCase().contains("date of birth") 
                    || phraseConnect.toLowerCase().contains("birthday") 
                    || phraseConnect.toLowerCase().contains("birth day") 
                    || phraseConnect.toLowerCase().contains("mother's maiden name") 
                    || phraseConnect.toLowerCase().contains("mothers maiden name") 
                    || phraseConnect.toLowerCase().contains("social security number")
                    || phraseConnect.toLowerCase().contains("account")
                    || phraseConnect.toLowerCase().contains("bank account")
                    || phraseConnect.toLowerCase().contains("username")
                    || phraseConnect.toLowerCase().contains("user name")
                    || phraseConnect.toLowerCase().contains("pass word")
                    || phraseConnect.toLowerCase().contains("password")) 
                    && (!socialAttack))
        		{
        		    socialAttack = true;
        			logger.info(String.format("GJP: Social engineering attack %s", phraseConnect));
        		}

                Instant now = Instant.now();
                ddbItem = new Item()
                        .withKeyComponent("ContactId", contactId)
                        .withKeyComponent("StartTime", result.startTime())
                        .withString("SegmentId", result.resultId())
                        .withDouble("EndTime", result.endTime())
                        .withString("Transcript", result.alternatives().get(0).transcript())
                        .withBoolean("IsPartial", result.isPartial())
                        .withBoolean("SocialAttack", socialAttack)
                        // LoggedOn is an ISO-8601 string representation of when the entry was created
                        .withString("LoggedOn", now.toString())
                        // expire after a week by default
                        .withDouble("ExpiresAfter", now.plusMillis(7 * 24 * 3600).toEpochMilli());

                if (consoleLogTranscriptFlag) {
                    logger.info(String.format("Thread %s %d: [%s, %s] - %s",
                            Thread.currentThread().getName(),
                            System.currentTimeMillis(),
                            nf.format(result.startTime()),
                            nf.format(result.endTime()),
                            phraseConnect,
                            result.alternatives().get(0).transcript()));
                }
            }
        }
        return ddbItem;
    }
}