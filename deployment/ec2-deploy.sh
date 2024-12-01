#!/bin/bash

# Update and install dependencies
sudo yum update -y
sudo yum install java-11-amazon-corretto-headless git -y

# Install SBT
curl https://www.scala-sbt.org/sbt-rpm.repo -o sbt-sbt.repo
sudo mv sbt-sbt.repo /etc/yum.repos.d/
sudo yum install sbt -y

# Clone your project (replace with your repo)
git clone YOUR_GITHUB_REPO_URL
cd YOUR_PROJECT_DIRECTORY

# Build and run
sbt clean compile run