set -e
J=app/src/main/java/com/example/cfscanner
mkdir -p $J app/src/main/res/mipmap-xxxhdpi app/src/main/res/drawable-nodpi
mv MainActivity.kt $J/
mv AndroidManifest.xml app/src/main/
mv app-build.gradle.kts app/build.gradle.kts
mv ic_launcher.png app/src/main/res/mipmap-xxxhdpi/
mv logo.png app/src/main/res/drawable-nodpi/
if [ -f debug.keystore ]; then mv debug.keystore app/debug.keystore; fi
