#!/bin/sh

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

# Build ACE (if needed)
FILE=ace/target/ace-0.0.1-SNAPSHOT.jar
if [ -f "$FILE" ]; then
    echo "$FILE exists."
else 
    echo "$FILE does not exist."
    
    # Copy library Jar files from Californium to ACE lib folder
    mkdir ace/lib
    cp californium-extended/cf-oscore/target/cf-oscore-3.1.0-SNAPSHOT.jar ace/lib
    cp californium-extended/californium-core/target/californium-core-3.1.0-SNAPSHOT.jar ace/lib
    cp californium-extended/scandium-core/target/scandium-3.1.0-SNAPSHOT.jar ace/lib
    cp californium-extended/element-connector/target/element-connector-3.1.0-SNAPSHOT.jar ace/lib
    
    cd ace
    mvn -DskipTests clean install
    cd ..
fi

# Copy library Jar files from Californium to Group Apps lib folder
mkdir group-applications/lib
cp californium-extended/cf-oscore/target/cf-oscore-3.1.0-SNAPSHOT.jar group-applications/lib

cp californium-extended/californium-core/target/californium-core-3.1.0-SNAPSHOT.jar group-applications/lib

cp californium-extended/scandium-core/target/scandium-3.1.0-SNAPSHOT.jar group-applications/lib

cp californium-extended/element-connector/target/element-connector-3.1.0-SNAPSHOT.jar group-applications/lib

cp californium-extended/cf-edhoc/target/cf-edhoc-3.1.0-SNAPSHOT.jar group-applications/lib

cp ace/target/ace-0.0.1-SNAPSHOT.jar group-applications/lib

# Run Group Apps JUnit tests
# https://stackoverflow.com/questions/65092032/maven-build-failed-but-exit-code-is-still-0

cd group-applications
echo "*** Building Group Applications ***"
# mvn clean install | tee mvn_res
mvn clean org.jacoco:jacoco-maven-plugin:0.8.6:prepare-agent install org.jacoco:jacoco-maven-plugin:0.8.6:report | tee mvn_res
if grep 'BUILD FAILURE' mvn_res;then exit 1; fi;
if grep 'BUILD SUCCESS' mvn_res;then echo "BUILD SUCCESS"; else exit 1; fi;
rm mvn_res
cd ..

# Copy necessary dependencies
cp -n ~/.m2/repository/org/bouncycastle/bcpkix-jdk15on/1.67/bcpkix-jdk15on-1.67.jar group-applications/lib
cp -n ~/.m2/repository/org/bouncycastle/bcprov-jdk15on/1.67/bcprov-jdk15on-1.67.jar group-applications/lib
cp -n ~/.m2/repository/com/upokecenter/cbor/4.3.0/cbor-4.3.0.jar group-applications/lib
cp -n ~/.m2/repository/net/i2p/crypto/eddsa/0.3.0/eddsa-0.3.0.jar group-applications/lib
cp -n ~/.m2/repository/com/sun/activation/jakarta.activation/2.0.0/jakarta.activation-2.0.0.jar group-applications/lib
cp -n ~/.m2/repository/jakarta/websocket/jakarta.websocket-api/2.0.0/jakarta.websocket-api-2.0.0.jar group-applications/lib
cp -n ~/.m2/repository/jakarta/xml/bind/jakarta.xml.bind-api/3.0.0/jakarta.xml.bind-api-3.0.0.jar group-applications/lib
cp -n ~/.m2/repository/org/slf4j/jcl-over-slf4j/1.7.5/jcl-over-slf4j-1.7.5.jar group-applications/lib
cp -n ~/.m2/repository/com/github/peteroupc/numbers/1.4.3/numbers-1.4.3.jar group-applications/lib
cp -n ~/.m2/repository/org/slf4j/slf4j-api/1.7.5/slf4j-api-1.7.5.jar group-applications/lib
cp -n ~/.m2/repository/org/slf4j/slf4j-log4j12/1.7.5/slf4j-log4j12-1.7.5.jar group-applications/lib
cp -n ~/.m2/repository/org/slf4j/slf4j-simple/1.7.5/slf4j-simple-1.7.5.jar group-applications/lib

# Printing of where Jar ended up and how to run it
echo "Jar file containing Group Applications built under group-applications/target/group-applications-0.0.2-SNAPSHOT.jar" 
echo "Run using (from folder target): "
echo "java -cp group-applications-0.0.2-SNAPSHOT.jar:../lib/* se.sics.prototype.apps.OscoreAsServer --help"
echo "java -cp group-applications-0.0.2-SNAPSHOT.jar:../lib/* se.sics.prototype.apps.OscoreRsServer --help"
echo "java -cp group-applications-0.0.2-SNAPSHOT.jar:../lib/* se.sics.prototype.apps.OscoreAsRsClient --help"
echo "java -cp group-applications-0.0.2-SNAPSHOT.jar:../lib/* se.sics.prototype.apps.Adversary --help"
