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

# Copy library Jar files from Californium to EDHOC Apps lib folder
mkdir edhoc-applications/lib
cp californium-extended/cf-oscore/target/cf-oscore-3.1.0-SNAPSHOT-sources.jar edhoc-applications/lib
cp californium-extended/cf-oscore/target/cf-oscore-3.1.0-SNAPSHOT.jar edhoc-applications/lib

cp californium-extended/californium-core/target/californium-core-3.1.0-SNAPSHOT.jar edhoc-applications/lib
cp californium-extended/californium-core/target/californium-core-3.1.0-SNAPSHOT-sources.jar edhoc-applications/lib
cp californium-extended/californium-core/target/californium-core-3.1.0-SNAPSHOT-tests.jar edhoc-applications/lib

cp californium-extended/scandium-core/target/scandium-3.1.0-SNAPSHOT-sources.jar edhoc-applications/lib
cp californium-extended/scandium-core/target/scandium-3.1.0-SNAPSHOT.jar edhoc-applications/lib
cp californium-extended/scandium-core/target/scandium-3.1.0-SNAPSHOT-tests.jar edhoc-applications/lib

cp californium-extended/element-connector/target/element-connector-3.1.0-SNAPSHOT-sources.jar edhoc-applications/lib
cp californium-extended/element-connector/target/element-connector-3.1.0-SNAPSHOT-tests.jar edhoc-applications/lib
cp californium-extended/element-connector/target/element-connector-3.1.0-SNAPSHOT.jar edhoc-applications/lib

cp californium-extended/cf-edhoc/target/cf-edhoc-3.1.0-SNAPSHOT-sources.jar edhoc-applications/lib
cp californium-extended/cf-edhoc/target/cf-edhoc-3.1.0-SNAPSHOT.jar edhoc-applications/lib

# Run EDHOC Apps JUnit tests
# https://stackoverflow.com/questions/65092032/maven-build-failed-but-exit-code-is-still-0

cd edhoc-applications
echo "*** Building EDHOC Applications ***"
# mvn clean install | tee mvn_res
mvn clean org.jacoco:jacoco-maven-plugin:0.8.6:prepare-agent install org.jacoco:jacoco-maven-plugin:0.8.6:report | tee mvn_res
if grep 'BUILD FAILURE' mvn_res;then exit 1; fi;
if grep 'BUILD SUCCESS' mvn_res;then exit 0; else exit 1; fi; #FIXME
rm mvn_res
cd ..

# TODO: Add copying of needed Jars
#bcpkix-jdk15on-1.67.jar
#bcprov-jdk15on-1.67.jar
#californium-core-3.1.0-SNAPSHOT.jar
#cbor-4.3.0.jar
#cf-edhoc-3.1.0-SNAPSHOT.jar
#cf-oscore-3.1.0-SNAPSHOT.jar
#eddsa-0.3.0.jar
#element-connector-3.1.0-SNAPSHOT.jar
#jakarta.activation-2.0.0.jar
#jakarta.websocket-api-2.0.0.jar
#jakarta.xml.bind-api-3.0.0.jar
#jcl-over-slf4j-1.7.5.jar
#numbers-1.4.3.jar
#scandium-3.1.0-SNAPSHOT.jar
#slf4j-api-1.7.5.jar
#slf4j-log4j12-1.7.5.jar
#slf4j-simple-1.7.5.jar

# Add printing of where Jar ended up and how to run it
