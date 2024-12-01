# CS441 Fall2024 - HW3
## Vibha Navale
#### UIN: 676301415
#### NetID: vnava22@uic.edu

Repo for Homework-3 for CS441 Fall2024: The Design and Deployment of a Conversational Agent using Large Language Models (LLMs)

Project walkthrough:


## Environment:
**OS** : macOS (M3 Chip)\
**Development Tools**: IntelliJ IDEA, sbt\
**Cloud Platform**: AWS EC2

---

## Prerequisites:
**Programming Languages/Tools:** Scala, Java 11, sbt (1.10.2)

**Frameworks:** Akka HTTP, Play, Finch/Finagle, or Scalatra

**Cloud Services:** AWS EC2, AWS Lambda, AWS API Gateway

**LLM Models:** Trained LLM or Ollama models, Optionally Amazon Bedrock for backend LLMs

**Dependencies:** Ollama dependency: ```"io.github.ollama4j" % "ollama4j" % "1.0.79"```

---

## Running the project
1) Clone the repository and navigate to the root directory.
2) The root file is _src/main/scala/App/AkkaHttpServer.scala_
3) Run `sbt clean update` and `sbt clean compile` from the terminal.
4) Run `sbt "run"` to start the local server.
5) Run `sbt test` to test.
6) To create a fat jar, run the command `sbt clean assembly`
7) The resulting jar file can be found at _target/scala-2.12/CS-441-HW-3-assembly-0.1.0-SNAPSHOT.jar_

---
### Deploying to AWS
- Set up an EC2 instance and install required dependencies.

Deploy the microservice to AWS Lambda via AWS Serverless Application Model (SAM):
- Follow AWS SAM guides to package and deploy the Lambda function. 
- Use AWS API Gateway to expose your service as a RESTful endpoint.

Test the deployment:
- Use Postman, curl commands, or a custom Scala client to send requests and receive responses.

---

## Requirements:

In this homework, the focus is on building a RESTful microservice that leverages a Large Language Model (LLM) for conversational responses. The microservice will be deployed in a cloud environment (AWS EC2) and will interface with both cloud-based and local LLM instances.

**Mandatory Requirements (All Students):**\
1. LLM Integration:
   - Use a REST framework (e.g., Akka HTTP, Play) to design a microservice that queries an LLM and returns responses. 
2. Microservice Functionality:
   - Accept HTTP requests with queries (e.g., "How do cats express love?").
   - Generate responses and support multi-turn conversations until termination conditions are met (e.g., max responses). 
3. AWS Deployment:
   - Deploy the microservice on AWS EC2.
   - Use Postman or cURL to test the REST API.

**Additional Requirements (Graduate Students Only):**\
1. Conversational Testing:
   - Develop automatic clients to test multi-turn conversations using predefined templates.
   - Analyze the influence of templates on conversation flow and include insights in your submission. 
2. Local Ollama Integration:
   - Sign up for Ollama and install a local LLM server with a chosen model.
   - Compare the performance of your microservice with cloud-based and local Ollama LLMs.
3. Deployment on AWS Lambda:
   - Utilize AWS Lambda and API Gateway for some functionality.
   - Optionally integrate gRPC for Lambda function invocation.

---

## Technical Design

We will take a look at the detailed description of how each of these pieces of code work below. Line-by-line comments explaining every step are also added to the source code in this git repo:

1) ### [AkkaHttpServer](src/main/scala/App/AkkaHttpServer.scala) [Akka HTTP]
   This file initializes and runs the Akka HTTP server to handle incoming requests to interact with the microservice.
   - The Akka HTTP server listens for incoming HTTP requests (e.g., GET, POST) on specified routes.
   - Routes include endpoints for querying the LLM, starting a conversation, and generating responses.
   - Utilizes Akka HTTP’s Route DSL to define various HTTP request handlers for conversation-related tasks.
   - Implements logging to capture incoming requests and outgoing responses.
   - Handles exceptions by returning appropriate HTTP status codes (e.g., 400 for invalid requests, 500 for internal server errors).

2) ### [TextGenerationService](src/main/scala/Service/TextGenerationService.scala) [Service]
   The service that interacts with the LLM, either locally via Ollama or a cloud-based service.
   - This service encapsulates the logic for generating text responses by calling the LLM (either Ollama or cloud).
   - It handles the communication between the HTTP server and the underlying LLM service.
   - Makes use of an asynchronous HTTP client to make requests to the LLM endpoint and processes the responses.
   - Manages conversation state (e.g., keeping track of conversation history for multi-turn interactions).
   - Logs the generated responses and tracks performance metrics for evaluation.

3) ### [OllamaService](src/main/scala/Service/OllamaService.scala) [Ollama Integration]
   Responsible for interacting with the Ollama local LLM server and integrating its responses into the system.
   - Initializes the local Ollama server connection and configures the communication protocol.
   - Sends text prompts to the Ollama server, retrieves the generated responses, and formats them for downstream components.
   - Handles both single-turn and multi-turn conversations by managing the session state.
   - Logs all requests and responses to track Ollama service performance and response times.
   - Supports failover mechanisms to fall back to cloud-based LLM if Ollama server is unavailable.

4) ### [LambdaTextGenerationHandler](src/main/scala/Service/LambdaTextGenerationHandler.scala) [AWS Lambda]
   This file handles the Lambda function’s execution, invoking it for text generation in a serverless architecture. 
   - The Lambda handler is invoked by an HTTP request via AWS API Gateway.
   - Upon receiving a request, the handler invokes the TextGenerationService to get a response from the LLM.
   - Uses AWS SDK to integrate Lambda with other AWS services if necessary, like S3 for logging or CloudWatch for monitoring.
   - Returns the generated text in the response to the client and logs the outcome for further analysis.
   - Implements error handling to ensure the Lambda function responds correctly to various failure scenarios.

5) ### [Dockerfile](Dockerfile)[Containerization]
   This file describes the process for building a Docker container for your application, using a multi-stage build to optimize the image size and ensure the necessary dependencies are available.
   - First Stage (Builder):
     - Base Image: Uses the official openjdk:11-jdk-slim image to ensure Java development tools are available.
     - Install SBT: Installs Scala Build Tool (SBT) for building Scala projects. Downloads the SBT package and installs it.
     - Project Files: Copies the project's build files (build.sbt, src, etc.) to the working directory /app.
     - Fetch Dependencies and Compile: Runs sbt clean compile to fetch the necessary dependencies and compile the project.
     - Create Fat Jar: Uses sbt assembly to package the application into a fat JAR file, including all dependencies.
   - Second Stage (Runtime):
     - Base Image: Uses a smaller runtime image openjdk:11-jre-slim to reduce the final image size.
     - Copy the Jar: Copies the fat JAR generated in the builder stage into the /app directory of the runtime image.
     - Copy Resources: Ensures that any application resources (e.g., configuration files) are retained in the image by copying them from the builder stage.
     - Create Output Directory: Creates an /app/output directory for storing any generated output files.
     - Expose Port: Exposes port 8080 for the HTTP server to listen on.
     - Run Command: Configures the container to run the Akka HTTP server directly, using the application.conf configuration file and specifying the necessary JVM options.

6) ### [Protobuf](src/main/protobuf/llm_service.proto) [Data Serialization]
   This is the Protobuf definition file used to structure data for text generation requests and responses.
    - GenerateTextRequest:
      - prompt (string): The text prompt provided to the model for generating a response.
      - max_length (int32): The maximum length for the generated text.
      - temperature (float): Controls the randomness of the response. Higher values make the output more random.
      - model_id (string): The identifier of the specific model to use for text generation.
    - GenerateTextResponse:
      - generated_text (string): The text generated by the model based on the input prompt.
      - confidence (float): A confidence score for the generated text, indicating the model's certainty.
      - tokens_used (int32): The number of tokens consumed during the text generation process.
      - model_id (string): The identifier of the model that generated the text.
      - status (ResponseStatus): Contains information about the status of the response, such as success or error.
    - ResponseStatus:
      - StatusCode: An enum that defines the possible status codes: SUCCESS (0) and ERROR (1).
      - message (string): A message providing additional context about the status (e.g., error details if the code is ERROR).

---

## Test Cases
These are run through the command `sbt test`

---
