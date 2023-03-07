#/bin/bash

# This script builds Docker images for the Group & EDHOC Applications


# Build the Jar files for the Group & EDHOC Applications if needed

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

# Create working directory for image building
mkdir docker-images
cd docker-images

# Create base Dockerfile. Initial part is same for all images.
# Uses Ubuntu 20.04 as base. Then sets the timezone, and installs OpenJDK.
# Setting the timezone is needed as the OpenJDK install otherwise interactively interrupts.

echo 'FROM ubuntu:20.04' > Dockerfile.base
echo 'ENV DEBIAN_FRONTEND noninteractive' >> Dockerfile.base
echo 'ENV TZ="Europe/Stockholm"' >> Dockerfile.base
echo 'RUN apt-get -y update && \' >> Dockerfile.base
echo '    apt-get install -yq tzdata && \' >> Dockerfile.base
echo '    ln -fs /usr/share/zoneinfo/Europe/Stockholm /etc/localtime && \' >> Dockerfile.base
echo '    dpkg-reconfigure -f noninteractive tzdata && \' >> Dockerfile.base
echo '    mkdir -p apps/lib && \' >> Dockerfile.base
echo '    apt-get -y install default-jre-headless' >> Dockerfile.base
echo '' >> Dockerfile.base

# Create directories for Group Applications and EDHOC Applications
mkdir group
mkdir edhoc

# Copy needed files
cp ../group-applications/*.jar group/
cp -r ../group-applications/lib group/lib

cp ../edhoc-applications/*.jar edhoc/
cp -r ../edhoc-applications/lib edhoc/lib


## Build images for Group Applications

cd group

# OscoreAsServer: ACE Authorization Server
cp ../Dockerfile.base Dockerfile-OscoreAsServer
echo 'EXPOSE 5683/udp' >> Dockerfile-OscoreAsServer
echo 'ADD OscoreAsServer.jar /apps' >> Dockerfile-OscoreAsServer
echo 'ADD lib /apps/lib/' >> Dockerfile-OscoreAsServer
echo '' >> Dockerfile-OscoreAsServer
echo 'ENTRYPOINT ["java", "-jar", "/apps/OscoreAsServer.jar"]' >> Dockerfile-OscoreAsServer
# docker build -f Dockerfile-OscoreAsServer -t OscoreAsServer .

# OscoreRsServer: Group Manager (ACE Resource Server)
cp ../Dockerfile.base Dockerfile-OscoreRsServer
echo 'EXPOSE 5783/udp' >> Dockerfile-OscoreRsServer
echo 'ADD OscoreRsServer.jar /apps' >> Dockerfile-OscoreRsServer
echo 'ADD lib /apps/lib/' >> Dockerfile-OscoreRsServer
echo '' >> Dockerfile-OscoreRsServer
echo 'ENTRYPOINT ["java", "-jar", "/apps/OscoreRsServer.jar"]' >> Dockerfile-OscoreRsServer
# docker build -f Dockerfile-OscoreRsServer -t OscoreRsServer .

# OscoreAsRsClient: Group OSCORE Server/Client which will join the group(s)
# Client 1 (for Group A)
cp ../Dockerfile.base Dockerfile-OscoreAsRsClient-Client1
echo 'ADD OscoreAsRsClient.jar /apps' >> Dockerfile-OscoreAsRsClient-Client1
echo 'ADD lib /apps/lib/' >> Dockerfile-OscoreAsRsClient-Client1
echo '' >> Dockerfile-OscoreAsRsClient-Client1
echo 'ENTRYPOINT ["java", "-jar", "/apps/OscoreAsRsClient.jar", "-delay", "60", "-name", "Client1", "-as", "XXX", "-gm", "XXX", "-dht"]' >> Dockerfile-OscoreAsRsClient-Client1
# docker build -f Dockerfile-OscoreAsRsClient-Client1 -t OscoreAsRsClient-Client1 .

# OscoreAsRsClient: Group OSCORE Server/Client which will join the group(s)
# Server 1 (for Group A)
cp ../Dockerfile.base Dockerfile-OscoreAsRsClient-Server1
echo 'EXPOSE 4683/udp' >> Dockerfile-OscoreAsRsClient-Server1
echo 'ADD OscoreAsRsClient.jar /apps' >> Dockerfile-OscoreAsRsClient-Server1
echo 'ADD lib /apps/lib/' >> Dockerfile-OscoreAsRsClient-Server1
echo '' >> Dockerfile-OscoreAsRsClient-Server1
echo 'ENTRYPOINT ["java", "-jar", "/apps/OscoreAsRsClient.jar", "-name", "Server1", "-delay", "10"]' >> Dockerfile-OscoreAsRsClient-Server1
# docker build -f Dockerfile-OscoreAsRsClient-Server1 -t OscoreAsRsClient-Server1 .

# OscoreAsRsClient: Group OSCORE Server/Client which will join the group(s)
# Server 2 (for Group A)
cp ../Dockerfile.base Dockerfile-OscoreAsRsClient-Server2
echo 'EXPOSE 4683/udp' >> Dockerfile-OscoreAsRsClient-Server2
echo 'ADD OscoreAsRsClient.jar /apps' >> Dockerfile-OscoreAsRsClient-Server2
echo 'ADD lib /apps/lib/' >> Dockerfile-OscoreAsRsClient-Server2
echo '' >> Dockerfile-OscoreAsRsClient-Server2
echo 'ENTRYPOINT ["java", "-jar", "/apps/OscoreAsRsClient.jar", "-name", "Server2", "-delay", "20"]' >> Dockerfile-OscoreAsRsClient-Server2
# docker build -f Dockerfile-OscoreAsRsClient-Server2 -t OscoreAsRsClient-Server2 .

# OscoreAsRsClient: Group OSCORE Server/Client which will join the group(s)
# Server 3 (for Group A)
cp ../Dockerfile.base Dockerfile-OscoreAsRsClient-Server3
echo 'EXPOSE 4683/udp' >> Dockerfile-OscoreAsRsClient-Server3
echo 'ADD OscoreAsRsClient.jar /apps' >> Dockerfile-OscoreAsRsClient-Server3
echo 'ADD lib /apps/lib/' >> Dockerfile-OscoreAsRsClient-Server3
echo '' >> Dockerfile-OscoreAsRsClient-Server3
echo 'ENTRYPOINT ["java", "-jar", "/apps/OscoreAsRsClient.jar", "-name", "Server3", "-delay", "30"]' >> Dockerfile-OscoreAsRsClient-Server3
# docker build -f Dockerfile-OscoreAsRsClient-Server3 -t OscoreAsRsClient-Server3 .

# Adversary: Adversary for testing attacks against the group(s)
cp ../Dockerfile.base Dockerfile-Adversary
echo 'ADD Adversary.jar /apps' >> Dockerfile-Adversary
echo 'ADD lib /apps/lib/' >> Dockerfile-Adversary
echo '' >> Dockerfile-Adversary
echo 'ENTRYPOINT ["java", "-jar", "/apps/Adversary.jar"]' >> Dockerfile-Adversary
# docker build -f Dockerfile-Adversary -t Adversary .


## Build images for EDHOC Applications

cd ../edhoc


# Phase0Server: CoAP-only server
cp ../Dockerfile.base Dockerfile-Phase0Server
echo 'EXPOSE 5683/udp' >> Dockerfile-Phase0Server
echo 'ADD Phase0Server.jar /apps' >> Dockerfile-Phase0Server
echo 'ADD lib /apps/lib/' >> Dockerfile-Phase0Server
echo '' >> Dockerfile-Phase0Server
echo 'ENTRYPOINT ["java", "-jar", "/apps/Phase0Server.jar"]' >> Dockerfile-Phase0Server
# docker build -f Dockerfile-Phase0Server -t Phase0Server .

# Phase0Client: CoAP-only client
cp ../Dockerfile.base Dockerfile-Phase0Client
echo 'ADD Phase0Client.jar /apps' >> Dockerfile-Phase0Client
echo 'ADD lib /apps/lib/' >> Dockerfile-Phase0Client
echo '' >> Dockerfile-Phase0Client
echo 'ENTRYPOINT ["java", "-jar", "/apps/Phase0Client.jar", "-server", "XX server:port XX", "-dht"]' >> Dockerfile-Phase0Client
# docker build -f Dockerfile-Phase0Client -t Phase0Client .

# Phase1Server: EDHOC server using method 0 and no optimized request
cp ../Dockerfile.base Dockerfile-Phase1Server
echo 'EXPOSE 5683/udp' >> Dockerfile-Phase1Server
echo 'ADD Phase1Server.jar /apps' >> Dockerfile-Phase1Server
echo 'ADD lib /apps/lib/' >> Dockerfile-Phase1Server
echo '' >> Dockerfile-Phase1Server
echo 'ENTRYPOINT ["java", "-jar", "/apps/Phase1Server.jar"]' >> Dockerfile-Phase1Server
# docker build -f Dockerfile-Phase1Server -t Phase1Server .

# Phase1Client: EDHOC client using method 0 and no optimized request
cp ../Dockerfile.base Dockerfile-Phase1Client
echo 'ADD Phase1Client.jar /apps' >> Dockerfile-Phase1Client
echo 'ADD lib /apps/lib/' >> Dockerfile-Phase1Client
echo '' >> Dockerfile-Phase1Client
echo 'ENTRYPOINT ["java", "-jar", "/apps/Phase1Client.jar", "-server", "XX server:port XX", "-dht"]' >> Dockerfile-Phase1Client
# docker build -f Dockerfile-Phase1Client -t Phase1Client .

# Phase2Server: EDHOC server using method 3 and no optimized request
cp ../Dockerfile.base Dockerfile-Phase2Server
echo 'EXPOSE 5683/udp' >> Dockerfile-Phase2Server
echo 'ADD Phase2Server.jar /apps' >> Dockerfile-Phase2Server
echo 'ADD lib /apps/lib/' >> Dockerfile-Phase2Server
echo '' >> Dockerfile-Phase2Server
echo 'ENTRYPOINT ["java", "-jar", "/apps/Phase2Server.jar"]' >> Dockerfile-Phase2Server
# docker build -f Dockerfile-Phase2Server -t Phase2Server .

# Phase2Client: EDHOC client using method 3 and no optimized request
cp ../Dockerfile.base Dockerfile-Phase2Client
echo 'ADD Phase2Client.jar /apps' >> Dockerfile-Phase2Client
echo 'ADD lib /apps/lib/' >> Dockerfile-Phase2Client
echo '' >> Dockerfile-Phase2Client
echo 'ENTRYPOINT ["java", "-jar", "/apps/Phase2Client.jar", "-server", "XX server:port XX", "-dht"]' >> Dockerfile-Phase2Client
# docker build -f Dockerfile-Phase2Client -t Phase2Client .

# Phase3Server: EDHOC server using method 0 and the optimized request
cp ../Dockerfile.base Dockerfile-Phase3Server
echo 'EXPOSE 5683/udp' >> Dockerfile-Phase3Server
echo 'ADD Phase3Server.jar /apps' >> Dockerfile-Phase3Server
echo 'ADD lib /apps/lib/' >> Dockerfile-Phase3Server
echo '' >> Dockerfile-Phase3Server
echo 'ENTRYPOINT ["java", "-jar", "/apps/Phase3Server.jar"]' >> Dockerfile-Phase3Server
# docker build -f Dockerfile-Phase3Server -t Phase3Server .

# Phase3Client: EDHOC client using method 0 and the optimized request
cp ../Dockerfile.base Dockerfile-Phase3Client
echo 'ADD Phase3Client.jar /apps' >> Dockerfile-Phase3Client
echo 'ADD lib /apps/lib/' >> Dockerfile-Phase3Client
echo '' >> Dockerfile-Phase3Client
echo 'ENTRYPOINT ["java", "-jar", "/apps/Phase3Client.jar", "-server", "XX server:port XX", "-dht"]' >> Dockerfile-Phase3Client
# docker build -f Dockerfile-Phase3Client -t Phase3Client .

# Phase4Server: EDHOC server using method 3 and the optimized request
cp ../Dockerfile.base Dockerfile-Phase4Server
echo 'EXPOSE 5683/udp' >> Dockerfile-Phase4Server
echo 'ADD Phase4Server.jar /apps' >> Dockerfile-Phase4Server
echo 'ADD lib /apps/lib/' >> Dockerfile-Phase4Server
echo '' >> Dockerfile-Phase4Server
echo 'ENTRYPOINT ["java", "-jar", "/apps/Phase4Server.jar"]' >> Dockerfile-Phase4Server
# docker build -f Dockerfile-Phase4Server -t Phase4Server .

# Phase4Client: EDHOC client using method 3 and the optimized request
cp ../Dockerfile.base Dockerfile-Phase4Client
echo 'ADD Phase4Client.jar /apps' >> Dockerfile-Phase4Client
echo 'ADD lib /apps/lib/' >> Dockerfile-Phase4Client
echo '' >> Dockerfile-Phase4Client
echo 'ENTRYPOINT ["java", "-jar", "/apps/Phase4Client.jar", "-server", "XX server:port XX", "-dht"]' >> Dockerfile-Phase4Client
# docker build -f Dockerfile-Phase4Client -t Phase4Client .

