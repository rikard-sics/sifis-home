#/bin/bash

# This script builds Docker images for the Group & EDHOC Applications


## Build the Jar files for the Group & EDHOC Applications if needed

FILE=group-applications/OscoreAsServer.jar
if [ -f "$FILE" ]; then
    echo "$FILE exists."
else 
    echo "$FILE does not exist."
    ./build-group-apps.sh
fi

FILE=edhoc-applications/Phase0Server.jar
if [ -f "$FILE" ]; then
    echo "$FILE exists."
else 
    echo "$FILE does not exist."
    ./build-edhoc-apps.sh
fi

## Create working directory for image building

mkdir docker-images
cd docker-images

# Create directories for Group Applications and EDHOC Applications
mkdir group
mkdir edhoc

# Copy needed files (jar files and library files)
cp ../group-applications/*.jar group/
cp -r ../group-applications/lib group/lib

cp ../edhoc-applications/*.jar edhoc/
cp -r ../edhoc-applications/lib edhoc/lib


## Create base Dockerfile. Initial part is same for all images.
#  Uses Ubuntu 20.04 as base. Then sets the timezone, and installs OpenJDK.
#  Setting the timezone is needed as the OpenJDK install otherwise interactively interrupts.

echo 'FROM ubuntu:20.04' > Dockerfile.base
echo 'ENV DEBIAN_FRONTEND noninteractive' >> Dockerfile.base
echo 'ENV TZ="Europe/Stockholm"' >> Dockerfile.base
echo 'RUN apt-get -y update && \' >> Dockerfile.base
echo '    apt-get install -yq tzdata && \' >> Dockerfile.base
echo '    ln -fs /usr/share/zoneinfo/Europe/Stockholm /etc/localtime && \' >> Dockerfile.base
echo '    dpkg-reconfigure -f noninteractive tzdata && \' >> Dockerfile.base
echo '    apt-get -y install default-jre-headless && \' >> Dockerfile.base
echo '    mkdir -p apps/lib' >> Dockerfile.base
echo '' >> Dockerfile.base


## Build images for Group Applications

cd group

# OscoreAsServer: ACE Authorization Server
# Assumes container name mysql for MySQL server
# Assumes root password xxxxxx for MySQL server
echo "xxxxxx mysql" >> db.pwd
dockerfile=Dockerfile-OscoreAsServer
cp ../Dockerfile.base $dockerfile
echo 'EXPOSE 5683/udp' >> $dockerfile
echo 'ADD db.pwd /apps' >> $dockerfile
echo 'ADD OscoreAsServer.jar /apps' >> $dockerfile
echo 'ADD lib /apps/lib/' >> $dockerfile
echo '' >> $dockerfile
echo 'ENTRYPOINT ["java", "-jar", "/apps/OscoreAsServer.jar"]' >> $dockerfile
#docker build -f $dockerfile -t oscoreasserver .

# OscoreRsServer: Group Manager (ACE Resource Server)
dockerfile=Dockerfile-OscoreRsServer
cp ../Dockerfile.base $dockerfile
echo 'EXPOSE 5783/udp' >> $dockerfile
echo 'ADD OscoreRsServer.jar /apps' >> $dockerfile
echo 'ADD lib /apps/lib/' >> $dockerfile
echo '' >> $dockerfile
echo 'ENTRYPOINT ["java", "-jar", "/apps/OscoreRsServer.jar"]' >> $dockerfile
#docker build -f $dockerfile -t oscorersserver .

# OscoreAsRsClient: Group OSCORE Server/Client which will join the group(s)
# Client 1 (for Group A)
# Assumes container name oscoreasserver for ACE Authorization Server
# Assumes container name oscorersserver for ACE Resource Server
dockerfile=Dockerfile-OscoreAsRsClient-Client1
cp ../Dockerfile.base $dockerfile
echo 'ADD OscoreAsRsClient.jar /apps' >> $dockerfile
echo 'ADD lib /apps/lib/' >> $dockerfile
echo '' >> $dockerfile
echo 'ENTRYPOINT ["java", "-jar", "/apps/OscoreAsRsClient.jar", "-name", "Client1", "-delay", "60", "-as", "coap://oscoreasserver:5683", "-gm", "coap://oscorersserver:5783", "-dht"]' >> $dockerfile
#docker build -f $dockerfile -t oscoreasrsclient-client1 .

# OscoreAsRsClient: Group OSCORE Server/Client which will join the group(s)
# Server 1 (for Group A)
dockerfile=Dockerfile-OscoreAsRsClient-Server1
cp ../Dockerfile.base $dockerfile
echo 'EXPOSE 4683/udp' >> $dockerfile
echo 'ADD OscoreAsRsClient.jar /apps' >> $dockerfile
echo 'ADD lib /apps/lib/' >> $dockerfile
echo '' >> $dockerfile
echo 'ENTRYPOINT ["java", "-jar", "/apps/OscoreAsRsClient.jar", "-name", "Server1", "-delay", "10"]' >> $dockerfile
#docker build -f $dockerfile -t oscoreasrsclient-server1 .

# OscoreAsRsClient: Group OSCORE Server/Client which will join the group(s)
# Server 2 (for Group A)
dockerfile=Dockerfile-OscoreAsRsClient-Server2
cp ../Dockerfile.base $dockerfile
echo 'EXPOSE 4683/udp' >> $dockerfile
echo 'ADD OscoreAsRsClient.jar /apps' >> $dockerfile
echo 'ADD lib /apps/lib/' >> $dockerfile
echo '' >> $dockerfile
echo 'ENTRYPOINT ["java", "-jar", "/apps/OscoreAsRsClient.jar", "-name", "Server2", "-delay", "20"]' >> $dockerfile
#docker build -f $dockerfile -t oscoreasrsclient-server2 .

# OscoreAsRsClient: Group OSCORE Server/Client which will join the group(s)
# Server 3 (for Group A)
dockerfile=Dockerfile-OscoreAsRsClient-Server3
cp ../Dockerfile.base $dockerfile
echo 'EXPOSE 4683/udp' >> $dockerfile
echo 'ADD OscoreAsRsClient.jar /apps' >> $dockerfile
echo 'ADD lib /apps/lib/' >> $dockerfile
echo '' >> $dockerfile
echo 'ENTRYPOINT ["java", "-jar", "/apps/OscoreAsRsClient.jar", "-name", "Server3", "-delay", "30"]' >> $dockerfile
#docker build -f $dockerfile -t oscoreasrsclient-server3 .

# Adversary: Adversary for testing attacks against the group(s)
dockerfile=Dockerfile-Adversary
cp ../Dockerfile.base $dockerfile
echo 'ADD Adversary.jar /apps' >> $dockerfile
echo 'ADD lib /apps/lib/' >> $dockerfile
echo '' >> $dockerfile
echo 'ENTRYPOINT ["java", "-jar", "/apps/Adversary.jar"]' >> $dockerfile
#docker build -f $dockerfile -t adversary .


## Build images for EDHOC Applications

cd ../edhoc

# Phase0Server: CoAP-only server
dockerfile=Dockerfile-Phase0Server
cp ../Dockerfile.base $dockerfile
echo 'EXPOSE 5683/udp' >> $dockerfile
echo 'ADD Phase0Server.jar /apps' >> $dockerfile
echo 'ADD lib /apps/lib/' >> $dockerfile
echo '' >> $dockerfile
echo 'ENTRYPOINT ["java", "-jar", "/apps/Phase0Server.jar"]' >> $dockerfile
#docker build -f $dockerfile -t phase0server .

# Phase0Client: CoAP-only client
# Assumes container name phase0server for server-side
dockerfile=Dockerfile-Phase0Client
cp ../Dockerfile.base $dockerfile
echo 'ADD Phase0Client.jar /apps' >> $dockerfile
echo 'ADD lib /apps/lib/' >> $dockerfile
echo '' >> $dockerfile
echo 'ENTRYPOINT ["java", "-jar", "/apps/Phase0Client.jar", "-server", "coap://phase0server:5683", "-dht"]' >> $dockerfile
#docker build -f $dockerfile -t phase0client .

# Phase1Server: EDHOC server using method 0 and no optimized request
dockerfile=Dockerfile-Phase1Server
cp ../Dockerfile.base $dockerfile
echo 'EXPOSE 5683/udp' >> $dockerfile
echo 'ADD Phase1Server.jar /apps' >> $dockerfile
echo 'ADD lib /apps/lib/' >> $dockerfile
echo '' >> $dockerfile
echo 'ENTRYPOINT ["java", "-jar", "/apps/Phase1Server.jar"]' >> $dockerfile
#docker build -f $dockerfile -t phase1server .

# Phase1Client: EDHOC client using method 0 and no optimized request
# Assumes container name phase1server for server-side
dockerfile=Dockerfile-Phase1Client
cp ../Dockerfile.base $dockerfile
echo 'ADD Phase1Client.jar /apps' >> $dockerfile
echo 'ADD lib /apps/lib/' >> $dockerfile
echo '' >> $dockerfile
echo 'ENTRYPOINT ["java", "-jar", "/apps/Phase1Client.jar", "-server", "coap://phase1server:5683", "-dht"]' >> $dockerfile
#docker build -f $dockerfile -t phase1client .

# Phase2Server: EDHOC server using method 3 and no optimized request
dockerfile=Dockerfile-Phase2Server
cp ../Dockerfile.base $dockerfile
echo 'EXPOSE 5683/udp' >> $dockerfile
echo 'ADD Phase2Server.jar /apps' >> $dockerfile
echo 'ADD lib /apps/lib/' >> $dockerfile
echo '' >> $dockerfile
echo 'ENTRYPOINT ["java", "-jar", "/apps/Phase2Server.jar"]' >> $dockerfile
#docker build -f $dockerfile -t phase2server .

# Phase2Client: EDHOC client using method 3 and no optimized request
# Assumes container name phase2server for server-side
dockerfile=Dockerfile-Phase2Client
cp ../Dockerfile.base $dockerfile
echo 'ADD Phase2Client.jar /apps' >> $dockerfile
echo 'ADD lib /apps/lib/' >> $dockerfile
echo '' >> $dockerfile
echo 'ENTRYPOINT ["java", "-jar", "/apps/Phase2Client.jar", "-server", "coap://phase2server:5683", "-dht"]' >> $dockerfile
#docker build -f $dockerfile -t phase2client .

# Phase3Server: EDHOC server using method 0 and the optimized request
dockerfile=Dockerfile-Phase3Server
cp ../Dockerfile.base $dockerfile
echo 'EXPOSE 5683/udp' >> $dockerfile
echo 'ADD Phase3Server.jar /apps' >> $dockerfile
echo 'ADD lib /apps/lib/' >> $dockerfile
echo '' >> $dockerfile
echo 'ENTRYPOINT ["java", "-jar", "/apps/Phase3Server.jar"]' >> $dockerfile
#docker build -f $dockerfile -t phase3server .

# Phase3Client: EDHOC client using method 0 and the optimized request
# Assumes container name phase3server for server-side
dockerfile=Dockerfile-Phase3Client
cp ../Dockerfile.base $dockerfile
echo 'ADD Phase3Client.jar /apps' >> $dockerfile
echo 'ADD lib /apps/lib/' >> $dockerfile
echo '' >> $dockerfile
echo 'ENTRYPOINT ["java", "-jar", "/apps/Phase3Client.jar", "-server", "coap://phase3server:5683", "-dht"]' >> $dockerfile
#docker build -f $dockerfile -t phase3client .

# Phase4Server: EDHOC server using method 3 and the optimized request
dockerfile=Dockerfile-Phase4Server
cp ../Dockerfile.base $dockerfile
echo 'EXPOSE 5683/udp' >> $dockerfile
echo 'ADD Phase4Server.jar /apps' >> $dockerfile
echo 'ADD lib /apps/lib/' >> $dockerfile
echo '' >> $dockerfile
echo 'ENTRYPOINT ["java", "-jar", "/apps/Phase4Server.jar"]' >> $dockerfile
#docker build -f $dockerfile -t phase4server .

# Phase4Client: EDHOC client using method 3 and the optimized request
# Assumes container name phase4server for server-side
dockerfile=Dockerfile-Phase4Client
cp ../Dockerfile.base $dockerfile
echo 'ADD Phase4Client.jar /apps' >> $dockerfile
echo 'ADD lib /apps/lib/' >> $dockerfile
echo '' >> $dockerfile
echo 'ENTRYPOINT ["java", "-jar", "/apps/Phase4Client.jar", "-server", "coap://phase4server:5683", "-dht"]' >> $dockerfile
#docker build -f $dockerfile -t phase4client .

