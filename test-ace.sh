#!/bin/sh

# Execute Junit tests for ACE and save as Jacoco test reports

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

# Copy library Jar files from Californium to ACE lib folder
mkdir ace/lib
cp californium-extended/cf-oscore/target/cf-oscore-3.1.0-SNAPSHOT.jar ace/lib

cp californium-extended/californium-core/target/californium-core-3.1.0-SNAPSHOT.jar ace/lib

cp californium-extended/scandium-core/target/scandium-3.1.0-SNAPSHOT.jar ace/lib

cp californium-extended/element-connector/target/element-connector-3.1.0-SNAPSHOT.jar ace/lib

# Install Mysql server (seems to be already installed in Github test environment)
if [ "$1" != "--skip-mysql-install" ]
then
  echo "mysql-server mysql-server/root_password password root" | sudo debconf-set-selections
  echo "mysql-server mysql-server/root_password_again password root" | sudo debconf-set-selections
  sudo apt-get -y install mysql-server
  sudo systemctl start mysql.service
  echo "root" > ace/db.pwd
fi

# Run ACE JUnit tests
# https://stackoverflow.com/questions/65092032/maven-build-failed-but-exit-code-is-still-0

cd ace
echo "*** Building and running ACE JUnit tests ***"
# mvn clean install | tee mvn_res
mvn clean org.jacoco:jacoco-maven-plugin:0.8.6:prepare-agent install org.jacoco:jacoco-maven-plugin:0.8.6:report | tee mvn_res
if grep 'BUILD FAILURE' mvn_res;then exit 1; fi;
if grep 'BUILD SUCCESS' mvn_res;then exit 0; else exit 1; fi;
rm mvn_res
cd ..

