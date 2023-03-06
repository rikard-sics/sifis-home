#/bin/bash

# Build the Jar files for the Group & EDHOC Applications
./build-edhoc-apps.sh
./build-group-apps.sh

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
echo 'ADD OscoreAsServer.jar /apps' >> Dockerfile-OscoreAsServer
echo 'ADD lib /apps/lib/' >> Dockerfile-OscoreAsServer
echo '' >> Dockerfile-OscoreAsServer
echo 'ENTRYPOINT ["java", "-jar", "/apps/OscoreAsServer.jar"]' >> Dockerfile-OscoreAsServer

# OscoreRsServer: Group Manager (ACE Resource Server)
cp ../Dockerfile.base Dockerfile-OscoreRsServer
echo 'ADD OscoreRsServer.jar /apps' >> Dockerfile-OscoreRsServer
echo 'ADD lib /apps/lib/' >> Dockerfile-OscoreRsServer
echo '' >> Dockerfile-OscoreRsServer
echo 'ENTRYPOINT ["java", "-jar", "/apps/OscoreRsServer.jar"]' >> Dockerfile-OscoreRsServer

# OscoreAsRsClient: Group OSCORE Server/Client which will join the group(s)
#TODO: Add CMD arguments
cp ../Dockerfile.base Dockerfile-OscoreAsRsClient
echo 'ADD OscoreAsRsClient.jar /apps' >> Dockerfile-OscoreAsRsClient
echo 'ADD lib /apps/lib/' >> Dockerfile-OscoreAsRsClient
echo '' >> Dockerfile-OscoreAsRsClient
echo 'ENTRYPOINT ["java", "-jar", "/apps/OscoreAsRsClient.jar"]' >> Dockerfile-OscoreAsRsClient

# Adversary: Adversary for testing attacks against the group(s)
cp ../Dockerfile.base Dockerfile-Adversary
echo 'ADD Adversary.jar /apps' >> Dockerfile-Adversary
echo 'ADD lib /apps/lib/' >> Dockerfile-Adversary
echo '' >> Dockerfile-Adversary
echo 'ENTRYPOINT ["java", "-jar", "/apps/Adversary.jar"]' >> Dockerfile-Adversary


## Build images for EDHOC Applications

cd ../edhoc


# Phase0Server: CoAP-only server
cp ../Dockerfile.base Dockerfile-Phase0Server
echo 'ADD Phase0Server.jar /apps' >> Dockerfile-Phase0Server
echo 'ADD lib /apps/lib/' >> Dockerfile-Phase0Server
echo '' >> Dockerfile-Phase0Server
echo 'ENTRYPOINT ["java", "-jar", "/apps/Phase0Server.jar"]' >> Dockerfile-Phase0Server

# Phase0Client: CoAP-only client
cp ../Dockerfile.base Dockerfile-Phase0Client
echo 'ADD Phase0Client.jar /apps' >> Dockerfile-Phase0Client
echo 'ADD lib /apps/lib/' >> Dockerfile-Phase0Client
echo '' >> Dockerfile-Phase0Client
echo 'ENTRYPOINT ["java", "-jar", "/apps/Phase0Client.jar"]' >> Dockerfile-Phase0Client

# Phase1Server: EDHOC server using method 0 and no optimized request
cp ../Dockerfile.base Dockerfile-Phase1Server
echo 'ADD Phase1Server.jar /apps' >> Dockerfile-Phase1Server
echo 'ADD lib /apps/lib/' >> Dockerfile-Phase1Server
echo '' >> Dockerfile-Phase1Server
echo 'ENTRYPOINT ["java", "-jar", "/apps/Phase1Server.jar"]' >> Dockerfile-Phase1Server

# Phase1Client: EDHOC client using method 0 and no optimized request
cp ../Dockerfile.base Dockerfile-Phase1Client
echo 'ADD Phase1Client.jar /apps' >> Dockerfile-Phase1Client
echo 'ADD lib /apps/lib/' >> Dockerfile-Phase1Client
echo '' >> Dockerfile-Phase1Client
echo 'ENTRYPOINT ["java", "-jar", "/apps/Phase1Client.jar"]' >> Dockerfile-Phase1Client

# Phase2Server: EDHOC server using method 3 and no optimized request
cp ../Dockerfile.base Dockerfile-Phase2Server
echo 'ADD Phase2Server.jar /apps' >> Dockerfile-Phase2Server
echo 'ADD lib /apps/lib/' >> Dockerfile-Phase2Server
echo '' >> Dockerfile-Phase2Server
echo 'ENTRYPOINT ["java", "-jar", "/apps/Phase2Server.jar"]' >> Dockerfile-Phase2Server

# Phase2Client: EDHOC client using method 3 and no optimized request
cp ../Dockerfile.base Dockerfile-Phase2Client
echo 'ADD Phase2Client.jar /apps' >> Dockerfile-Phase2Client
echo 'ADD lib /apps/lib/' >> Dockerfile-Phase2Client
echo '' >> Dockerfile-Phase2Client
echo 'ENTRYPOINT ["java", "-jar", "/apps/Phase2Client.jar"]' >> Dockerfile-Phase2Client

# Phase3Server: EDHOC server using method 0 and the optimized request
cp ../Dockerfile.base Dockerfile-Phase3Server
echo 'ADD Phase3Server.jar /apps' >> Dockerfile-Phase3Server
echo 'ADD lib /apps/lib/' >> Dockerfile-Phase3Server
echo '' >> Dockerfile-Phase3Server
echo 'ENTRYPOINT ["java", "-jar", "/apps/Phase3Server.jar"]' >> Dockerfile-Phase3Server

# Phase3Client: EDHOC client using method 0 and the optimized request
cp ../Dockerfile.base Dockerfile-Phase3Client
echo 'ADD Phase3Client.jar /apps' >> Dockerfile-Phase3Client
echo 'ADD lib /apps/lib/' >> Dockerfile-Phase3Client
echo '' >> Dockerfile-Phase3Client
echo 'ENTRYPOINT ["java", "-jar", "/apps/Phase3Client.jar"]' >> Dockerfile-Phase3Client

# Phase4Server: EDHOC server using method 3 and the optimized request
cp ../Dockerfile.base Dockerfile-Phase4Server
echo 'ADD Phase4Server.jar /apps' >> Dockerfile-Phase4Server
echo 'ADD lib /apps/lib/' >> Dockerfile-Phase4Server
echo '' >> Dockerfile-Phase4Server
echo 'ENTRYPOINT ["java", "-jar", "/apps/Phase4Server.jar"]' >> Dockerfile-Phase4Server

# Phase4Client: EDHOC client using method 3 and the optimized request
cp ../Dockerfile.base Dockerfile-Phase4Client
echo 'ADD Phase4Client.jar /apps' >> Dockerfile-Phase4Client
echo 'ADD lib /apps/lib/' >> Dockerfile-Phase4Client
echo '' >> Dockerfile-Phase4Client
echo 'ENTRYPOINT ["java", "-jar", "/apps/Phase4Client.jar"]' >> Dockerfile-Phase4Client

