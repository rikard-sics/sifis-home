#!/bin/bash

# Builds DHT-enabled standalone Jar files for the EDHOC Applications
# Phase0Server: CoAP-only server
# Phase0Client: CoAP-only client
# Phase1Server: EDHOC server using method 0 and no optimized request
# Phase1Client: EDHOC client using method 0 and no optimized request
# Phase2Server: EDHOC server using method 3 and no optimized request
# Phase2Client: EDHOC client using method 3 and no optimized request
# Phase3Server: EDHOC server using method 0 and the optimized request
# Phase3Client: EDHOC client using method 0 and the optimized request
# Phase4Server: EDHOC server using method 3 and the optimized request
# Phase4Client: EDHOC client using method 3 and the optimized request

# Fail script with error if any command fails
set -e

# Separately install this dependency (if needed)
FILE=~/.m2/repository/com/github/peteroupc/numbers/1.4.3/numbers-1.4.3.jar
if [ -f "$FILE" ]; then
    echo "$FILE exists."
else 
    echo "$FILE does not exist."
    mvn org.apache.maven.plugins:maven-dependency-plugin:2.8:get -Dartifact=com.github.peteroupc:numbers:1.4.3
fi

# Build Californium (if needed)
FILE=californium-extended/cf-oscore/target/cf-oscore-3.1.0-SNAPSHOT.jar
if [ -f "$FILE" ]; then
    echo "$FILE exists."
else 
    echo "$FILE does not exist."
    cd californium-extended
    mvn -DskipTests clean install
    cd ..
fi

# Copy library Jar files from Californium to EDHOC Apps lib folder
# Dependencies for building with Maven
mkdir -p edhoc-applications/lib
cp californium-extended/cf-oscore/target/cf-oscore-3.1.0-SNAPSHOT.jar edhoc-applications/lib

cp californium-extended/californium-core/target/californium-core-3.1.0-SNAPSHOT.jar edhoc-applications/lib

cp californium-extended/scandium-core/target/scandium-3.1.0-SNAPSHOT.jar edhoc-applications/lib

cp californium-extended/element-connector/target/element-connector-3.1.0-SNAPSHOT.jar edhoc-applications/lib

cp californium-extended/cf-edhoc/target/cf-edhoc-3.1.0-SNAPSHOT.jar edhoc-applications/lib

# Run EDHOC Apps JUnit tests
# https://stackoverflow.com/questions/65092032/maven-build-failed-but-exit-code-is-still-0

cd edhoc-applications
echo "*** Building EDHOC Applications ***"
# mvn clean install | tee mvn_res
mvn clean org.jacoco:jacoco-maven-plugin:0.8.6:prepare-agent install org.jacoco:jacoco-maven-plugin:0.8.6:report | tee mvn_res
if grep 'BUILD FAILURE' mvn_res;then exit 1; fi;
if grep 'BUILD SUCCESS' mvn_res;then echo "BUILD SUCCESS"; else exit 1; fi;
rm mvn_res
cd ..

# Copy necessary dependencies
# Dependencies for running
cp -n ~/.m2/repository/org/bouncycastle/bcpkix-jdk15on/1.67/bcpkix-jdk15on-1.67.jar edhoc-applications/lib
cp -n ~/.m2/repository/org/bouncycastle/bcprov-jdk15on/1.67/bcprov-jdk15on-1.67.jar edhoc-applications/lib
cp -n ~/.m2/repository/com/upokecenter/cbor/4.3.0/cbor-4.3.0.jar edhoc-applications/lib
cp -n ~/.m2/repository/net/i2p/crypto/eddsa/0.3.0/eddsa-0.3.0.jar edhoc-applications/lib
cp -n ~/.m2/repository/com/sun/activation/jakarta.activation/2.0.0/jakarta.activation-2.0.0.jar edhoc-applications/lib
cp -n ~/.m2/repository/jakarta/websocket/jakarta.websocket-api/2.0.0/jakarta.websocket-api-2.0.0.jar edhoc-applications/lib
cp -n ~/.m2/repository/jakarta/xml/bind/jakarta.xml.bind-api/3.0.0/jakarta.xml.bind-api-3.0.0.jar edhoc-applications/lib
cp -n ~/.m2/repository/com/github/peteroupc/numbers/1.4.3/numbers-1.4.3.jar edhoc-applications/lib
cp -n ~/.m2/repository/org/glassfish/tyrus/tyrus-client/2.0.0/tyrus-client-2.0.0.jar edhoc-applications/lib
cp -n ~/.m2/repository/org/glassfish/tyrus/tyrus-container-grizzly-client/2.0.0/tyrus-container-grizzly-client-2.0.0.jar edhoc-applications/lib
cp -n ~/.m2/repository/javax/websocket/javax.websocket-api/1.1/javax.websocket-api-1.1.jar edhoc-applications/lib
cp -n ~/.m2/repository/org/glassfish/tyrus/tyrus-core/2.0.0/tyrus-core-2.0.0.jar edhoc-applications/lib #TODO: Add in pom?
cp -n ~/.m2/repository/org/glassfish/tyrus/tyrus-spi/2.0.0/tyrus-spi-2.0.0.jar edhoc-applications/lib #TODO: Add in pom?
cp -n ~/.m2/repository/org/slf4j/slf4j-api/1.7.36/slf4j-api-1.7.36.jar edhoc-applications/lib
cp -n ~/.m2/repository/org/slf4j/jul-to-slf4j/1.7.36/jul-to-slf4j-1.7.36.jar edhoc-applications/lib
cp -n ~/.m2/repository/org/slf4j/slf4j-simple/1.7.36/slf4j-simple-1.7.36.jar edhoc-applications/lib

#More
cp -n ~/.m2/repository/org/eclipse/californium/californium-core/3.1.0-SNAPSHOT/californium-core-3.1.0-SNAPSHOT.jar edhoc-applications/lib
cp -n ~/.m2/repository/org/eclipse/californium/cf-edhoc/3.1.0-SNAPSHOT/cf-edhoc-3.1.0-SNAPSHOT.jar edhoc-applications/lib
cp -n ~/.m2/repository/org/eclipse/californium/element-connector/3.1.0-SNAPSHOT/element-connector-3.1.0-SNAPSHOT.jar edhoc-applications/lib
cp -n ~/.m2/repository/org/eclipse/californium/cf-oscore/3.1.0-SNAPSHOT/cf-oscore-3.1.0-SNAPSHOT.jar edhoc-applications/lib
cp -n ~/.m2/repository/org/eclipse/californium/scandium/3.1.0-SNAPSHOT/scandium-3.1.0-SNAPSHOT.jar edhoc-applications/lib
cp -n ~/.m2/repository/javax/websocket/javax.websocket-api/1.1/javax.websocket-api-1.1.jar edhoc-applications/lib
cp -n ~/.m2/repository/com/google/code/gson/gson/2.9.1/gson-2.9.1.jar edhoc-applications/lib
cp -n ~/.m2/repository/junit/junit/4.12/junit-4.12.jar edhoc-applications/lib
cp -n ~/.m2/repository/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar edhoc-applications/lib
cp -n ~/.m2/repository/com/upokecenter/cbor/4.3.0/cbor-4.3.0.jar edhoc-applications/lib
cp -n ~/.m2/repository/com/github/peteroupc/numbers/1.4.3/numbers-1.4.3.jar edhoc-applications/lib
cp -n ~/.m2/repository/org/glassfish/tyrus/tyrus-client/2.0.0/tyrus-client-2.0.0.jar edhoc-applications/lib
cp -n ~/.m2/repository/org/glassfish/tyrus/tyrus-core/2.0.0/tyrus-core-2.0.0.jar edhoc-applications/lib
cp -n ~/.m2/repository/org/glassfish/tyrus/tyrus-spi/2.0.0/tyrus-spi-2.0.0.jar edhoc-applications/lib
cp -n ~/.m2/repository/jakarta/websocket/jakarta.websocket-api/2.0.0/jakarta.websocket-api-2.0.0.jar edhoc-applications/lib
cp -n ~/.m2/repository/jakarta/xml/bind/jakarta.xml.bind-api/3.0.0/jakarta.xml.bind-api-3.0.0.jar edhoc-applications/lib
cp -n ~/.m2/repository/com/sun/activation/jakarta.activation/2.0.0/jakarta.activation-2.0.0.jar edhoc-applications/lib
cp -n ~/.m2/repository/org/glassfish/tyrus/tyrus-container-grizzly-client/2.0.0/tyrus-container-grizzly-client-2.0.0.jar edhoc-applications/lib
cp -n ~/.m2/repository/org/glassfish/grizzly/grizzly-framework/3.0.0-M1/grizzly-framework-3.0.0-M1.jar edhoc-applications/lib
cp -n ~/.m2/repository/org/glassfish/grizzly/grizzly-http-server/3.0.0-M1/grizzly-http-server-3.0.0-M1.jar edhoc-applications/lib
cp -n ~/.m2/repository/org/glassfish/grizzly/grizzly-http/3.0.0-M1/grizzly-http-3.0.0-M1.jar edhoc-applications/lib
cp -n ~/.m2/repository/org/slf4j/slf4j-api/1.7.36/slf4j-api-1.7.36.jar edhoc-applications/lib
cp -n ~/.m2/repository/org/slf4j/jul-to-slf4j/1.7.36/jul-to-slf4j-1.7.36.jar edhoc-applications/lib
cp -n ~/.m2/repository/ch/qos/logback/logback-classic/1.2.11/logback-classic-1.2.11.jar edhoc-applications/lib
cp -n ~/.m2/repository/ch/qos/logback/logback-core/1.2.11/logback-core-1.2.11.jar edhoc-applications/lib
cp -n ~/.m2/repository/org/slf4j/slf4j-simple/1.7.36/slf4j-simple-1.7.36.jar edhoc-applications/lib
cp -n ~/.m2/repository/junit/junit/4.13.2/junit-4.13.2.jar edhoc-applications/lib
cp -n ~/.m2/repository/org/hamcrest/hamcrest-library/1.3/hamcrest-library-1.3.jar edhoc-applications/lib
cp -n ~/.m2/repository/org/mockito/mockito-core/2.28.2/mockito-core-2.28.2.jar edhoc-applications/lib
cp -n ~/.m2/repository/net/bytebuddy/byte-buddy/1.9.10/byte-buddy-1.9.10.jar edhoc-applications/lib
cp -n ~/.m2/repository/net/bytebuddy/byte-buddy-agent/1.9.10/byte-buddy-agent-1.9.10.jar edhoc-applications/lib
cp -n ~/.m2/repository/org/objenesis/objenesis/2.6/objenesis-2.6.jar edhoc-applications/lib
cp -n ~/.m2/repository/com/upokecenter/cbor/4.5.2/cbor-4.5.2.jar edhoc-applications/lib
cp -n ~/.m2/repository/com/github/peteroupc/numbers/1.8.2/numbers-1.8.2.jar edhoc-applications/lib
cp -n ~/.m2/repository/net/i2p/crypto/eddsa/0.3.0/eddsa-0.3.0.jar edhoc-applications/lib
cp -n ~/.m2/repository/com/google/guava/guava/30.0-android/guava-30.0-android.jar edhoc-applications/lib
cp -n ~/.m2/repository/com/google/guava/failureaccess/1.0.1/failureaccess-1.0.1.jar edhoc-applications/lib
cp -n ~/.m2/repository/com/google/guava/listenablefuture/9999.0-empty-to-avoid-conflict-with-guava/listenablefuture-9999.0-empty-to-avoid-conflict-with-guava.jar edhoc-applications/lib
cp -n ~/.m2/repository/org/checkerframework/checker-compat-qual/2.5.5/checker-compat-qual-2.5.5.jar edhoc-applications/lib
cp -n ~/.m2/repository/com/google/errorprone/error_prone_annotations/2.3.4/error_prone_annotations-2.3.4.jar edhoc-applications/lib
cp -n ~/.m2/repository/com/google/j2objc/j2objc-annotations/1.3/j2objc-annotations-1.3.jar edhoc-applications/lib
cp -n ~/.m2/repository/org/apache/httpcomponents/client5/httpclient5/5.1.3/httpclient5-5.1.3.jar edhoc-applications/lib
cp -n ~/.m2/repository/org/apache/httpcomponents/core5/httpcore5/5.1.3/httpcore5-5.1.3.jar edhoc-applications/lib
cp -n ~/.m2/repository/org/apache/httpcomponents/core5/httpcore5-h2/5.1.3/httpcore5-h2-5.1.3.jar edhoc-applications/lib
cp -n ~/.m2/repository/org/bouncycastle/bcpkix-jdk15on/1.67/bcpkix-jdk15on-1.67.jar edhoc-applications/lib
cp -n ~/.m2/repository/org/bouncycastle/bcprov-jdk15on/1.67/bcprov-jdk15on-1.67.jar edhoc-applications/lib

# Printing of where Jar ended up and how to run it
#echo "Jar file containing EDHOC Applications built under edhoc-applications/target/edhoc-applications-0.0.2-SNAPSHOT.jar" 
#echo "Run using (from folder target): "
#echo "java -cp edhoc-applications-0.0.2-SNAPSHOT.jar:../lib/* se.sics.edhocapps.Phase0Server"
#echo "java -cp edhoc-applications-0.0.2-SNAPSHOT.jar:../lib/* se.sics.edhocapps.Phase0Client -help"
#echo "java -cp edhoc-applications-0.0.2-SNAPSHOT.jar:../lib/* se.sics.edhocapps.Phase1Server"
#echo "java -cp edhoc-applications-0.0.2-SNAPSHOT.jar:../lib/* se.sics.edhocapps.Phase1Client -help"
#echo "java -cp edhoc-applications-0.0.2-SNAPSHOT.jar:../lib/* se.sics.edhocapps.Phase2Server"
#echo "java -cp edhoc-applications-0.0.2-SNAPSHOT.jar:../lib/* se.sics.edhocapps.Phase2Client -help"
#echo "java -cp edhoc-applications-0.0.2-SNAPSHOT.jar:../lib/* se.sics.edhocapps.Phase3Server"
#echo "java -cp edhoc-applications-0.0.2-SNAPSHOT.jar:../lib/* se.sics.edhocapps.Phase3Client -help"
#echo "java -cp edhoc-applications-0.0.2-SNAPSHOT.jar:../lib/* se.sics.edhocapps.Phase4Server"
#echo "java -cp edhoc-applications-0.0.2-SNAPSHOT.jar:../lib/* se.sics.edhocapps.Phase4Client -help"

# Build individual Jar files
cd edhoc-applications/target
echo "Main-Class: se.sics.edhocapps.Phase0Server" > Manifest.addition
echo "Class-Path: lib/cf-oscore-3.1.0-SNAPSHOT.jar" >> Manifest.addition
echo "  lib/californium-core-3.1.0-SNAPSHOT.jar" >> Manifest.addition
echo "  lib/cf-edhoc-3.1.0-SNAPSHOT.jar" >> Manifest.addition
echo "  lib/element-connector-3.1.0-SNAPSHOT.jar" >> Manifest.addition
echo "  lib/scandium-3.1.0-SNAPSHOT.jar" >> Manifest.addition
echo "  lib/javax.websocket-api-1.1.jar" >> Manifest.addition
echo "  lib/gson-2.9.1.jar" >> Manifest.addition
echo "  lib/junit-4.12.jar" >> Manifest.addition
echo "  lib/hamcrest-core-1.3.jar" >> Manifest.addition
echo "  lib/cbor-4.3.0.jar" >> Manifest.addition
echo "  lib/numbers-1.4.3.jar" >> Manifest.addition
echo "  lib/tyrus-client-2.0.0.jar" >> Manifest.addition
echo "  lib/tyrus-core-2.0.0.jar" >> Manifest.addition
echo "  lib/tyrus-spi-2.0.0.jar" >> Manifest.addition
echo "  lib/jakarta.websocket-api-2.0.0.jar" >> Manifest.addition
echo "  lib/jakarta.xml.bind-api-3.0.0.jar" >> Manifest.addition
echo "  lib/jakarta.activation-2.0.0.jar" >> Manifest.addition
echo "  lib/tyrus-container-grizzly-client-2.0.0.jar" >> Manifest.addition
echo "  lib/grizzly-framework-3.0.0-M1.jar" >> Manifest.addition
echo "  lib/grizzly-http-server-3.0.0-M1.jar" >> Manifest.addition
echo "  lib/grizzly-http-3.0.0-M1.jar" >> Manifest.addition
echo "  lib/slf4j-api-1.7.36.jar" >> Manifest.addition
echo "  lib/jul-to-slf4j-1.7.36.jar" >> Manifest.addition
echo "  lib/logback-classic-1.2.11.jar" >> Manifest.addition
echo "  lib/logback-core-1.2.11.jar" >> Manifest.addition
echo "  lib/slf4j-simple-1.7.36.jar" >> Manifest.addition
echo "  lib/junit-4.13.2.jar" >> Manifest.addition
echo "  lib/hamcrest-library-1.3.jar" >> Manifest.addition
echo "  lib/mockito-core-2.28.2.jar" >> Manifest.addition
echo "  lib/byte-buddy-1.9.10.jar" >> Manifest.addition
echo "  lib/byte-buddy-agent-1.9.10.jar" >> Manifest.addition
echo "  lib/objenesis-2.6.jar" >> Manifest.addition
echo "  lib/cbor-4.5.2.jar" >> Manifest.addition
echo "  lib/numbers-1.8.2.jar" >> Manifest.addition
echo "  lib/eddsa-0.3.0.jar" >> Manifest.addition
echo "  lib/guava-30.0-android.jar" >> Manifest.addition
echo "  lib/failureaccess-1.0.1.jar" >> Manifest.addition
echo "  lib/listenablefuture-9999.0-empty-to-avoid-conflict-with-guava.jar" >> Manifest.addition
echo "  lib/checker-compat-qual-2.5.5.jar" >> Manifest.addition
echo "  lib/error_prone_annotations-2.3.4.jar" >> Manifest.addition
echo "  lib/j2objc-annotations-1.3.jar" >> Manifest.addition
echo "  lib/httpclient5-5.1.3.jar" >> Manifest.addition
echo "  lib/httpcore5-5.1.3.jar" >> Manifest.addition
echo "  lib/httpcore5-h2-5.1.3.jar" >> Manifest.addition
echo "  lib/bcpkix-jdk15on-1.67.jar" >> Manifest.addition
echo "  lib/bcprov-jdk15on-1.67.jar" >> Manifest.addition
echo -e "\n" >> Manifest.addition

cp edhoc-applications-0.0.2-SNAPSHOT.jar edhoc-applications-0.0.2-SNAPSHOT.jar.bk
unzip -o edhoc-applications-0.0.2-SNAPSHOT.jar META-INF/MANIFEST.MF
head -c -1 -q META-INF/MANIFEST.MF Manifest.addition > META-INF/MANIFEST.MF

zip edhoc-applications-0.0.2-SNAPSHOT.jar META-INF/MANIFEST.MF
cp edhoc-applications-0.0.2-SNAPSHOT.jar ../Phase0Server.jar

sed -i "s/Phase0Server/Phase0Client/" META-INF/MANIFEST.MF
zip edhoc-applications-0.0.2-SNAPSHOT.jar META-INF/MANIFEST.MF
cp edhoc-applications-0.0.2-SNAPSHOT.jar ../Phase0Client.jar

sed -i "s/Phase0Client/Phase1Server/" META-INF/MANIFEST.MF
zip edhoc-applications-0.0.2-SNAPSHOT.jar META-INF/MANIFEST.MF
cp edhoc-applications-0.0.2-SNAPSHOT.jar ../Phase1Server.jar

sed -i "s/Phase1Server/Phase1Client/" META-INF/MANIFEST.MF
zip edhoc-applications-0.0.2-SNAPSHOT.jar META-INF/MANIFEST.MF
cp edhoc-applications-0.0.2-SNAPSHOT.jar ../Phase1Client.jar

sed -i "s/Phase1Client/Phase2Server/" META-INF/MANIFEST.MF
zip edhoc-applications-0.0.2-SNAPSHOT.jar META-INF/MANIFEST.MF
cp edhoc-applications-0.0.2-SNAPSHOT.jar ../Phase2Server.jar

sed -i "s/Phase2Server/Phase2Client/" META-INF/MANIFEST.MF
zip edhoc-applications-0.0.2-SNAPSHOT.jar META-INF/MANIFEST.MF
cp edhoc-applications-0.0.2-SNAPSHOT.jar ../Phase2Client.jar

sed -i "s/Phase2Client/Phase3Server/" META-INF/MANIFEST.MF
zip edhoc-applications-0.0.2-SNAPSHOT.jar META-INF/MANIFEST.MF
cp edhoc-applications-0.0.2-SNAPSHOT.jar ../Phase3Server.jar

sed -i "s/Phase3Server/Phase3Client/" META-INF/MANIFEST.MF
zip edhoc-applications-0.0.2-SNAPSHOT.jar META-INF/MANIFEST.MF
cp edhoc-applications-0.0.2-SNAPSHOT.jar ../Phase3Client.jar

sed -i "s/Phase3Client/Phase4Server/" META-INF/MANIFEST.MF
zip edhoc-applications-0.0.2-SNAPSHOT.jar META-INF/MANIFEST.MF
cp edhoc-applications-0.0.2-SNAPSHOT.jar ../Phase4Server.jar

sed -i "s/Phase4Server/Phase4Client/" META-INF/MANIFEST.MF
zip edhoc-applications-0.0.2-SNAPSHOT.jar META-INF/MANIFEST.MF
cp edhoc-applications-0.0.2-SNAPSHOT.jar ../Phase4Client.jar

cp edhoc-applications-0.0.2-SNAPSHOT.jar.bk edhoc-applications-0.0.2-SNAPSHOT.jar

rm -rf META-INF
rm Manifest.addition
cd ..
cd ..

echo "Jar files containing EDHOC Applications built under edhoc-applications/. Execute them with lib in the same folder. Use -help to see possible arguments when applicable."

