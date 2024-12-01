#!/bin/bash

# Ensure you have AWS CLI configured
# aws configure

# Project-specific variables
PROJECT_NAME="text-generation-lambda"
FUNCTION_NAME="generateText"
REGION="us-east-1"
RUNTIME="java11"
HANDLER="Service.LambdaTextGenerationHandler::handleRequest"

# Build the fat JAR
sbt clean assembly

# Create Lambda function (first time)
aws lambda create-function \
    --function-name $FUNCTION_NAME \
    --runtime $RUNTIME \
    --role arn:aws:iam::YOUR_ACCOUNT_ID:role/lambda-execution-role \
    --handler $HANDLER \
    --zip-file fileb://target/scala-2.12/CS-441-HW-3-assembly-0.1.0-SNAPSHOT.jar \
    --timeout 30 \
    --memory-size 512

# Update function code (for subsequent deployments)
aws lambda update-function-code \
    --function-name $FUNCTION_NAME \
    --zip-file fileb://target/scala-2.12/CS-441-HW-3-assembly-0.1.0-SNAPSHOT.jar
