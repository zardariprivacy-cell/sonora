set -e
# Sonora v3.3 build WITH AdMob (v3.2 pipeline + v3.3 "everything 3D, colourful, centered" polish) — same manual ads pipeline proven on Ilmora
# (~/workspace/apps/manualbuild-ads.sh): Google Mobile Ads SDK 23.6.0 AARs
# pre-exploded in ~/workspace/tools/ads-sdk/{libs,deps}; manifests merged,
# resources linked with --extra-packages, everything dexed with d8.
export JAVA_HOME=~/workspace/tools/jdk
BT=~/workspace/tools/android-sdk/build-tools/34.0.0
AJAR=~/workspace/tools/android-sdk/platforms/android-34/android.jar
PROJ=~/workspace/apps/sonora-android
SDKLIBS=~/workspace/tools/ads-sdk/libs
DEPS=~/workspace/tools/ads-sdk/deps
export PATH=$JAVA_HOME/bin:$BT:$PATH
cd $PROJ
rm -rf build-v33
mkdir -p build-v33/compiled build-v33/gen build-v33/classes build-v33/dex build-v33/stage

echo "== merge manifests =="
python3 - <<'PYEOF'
import os
import xml.etree.ElementTree as ET

ANDROID = "http://schemas.android.com/apk/res/android"
ET.register_namespace("android", ANDROID)

libs_dir = os.path.expanduser("~/workspace/tools/ads-sdk/libs")
s = open("app/src/main/AndroidManifest.xml").read()

perms, perm_seen = [], set()
app_children, child_seen = [], set()
queries_blocks = []
extra_packages = []

def aname(el):
    return el.get("{%s}name" % ANDROID) or ""

for lib in sorted(os.listdir(libs_dir)):
    mpath = os.path.join(libs_dir, lib, "AndroidManifest.xml")
    if not os.path.exists(mpath):
        continue
    try:
        tree = ET.parse(mpath)
    except Exception as e:
        print("skip unparseable manifest", lib, e)
        continue
    root = tree.getroot()
    pkg = root.get("package")
    if pkg and os.path.isdir(os.path.join(libs_dir, lib, "res")):
        extra_packages.append(pkg)
    for el in root:
        if el.tag == "uses-permission":
            n = aname(el)
            if n and n not in perm_seen:
                perm_seen.add(n)
                perms.append(ET.tostring(el, encoding="unicode"))
        elif el.tag == "queries":
            queries_blocks.append(ET.tostring(el, encoding="unicode"))
        elif el.tag == "application":
            for child in el:
                key = (child.tag, aname(child))
                if key in child_seen:
                    continue
                child_seen.add(key)
                app_children.append(ET.tostring(child, encoding="unicode"))

print("extra packages (with res):", len(extra_packages))
print("lib permissions:", len(perms), "lib app components:", len(app_children))

perm_text = "\n    ".join(p.strip() for p in perms)
s = s.replace("    <application", perm_text + "\n\n    <application", 1)
comp_text = "\n        ".join(c.strip() for c in app_children)
s = s.replace("    </application>", "        " + comp_text + "\n    </application>", 1)
if queries_blocks:
    q = "\n    ".join(b.strip() for b in queries_blocks)
    s = s.replace("</manifest>", "    " + q + "\n</manifest>", 1)

# Substitute the Gradle-style ${applicationId} placeholder that AAR manifests
# use in provider authorities (ads-lite, startup-runtime, work-runtime).
# Left literal, every app built with this pipeline declares the SAME
# authorities ("${applicationId}.mobileadsinitprovider",
# "${applicationId}.androidx-startup") and Android refuses to install a
# second such app: INSTALL_FAILED_CONFLICTING_PROVIDER.
s = s.replace("${applicationId}", "com.nawazishali.sonora")
assert "${applicationId}" not in s, "unsubstituted applicationId placeholder remains"

open("build-v33/AndroidManifest.xml", "w").write(s)
open("build-v33/extra-packages.txt", "w").write(":".join(extra_packages))
print("merged manifest written")
PYEOF

echo "== aapt2 compile (app + libs) =="
aapt2 compile --dir app/src/main/res -o build-v33/compiled/res-app.zip
i=0
for libdir in $SDKLIBS/*/; do
  if [ -d "$libdir/res" ]; then
    i=$((i+1))
    aapt2 compile --dir "$libdir/res" -o "build-v33/compiled/res-lib$i.zip" || echo "WARN: res compile failed for $libdir"
  fi
done
echo "lib res zips: $(ls build-v33/compiled/ | grep -c lib)"

echo "== aapt2 link =="
EXTRA=$(cat build-v33/extra-packages.txt)
aapt2 link -o build-v33/stage/app.apk -I $AJAR --manifest build-v33/AndroidManifest.xml -A app/src/main/assets \
  --java build-v33/gen --extra-packages "$EXTRA" \
  --min-sdk-version 24 --target-sdk-version 34 --version-code 13 --version-name 3.3 \
  build-v33/compiled/*.zip

echo "== javac =="
CP="$AJAR"
for j in $SDKLIBS/*/classes.jar; do CP="$CP:$j"; done
for j in $DEPS/*.jar; do
  case "$j" in
    *kotlin-stdlib-common*|*kotlin-stdlib-jdk7*|*kotlin-stdlib-jdk8*|*kotlinx-coroutines-core-1.7.1.jar) echo "skip dup-prone jar: $j" ;;
    *guava-31.1-android.jar) CP="$CP:$DEPS/guava-nolf.jar" ;;
    *guava-nolf.jar) ;;
    *) CP="$CP:$j" ;;
  esac
done
echo "$CP" > build-v33/classpath.txt
find app/src/main/java build-v33/gen -name "*.java" > build-v33/sources.txt
javac -encoding UTF-8 --release 8 -cp "$CP" -d build-v33/classes @build-v33/sources.txt

echo "== jar + d8 =="
(cd build-v33/classes && jar cf ../classes.jar .)
INPUTS="build-v33/classes.jar"
for j in $SDKLIBS/*/classes.jar; do INPUTS="$INPUTS $j"; done
for j in $DEPS/*.jar; do
  case "$j" in
    *kotlin-stdlib-common*|*kotlin-stdlib-jdk7*|*kotlin-stdlib-jdk8*|*kotlinx-coroutines-core-1.7.1.jar) ;;
    *guava-31.1-android.jar) INPUTS="$INPUTS $DEPS/guava-nolf.jar" ;;
    *guava-nolf.jar) ;;
    *) INPUTS="$INPUTS $j" ;;
  esac
done
d8 --min-api 24 --lib $AJAR --output build-v33/dex $INPUTS
ls -la build-v33/dex/

echo "== package dex =="
cp build-v33/dex/classes*.dex build-v33/stage/
(cd build-v33/stage && zip -q app.apk classes*.dex && rm classes*.dex)

echo "== zipalign + sign =="
zipalign -f -p 4 build-v33/stage/app.apk build-v33/app-aligned.apk
KS=~/workspace/apps/debug.keystore
apksigner sign --ks $KS --ks-pass pass:android --key-pass pass:android --out build-v33/Sonora.apk build-v33/app-aligned.apk
apksigner verify build-v33/Sonora.apk && echo "signature OK"
ls -la build-v33/Sonora.apk
aapt2 dump badging build-v33/Sonora.apk | head -6
echo "== SONORA v3.3 BUILD DONE =="
