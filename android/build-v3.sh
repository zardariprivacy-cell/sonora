set -e
export JAVA_HOME=~/workspace/tools/jdk
BT=~/workspace/tools/android-sdk/build-tools/34.0.0
AJAR=~/workspace/tools/android-sdk/platforms/android-34/android.jar
PROJ=~/workspace/apps/sonora-android
export PATH=$JAVA_HOME/bin:$BT:$PATH
cd $PROJ
rm -rf build-v3
mkdir -p build-v3/compiled build-v3/gen build-v3/classes build-v3/dex build-v3/stage
echo "== aapt2 compile =="
aapt2 compile --dir app/src/main/res -o build-v3/compiled/res.zip
echo "== aapt2 link =="
aapt2 link -o build-v3/stage/app.apk -I $AJAR --manifest app/src/main/AndroidManifest.xml --java build-v3/gen --min-sdk-version 24 --target-sdk-version 34 --version-code 10 --version-name 3.0 build-v3/compiled/res.zip
echo "== javac =="
find app/src/main/java build-v3/gen -name "*.java" > build-v3/sources.txt
javac -encoding UTF-8 --release 8 -cp $AJAR -d build-v3/classes @build-v3/sources.txt
echo "== jar + d8 =="
(cd build-v3/classes && jar cf ../classes.jar .)
d8 --min-api 24 --output build-v3/dex build-v3/classes.jar
echo "== package dex =="
cp build-v3/dex/classes.dex build-v3/stage/classes.dex
(cd build-v3/stage && zip -q app.apk classes.dex && rm classes.dex)
echo "== zipalign =="
zipalign -f -p 4 build-v3/stage/app.apk build-v3/app-aligned.apk
echo "== sign =="
KS=~/workspace/apps/debug.keystore
apksigner sign --ks $KS --ks-pass pass:android --key-pass pass:android --out build-v3/Sonora.apk build-v3/app-aligned.apk
echo "== verify =="
apksigner verify build-v3/Sonora.apk && echo "signature OK"
ls -la build-v3/Sonora.apk
aapt2 dump badging build-v3/Sonora.apk | head -6
echo "== SONORA v3 BUILD DONE =="
