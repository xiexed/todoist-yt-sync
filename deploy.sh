#!/bin/bash

# Create and use a Docker container with Java 11 for building the WAR file
echo "Building WAR file with Java 11 in Docker container..."
docker run --rm \
    -v $(pwd):/app \
    -w /app \
    openjdk:11-slim \
    bash -c "apt-get update && apt-get install -y leiningen && lein clean && lein ring uberwar && lein ring uberjar"

# Check if the build was successful
if [ ! -f "./target/todoist-sync-0.1.0-SNAPSHOT-standalone.war" ]; then
    echo "Error: WAR file not created. Build failed."
    exit 1
fi

echo "WAR file built successfully."

# Deploy using Docker with Ansible
echo "Running deployment with Ansible 2.9..."
docker run --rm \
    -v $(pwd):/workspace \
    -v ~/.ssh:/root/.ssh:ro \
    -w /workspace \
    cytopia/ansible:2.9 \
    ansible-playbook -i ansible/inventories/uits-labs/inventory.yaml ansible/playbook-deploy.yaml

echo "Deployment complete!"