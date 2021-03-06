#!/bin/bash

function getPropertyFromFile()
{
  # substitute “.” with “\.” so that we can use it as sed expression
  propertyName=`echo $1 | sed -e 's/\./\\\./g'`
  fileName=$2;
  cat $fileName | sed -n -e "s/^[ ]*//g;/^#/d;s/^$propertyName=//p" | tail -1
}

GITHUB_OAUTH=`getPropertyFromFile github.oauth $GITHUB_PROPERTIES`

cd $Repository
git init
git add .
git add -f gradle/wrapper/gradle-wrapper.jar
git commit -m "Initial template"
git remote add origin https://${GITHUB_OAUTH}:x-oauth-basic@github.com/nebula-plugins/$Repository
git push --quiet origin master

cd ..
