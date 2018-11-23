#!/bin/bash

### Configuration

# SSH user and cert
USER=zeus
CERT=/var/jenkins_home/.ssh/zeus_id_rsa

# Deployer machine IP (self)
DEPLOYER_IP=10.210.200.56

# Swarm Master
SWARM_MASTER=tcp://10.210.200.156:4000

# Docker Compose file
COMPOSE_FILE=docker-compose.dev.yml

# Local location of the compose file (from where to copy it)
LOCAL_COMPOSE_DIR="$WORKSPACE"
LOCAL_COMPOSE_FILE="$LOCAL_COMPOSE_DIR/$COMPOSE_FILE"

# Remote location of the compose file (to where to copy it)
REMOTE_COMPOSE_DIR=/home/fenix
REMOTE_COMPOSE_FILE="$REMOTE_COMPOSE_DIR/$COMPOSE_FILE"


### Action!

printf "\n### Copying $COMPOSE_FILE to $DEPLOYER_IP deployer machine.\n\n"
ssh -o StrictHostKeyChecking=no -i $CERT $USER@$DEPLOYER_IP "mkdir -p \"$REMOTE_COMPOSE_DIR\""
scp -o StrictHostKeyChecking=no -i $CERT "$LOCAL_COMPOSE_DIR/$COMPOSE_FILE" $USER@$DEPLOYER_IP:"$REMOTE_COMPOSE_DIR"

printf "\n### Checking what is actually running before bringing down the old instance.\n\n"
ssh -o StrictHostKeyChecking=no -i $CERT $USER@$DEPLOYER_IP "export DOCKER_HOST=$SWARM_MASTER && docker ps"

printf "\n### Bringing down the old instance.\n\n"
ssh -o StrictHostKeyChecking=no -i $CERT $USER@$DEPLOYER_IP "export DOCKER_HOST=$SWARM_MASTER && docker-compose -f \"$REMOTE_COMPOSE_FILE\" down"

printf "\n### Checking what is actually running after bringing down the old instance.\n\n"
ssh -o StrictHostKeyChecking=no -i $CERT $USER@$DEPLOYER_IP "export DOCKER_HOST=$SWARM_MASTER && docker ps"

printf "\n### Spinning up the new instance.\n\n"
ssh -o StrictHostKeyChecking=no -i $CERT $USER@$DEPLOYER_IP "export DOCKER_HOST=$SWARM_MASTER && docker-compose -f \"$REMOTE_COMPOSE_FILE\" up -d"

printf "\n### Checking what is actually running after the deployment.\n\n"
ssh -o StrictHostKeyChecking=no -i $CERT $USER@$DEPLOYER_IP "export DOCKER_HOST=$SWARM_MASTER && docker ps"
