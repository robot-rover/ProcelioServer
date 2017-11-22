#!/usr/bin/env bash
echo `pwd`
if [ -f jooq-3.10.1.jar ]; then
    echo "Jooq Main Jar Exists, skipping"
else
    echo "Downloading Jooq Main Jar"
    curl http://search.maven.org/remotecontent?filepath=org/jooq/jooq/3.10.1/jooq-3.10.1.jar --output jooq-3.10.1.jar
fi
if [ -f jooq-codegen-3.10.1.jar ]; then
    echo "Jooq Codegen Jar Exists, skipping"
else
    echo "Downloading Jooq Codegen Jar"
    curl https://search.maven.org/remotecontent?filepath=org/jooq/jooq-codegen/3.10.1/jooq-codegen-3.10.1.jar --output jooq-codegen-3.10.1.jar
fi
if [ -f jooq-meta-3.10.1.jar ]; then
    echo "Jooq Meta Jar Exists, skipping"
else
    echo "Downloading Jooq Meta Jar"
    curl https://search.maven.org/remotecontent?filepath=org/jooq/jooq-meta/3.10.1/jooq-meta-3.10.1.jar --output jooq-meta-3.10.1.jar
fi
if [ -f sqlite-jdbc-3.21.0.jar ]; then
    echo "SQLite JDBC Driver Jar exists, skipping"
else
    echo "Downloading SQLite JDBC Driver"
    curl https://search.maven.org/remotecontent?filepath=org/xerial/sqlite-jdbc/3.21.0/sqlite-jdbc-3.21.0.jar --output sqlite-jdbc-3.21.0.jar
fi
echo "Dropping Usertable"
sqlite3 ../procelio.db 'drop table usertable'
echo "Dropping Authtable"
sqlite3 ../procelio.db 'drop table authtable'
echo "Generating Database"
./schemaBackup.txt
java -classpath jooq-3.10.1.jar:jooq-meta-3.10.1.jar:jooq-codegen-3.10.1.jar:sqlite-jdbc-3.21.0.jar:. org.jooq.util.GenerationTool /library.xml