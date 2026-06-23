ACTION REQUIRED — Before building this project:

1. Download the official gradle-wrapper.jar:
   
   Option A (Recommended): Copy from any working Android project:
   cp ~/.gradle/wrapper/dists/gradle-8.2.1-bin/*/gradle-8.2.1/lib/gradle-wrapper.jar \
      app/src/main/assets/gradle-wrapper.jar

   Option B: Download from official source:
   curl -L -o app/src/main/assets/gradle-wrapper.jar \
     "https://raw.githubusercontent.com/gradle/gradle/v8.2.1/gradle/wrapper/gradle-wrapper.jar"

   Option C: Generate using Gradle itself (if gradle installed):
   gradle wrapper --gradle-version=8.2.1
   cp gradle/wrapper/gradle-wrapper.jar app/src/main/assets/gradle-wrapper.jar

2. Also copy to the wrapper directory:
   cp app/src/main/assets/gradle-wrapper.jar gradle/wrapper/gradle-wrapper.jar

3. Build normally:
   ./gradlew assembleDebug

WHY: gradle-wrapper.jar must be bundled in assets so generated project ZIPs
contain a valid jar. The previous code attempted to download it from GitHub 
at runtime which was unreliable (wrong version, network failures).
