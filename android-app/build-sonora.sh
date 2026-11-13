set -e
export JAVA_HOME=~/workspace/tools/jdk
BT=~/workspace/tools/android-sdk/build-tools/34.0.0
AJAR=~/workspace/tools/android-sdk/platforms/android-34/android.jar
PROJ=~/workspace/apps/sonora-android
export PATH=$JAVA_HOME/bin:$BT:$PATH
cd $PROJ
rm -rf build-manual
mkdir -p build-manual/compiled build-manual/gen build-manual/classes build-manual/dex build-manual/stage
echo "== aapt2 compile =="
aapt2 compile --dir app/src/main/res -o build-manual/compiled/res.zip
echo "== aapt2 link =="
aapt2 link -o build-manual/stage/app.apk -I $AJAR --manifest app/src/main/AndroidManifest.xml --java build-manual/gen --min-sdk-version 24 --target-sdk-version 34 --version-code 3 --version-name 2.1 build-manual/compiled/res.zip
echo "== javac =="
find app/src/main/java build-manual/gen -name "*.java" > build-manual/sources.txt
javac -encoding UTF-8 --release 8 -cp $AJAR -d build-manual/classes @build-manual/sources.txt
echo "== jar + d8 =="
(cd build-manual/classes && jar cf ../classes.jar .)
d8 --min-api 24 --output build-manual/dex build-manual/classes.jar
echo "== package dex + assets =="
cp build-manual/dex/classes.dex build-manual/stage/classes.dex
cp -r app/src/main/assets build-manual/stage/assets
(cd build-manual/stage && zip -q app.apk classes.dex && zip -qr app.apk assets && rm classes.dex && rm -rf assets)
echo "== zipalign =="
zipalign -f -p 4 build-manual/stage/app.apk build-manual/app-aligned.apk
echo "== sign =="
KS=~/workspace/apps/debug.keystore
apksigner sign --ks $KS --ks-pass pass:android --key-pass pass:android --out build-manual/Sonora.apk build-manual/app-aligned.apk
echo "== verify =="
apksigner verify build-manual/Sonora.apk && echo "signature OK"
ls -la build-manual/Sonora.apk
aapt2 dump badging build-manual/Sonora.apk | head -5
unzip -l build-manual/Sonora.apk | grep -E "assets/www/(index.html|app.js|styles.css)" 
echo "== SONORA BUILD DONE =="
