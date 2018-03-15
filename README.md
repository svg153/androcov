# androcov
Instrument an Android app to measure the test coverage.

Take method coverage as an example, the steps to measure the method coverage of dyn. analysis are as follows:

1. Analyze the app and get a list of all statements belonging to classes which share the app package name.
2. Instrument the app to print log messages when it reaches each statement.
3. Run the app and collect the log messages.
4. Get the list of reached methods.
5. Calculate the coverage (i.e. the percentage of reached statements).

# Installation

```
./gradlew build
```

# Executable Jar

```
./gradlew shadowJar
```

# Usage

```
java -jar androcov.jar -o <directory> -i <APK> [-h] -sdk <android.jar>
 -o,--output <directory>            path to output dir
 -i,--input <APK>                   path to target APK
 -h,--help                          print this help message
 -sdk,--android_jar <android.jar>   path to android.jar
```
