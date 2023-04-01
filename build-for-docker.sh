#/bin/bash

# This script prepares Docker Dockerfiles and Contexts for the Group & EDHOC Applications
# If the flag --build-images is specified, it also builds the Docker images

## Build the Jar files for the Group & EDHOC Applications if needed

# Separately install these two dependencies
mvn org.apache.maven.plugins:maven-dependency-plugin:2.8:get -Dartifact=org.slf4j:jul-to-slf4j:1.7.36
mvn org.apache.maven.plugins:maven-dependency-plugin:2.8:get -Dartifact=org.slf4j:slf4j-simple:1.7.36

# Group Applications
FILE=group-applications/OscoreAsServer.jar
if [ -f "$FILE" ]; then
    echo "$FILE exists."
else 
    echo "$FILE does not exist."
    ./build-group-apps.sh
fi

# EDHOC Applications
FILE=edhoc-applications/Phase4Server.jar
if [ -f "$FILE" ]; then
    echo "$FILE exists."
else 
    echo "$FILE does not exist."
    ./build-edhoc-apps.sh
fi


## Create working directory for image building

mkdir docker-build
cd docker-build

# Create directories for Group Applications and EDHOC Applications
mkdir group
mkdir edhoc

# Copy needed files (jar files and library files)
cp ../group-applications/*.jar group/
cp ../group-applications/*.py group/
cp -r ../group-applications/lib group/lib

cp ../edhoc-applications/*.jar edhoc/
cp ../edhoc-applications/*.py edhoc/
cp -r ../edhoc-applications/lib edhoc/lib


## Create base Dockerfile. Initial part is same for all images.
#  Uses Ubuntu 20.04 as base. Then sets the timezone, and installs OpenJDK.
#  Setting the timezone is needed as the OpenJDK install otherwise interactively interrupts.
#  Also installs Python and pip3 for the toggling Python scripts.

echo 'FROM ubuntu:20.04' > Dockerfile.base
echo 'ENV DEBIAN_FRONTEND noninteractive' >> Dockerfile.base
echo 'ENV TZ="Europe/Stockholm"' >> Dockerfile.base
echo 'WORKDIR /apps' >> Dockerfile.base
echo 'RUN apt-get -y update && \' >> Dockerfile.base
echo '    apt-get install -yq tzdata && \' >> Dockerfile.base
echo '    ln -fs /usr/share/zoneinfo/Europe/Stockholm /etc/localtime && \' >> Dockerfile.base
echo '    dpkg-reconfigure -f noninteractive tzdata && \' >> Dockerfile.base
echo '    apt-get -y install default-jre-headless && \' >> Dockerfile.base
echo '    apt-get -y install python3 && \' >> Dockerfile.base
echo '    apt-get -y install python3-pip && \' >> Dockerfile.base
echo '    pip3 install RPi.GPIO && \' >> Dockerfile.base
echo '    ln -s $(which python3) /usr/bin/python && \' >> Dockerfile.base
echo '    mkdir -p apps/lib' >> Dockerfile.base
echo '' >> Dockerfile.base


## Prepare to build images for Group Applications

cd group

# OscoreAsServer: ACE Authorization Server
# Assumes container name mysql for MySQL server
# Assumes root password xxxxxx for MySQL server
# Selected to be pushed to Docker Hub.
echo "xxxxxx mysql" >> db.pwd
dockerfile=Dockerfile-OscoreAsServer
cp ../Dockerfile.base $dockerfile
echo 'EXPOSE 5683/udp' >> $dockerfile
echo 'ADD db.pwd /apps' >> $dockerfile
echo 'ADD OscoreAsServer.jar /apps' >> $dockerfile
echo 'ADD lib /apps/lib/' >> $dockerfile
echo '' >> $dockerfile
echo 'ENTRYPOINT ["java", "-jar", "OscoreAsServer.jar"]' >> $dockerfile
# docker build -f $dockerfile -t oscoreasserver .

# OscoreRsServer: Group Manager (ACE Resource Server)
# Selected to be pushed to Docker Hub.
dockerfile=Dockerfile-OscoreRsServer
cp ../Dockerfile.base $dockerfile
echo 'EXPOSE 5783/udp' >> $dockerfile
echo 'ADD OscoreRsServer.jar /apps' >> $dockerfile
echo 'ADD lib /apps/lib/' >> $dockerfile
echo '' >> $dockerfile
echo 'ENTRYPOINT ["java", "-jar", "OscoreRsServer.jar"]' >> $dockerfile
# docker build -f $dockerfile -t oscorersserver .

# OscoreAsRsClient: Group OSCORE Server/Client which will join the group(s)
# Client 2 (for Group B)
# Assumes container name "authorization-server" for ACE Authorization Server
# Assumes container name "group-manager" for ACE Resource Server
# Selected to be pushed to Docker Hub.
dockerfile=Dockerfile-OscoreAsRsClient-Client2
cp ../Dockerfile.base $dockerfile
echo 'ADD OscoreAsRsClient.jar /apps' >> $dockerfile
echo 'ADD lib /apps/lib/' >> $dockerfile
echo '' >> $dockerfile
echo 'ENTRYPOINT ["java", "-jar", "OscoreAsRsClient.jar", "-name", "Client2", "-delay", "75", "-as", "coap://authorization-server:5683", "-gm", "coap://group-manager:5783", "-dht"]' >> $dockerfile
# docker build -f $dockerfile -t oscoreasrsclient-client2 .

# OscoreAsRsClient: Group OSCORE Server/Client which will join the group(s)
# Server 4 (for Group B)
# Selected to be pushed to Docker Hub.
dockerfile=Dockerfile-OscoreAsRsClient-Server4
cp ../Dockerfile.base $dockerfile
echo 'EXPOSE 4683/udp' >> $dockerfile
echo 'ADD LED-on.py /apps' >> $dockerfile
echo 'ADD LED-off.py /apps' >> $dockerfile
echo 'ADD OscoreAsRsClient.jar /apps' >> $dockerfile
echo 'ADD lib /apps/lib/' >> $dockerfile
echo '' >> $dockerfile
echo 'ENTRYPOINT ["java", "-jar", "OscoreAsRsClient.jar", "-name", "Server4", "-delay", "15"]' >> $dockerfile
# docker build -f $dockerfile -t oscoreasrsclient-server4 .

# OscoreAsRsClient: Group OSCORE Server/Client which will join the group(s)
# Server 5 (for Group B)
# Selected to be pushed to Docker Hub.
dockerfile=Dockerfile-OscoreAsRsClient-Server5
cp ../Dockerfile.base $dockerfile
echo 'EXPOSE 4683/udp' >> $dockerfile
echo 'ADD LED-on.py /apps' >> $dockerfile
echo 'ADD LED-off.py /apps' >> $dockerfile
echo 'ADD OscoreAsRsClient.jar /apps' >> $dockerfile
echo 'ADD lib /apps/lib/' >> $dockerfile
echo '' >> $dockerfile
echo 'ENTRYPOINT ["java", "-jar", "OscoreAsRsClient.jar", "-name", "Server5", "-delay", "30"]' >> $dockerfile
# docker build -f $dockerfile -t oscoreasrsclient-server5 .

# OscoreAsRsClient: Group OSCORE Server/Client which will join the group(s)
# Server 6 (for Group B)
# Selected to be pushed to Docker Hub.
dockerfile=Dockerfile-OscoreAsRsClient-Server6
cp ../Dockerfile.base $dockerfile
echo 'EXPOSE 4683/udp' >> $dockerfile
echo 'ADD LED-on.py /apps' >> $dockerfile
echo 'ADD LED-off.py /apps' >> $dockerfile
echo 'ADD OscoreAsRsClient.jar /apps' >> $dockerfile
echo 'ADD lib /apps/lib/' >> $dockerfile
echo '' >> $dockerfile
echo 'ENTRYPOINT ["java", "-jar", "OscoreAsRsClient.jar", "-name", "Server6", "-delay", "45"]' >> $dockerfile
# docker build -f $dockerfile -t oscoreasrsclient-server6 .

# Adversary: Adversary for testing attacks against the group(s)
dockerfile=Dockerfile-Adversary
cp ../Dockerfile.base $dockerfile
echo 'ADD Adversary.jar /apps' >> $dockerfile
echo 'ADD lib /apps/lib/' >> $dockerfile
echo '' >> $dockerfile
echo 'ENTRYPOINT ["java", "-jar", "Adversary.jar"]' >> $dockerfile
# docker build -f $dockerfile -t adversary .


## Prepare to build images for EDHOC Applications

cd ../edhoc

# Phase0Server: CoAP-only server
# Selected to be pushed to Docker Hub.
dockerfile=Dockerfile-Phase0Server
cp ../Dockerfile.base $dockerfile
echo 'EXPOSE 5683/udp' >> $dockerfile
echo 'ADD LED-on.py /apps' >> $dockerfile
echo 'ADD LED-off.py /apps' >> $dockerfile
echo 'ADD Phase0Server.jar /apps' >> $dockerfile
echo 'ADD lib /apps/lib/' >> $dockerfile
echo '' >> $dockerfile
echo 'ENTRYPOINT ["java", "-jar", "Phase0Server.jar"]' >> $dockerfile
# docker build -f $dockerfile -t phase0server .

# Phase0Client: CoAP-only client
# Assumes container name "coap-server" for server-side
# Selected to be pushed to Docker Hub.
dockerfile=Dockerfile-Phase0Client
cp ../Dockerfile.base $dockerfile
echo 'ADD Phase0Client.jar /apps' >> $dockerfile
echo 'ADD lib /apps/lib/' >> $dockerfile
echo '' >> $dockerfile
echo 'ENTRYPOINT ["java", "-jar", "Phase0Client.jar", "-server", "coap://coap-server:5683", "-dht"]' >> $dockerfile
# docker build -f $dockerfile -t phase0client .

# Phase1Server: EDHOC server using method 0 and no optimized request
dockerfile=Dockerfile-Phase1Server
cp ../Dockerfile.base $dockerfile
echo 'EXPOSE 5683/udp' >> $dockerfile
echo 'ADD LED-on.py /apps' >> $dockerfile
echo 'ADD LED-off.py /apps' >> $dockerfile
echo 'ADD Phase1Server.jar /apps' >> $dockerfile
echo 'ADD lib /apps/lib/' >> $dockerfile
echo '' >> $dockerfile
echo 'ENTRYPOINT ["java", "-jar", "Phase1Server.jar"]' >> $dockerfile
# docker build -f $dockerfile -t phase1server .

# Phase1Client: EDHOC client using method 0 and no optimized request
# Assumes container name phase1server for server-side
dockerfile=Dockerfile-Phase1Client
cp ../Dockerfile.base $dockerfile
echo 'ADD Phase1Client.jar /apps' >> $dockerfile
echo 'ADD lib /apps/lib/' >> $dockerfile
echo '' >> $dockerfile
echo 'ENTRYPOINT ["java", "-jar", "Phase1Client.jar", "-server", "coap://phase1server:5683", "-dht"]' >> $dockerfile
# docker build -f $dockerfile -t phase1client .

# Phase2Server: EDHOC server using method 3 and no optimized request
dockerfile=Dockerfile-Phase2Server
cp ../Dockerfile.base $dockerfile
echo 'EXPOSE 5683/udp' >> $dockerfile
echo 'ADD LED-on.py /apps' >> $dockerfile
echo 'ADD LED-off.py /apps' >> $dockerfile
echo 'ADD Phase2Server.jar /apps' >> $dockerfile
echo 'ADD lib /apps/lib/' >> $dockerfile
echo '' >> $dockerfile
echo 'ENTRYPOINT ["java", "-jar", "Phase2Server.jar"]' >> $dockerfile
# docker build -f $dockerfile -t phase2server .

# Phase2Client: EDHOC client using method 3 and no optimized request
# Assumes container name phase2server for server-side
dockerfile=Dockerfile-Phase2Client
cp ../Dockerfile.base $dockerfile
echo 'ADD Phase2Client.jar /apps' >> $dockerfile
echo 'ADD lib /apps/lib/' >> $dockerfile
echo '' >> $dockerfile
echo 'ENTRYPOINT ["java", "-jar", "Phase2Client.jar", "-server", "coap://phase2server:5683", "-dht"]' >> $dockerfile
# docker build -f $dockerfile -t phase2client .

# Phase3Server: EDHOC server using method 0 and the optimized request
dockerfile=Dockerfile-Phase3Server
cp ../Dockerfile.base $dockerfile
echo 'EXPOSE 5683/udp' >> $dockerfile
echo 'ADD LED-on.py /apps' >> $dockerfile
echo 'ADD LED-off.py /apps' >> $dockerfile
echo 'ADD Phase3Server.jar /apps' >> $dockerfile
echo 'ADD lib /apps/lib/' >> $dockerfile
echo '' >> $dockerfile
echo 'ENTRYPOINT ["java", "-jar", "Phase3Server.jar"]' >> $dockerfile
# docker build -f $dockerfile -t phase3server .

# Phase3Client: EDHOC client using method 0 and the optimized request
# Assumes container name phase3server for server-side
dockerfile=Dockerfile-Phase3Client
cp ../Dockerfile.base $dockerfile
echo 'ADD Phase3Client.jar /apps' >> $dockerfile
echo 'ADD lib /apps/lib/' >> $dockerfile
echo '' >> $dockerfile
echo 'ENTRYPOINT ["java", "-jar", "Phase3Client.jar", "-server", "coap://phase3server:5683", "-dht"]' >> $dockerfile
# docker build -f $dockerfile -t phase3client .

# Phase4Server: EDHOC server using method 3 and the optimized request
# Selected to be pushed to Docker Hub.
dockerfile=Dockerfile-Phase4Server
cp ../Dockerfile.base $dockerfile
echo 'EXPOSE 5683/udp' >> $dockerfile
echo 'ADD LED-on.py /apps' >> $dockerfile
echo 'ADD LED-off.py /apps' >> $dockerfile
echo 'ADD Phase4Server.jar /apps' >> $dockerfile
echo 'ADD lib /apps/lib/' >> $dockerfile
echo '' >> $dockerfile
echo 'ENTRYPOINT ["java", "-jar", "Phase4Server.jar"]' >> $dockerfile
# docker build -f $dockerfile -t phase4server .

# Phase4Client: EDHOC client using method 3 and the optimized request
# Assumes container name "edhoc-server" for server-side
# Selected to be pushed to Docker Hub.
dockerfile=Dockerfile-Phase4Client
cp ../Dockerfile.base $dockerfile
echo 'ADD Phase4Client.jar /apps' >> $dockerfile
echo 'ADD lib /apps/lib/' >> $dockerfile
echo '' >> $dockerfile
echo 'ENTRYPOINT ["java", "-jar", "Phase4Client.jar", "-server", "coap://edhoc-server:5683", "-dht"]' >> $dockerfile
# docker build -f $dockerfile -t phase4client .


## Actually build the images. Note that only some of the images are built.
# The below part should be done in the Github Actions to push the images to Docker Hub.

# If indicated actually build the images, otherwise exit here
if [ "$1" != "--build-images" ]
then
  exit
fi

# Build a selection of the images for the Group Applications

cd ../group

docker build -f Dockerfile-OscoreAsServer -t authorization-server .

docker build -f Dockerfile-OscoreRsServer -t group-manager .

docker build -f Dockerfile-OscoreAsRsClient-Client2 -t group-client .

docker build -f Dockerfile-OscoreAsRsClient-Server4 -t group-server1 .

docker build -f Dockerfile-OscoreAsRsClient-Server5 -t group-server2 .

docker build -f Dockerfile-OscoreAsRsClient-Server6 -t group-server3 .


# Build a selection of the images for the EDHOC Applications

cd ../edhoc

docker build -f Dockerfile-Phase0Server -t coap-server .

docker build -f Dockerfile-Phase0Client -t coap-client .

docker build -f Dockerfile-Phase4Server -t edhoc-server .

docker build -f Dockerfile-Phase4Client -t edhoc-client .

