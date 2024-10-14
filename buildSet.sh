#!/bin/bash

# compile the project
mvn clean compile test

# run the project
java -cp target/classes bguspl.set.Main

echo ""
echo "Set terminated"

# clean up the project
mvn clean
