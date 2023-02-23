## SIFIS-HOME WP3 codebase

Joint codebase for SIFIS-HOME including ACE, EDHOC, OSCORE, Group OSCORE, and Group Joining (plus associated things).

### Building and importing

1. Run the `config.sh` script

2. Start Eclipse, then import the following projects:
  
    - californium-extended
    - ace
    - group-applications
    - edhoc-applications

3. To "ace" add the following folders to the build path:

    - californium-core
    - cf-oscore
    - element-connector
    - scandium

4. To "group-applications" add the following folders to the build path:
    - ace
    - californium-core
    - cf-oscore
    - element-connector
    - scandium

5. To "edhoc-applications" add the following folders to the build path:
    - californium-core
    - cf-edhoc
    - cf-oscore
    - element-connector

To add dependencies:

*Right click project->Properties->Java Build Path->Add...*

### Select Maven profile

If you are developing in Eclipse (and possibly other IDEs) choose the "eclipse" Maven profile for ACE:

*Right click on "ace"->Maven->Select Maven Profile...* (CTRL+Alt+P)

Deactive the "default" profile, and activate the "eclipse" profile.

*Right click on "group-applications->Maven->Select Maven Profile...* (CTRL+Alt+P)

Deactive the "default" profile, and activate the "eclipse" profile.

*Right click on "edhoc-applications"->Maven->Select Maven Profile...* (CTRL+Alt+P)

Deactive the "default" profile, and activate the "eclipse" profile.


### MySQL installation

Note that mySQL is needed for the ACE parts to run correctly. To install it use:
```
sudo apt-get install mysql-server
```

Then place a file under ace/db.pwd and group-applications/db.pwd with the database root password.


### Updating the JCE (Java Cryptography Extensions)

If some of the JUnit tests fail due to "invalid key size" you may need to update the JCE. In such case follow these instructions:

https://www.andreafortuna.org/2016/06/08/java-tips-how-to-fix-the-invalidkeyexception-illegal-key-size-or-default-parameters-runtime/


### JUnit tests

To only run the JUnit tests:

For Californium: `test-californium.sh`

For ACE: `test-ace.sh --skip-mysql-install`


### Code coverage reports

Automatic code coverage reports are generated with Jacoco and can be found at the following links:

[EDHOC](https://sifis-home.github.io/wp3-solutions/reports/edhoc/) (californium-extended/cf-edhoc)

[OSCORE & Group OSCORE](https://sifis-home.github.io/wp3-solutions/reports/oscore/) (californium-extended/cf-oscore)

[ACE & Group Joining](https://sifis-home.github.io/wp3-solutions/reports/ace/) (ace)

