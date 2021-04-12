<h1 align="center">
  <a href="https://github.com/orangebeard-io/JUnit4-listener">
    <img src="https://raw.githubusercontent.com/orangebeard-io/JUnit4-listener/master/.github/logo.png" alt="Orangebeard.io JUnit4 Listener" height="200">
  </a>
  <br>Orangebeard.io JUnit4 Listener<br>
</h1>

<h4 align="center">Orangebeard listener for the Java <a href="https://junit.org/JUnit4/" target="_blank" rel="noopener">JUnit</a> test framework.</h4>

<p align="center">
  <a href="https://repo.maven.apache.org/maven2/io/orangebeard/JUnit4-listener/">
    <img src="https://img.shields.io/maven-central/v/io.orangebeard/JUnit4-listener?style=flat-square"
      alt="MVN Version" />
  </a>
  <a href="https://github.com/orangebeard-io/JUnit4-listener/actions">
    <img src="https://img.shields.io/github/workflow/status/orangebeard-io/JUnit4-listener/release?style=flat-square"
      alt="Build Status" />
  </a>
  <a href="https://github.com/orangebeard-io/JUnit4-listener/blob/master/LICENSE.txt">
    <img src="https://img.shields.io/github/license/orangebeard-io/JUnit4-listener?style=flat-square"
      alt="License" />
  </a>
</p>

<div align="center">
  <h4>
    <a href="https://orangebeard.io">Orangebeard</a> |
    <a href="#installation">Installation</a> |
    <a href="#configuration">Configuration</a>
  </h4>
</div>

## Installation

### Install the mvn package

Add the dependency to your pom:
```xml
<dependency>
    <groupId>io.orangebeard</groupId>
    <artifactId>junit4-listener</artifactId>
    <version>version</version>
    <scope>test</scope>
</dependency>
```

## Configuration

For general usage of the extension, add or modify the surefire plugin:

Create a new file named `orangebeard.properties` in the test resources folder. Add the following properties:

```properties
orangebeard.endpoint=<ORANGEBEARD-ENDPOINT>
orangebeard.accessToken=<XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX>
orangebeard.project=<PROJECT_NAME>
orangebeard.testset=<TESTSET_NAME>

# optional
orangebeard.description=<DESCRIPTION>
orangebeard.attributes=key:value; value;
```

### Environment variables

The properties above can be set as environment variables as well. Environment variables will override property values. In the environment variables, it is allowed to replace the dot by an underscore.
for example: ```orangebeard_endpoint``` as an environment variable will work as well.
