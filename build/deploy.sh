echo "=============================="
echo "====     General Prep     ===="
echo "=============================="
mkdir all_os_release
TIMESTAMP=$(date -u "+%Y-%m-%d_%H-%M-%S")

echo "=============================="
echo "====   Preparing Windows  ===="
echo "=============================="
ant cross-build-windows
zip -r all_os_release/$(echo $TIMESTAMP)_windows.zip windows/work


echo "=============================="
echo "====  Preparing MacOSX    ===="
echo "=============================="

echo ">>> Building for Mac"
ant cross-build-macosx

echo ">>> Creating keychain for signing"
security create-keychain -p $KEYCHAIN_PASS build.keychain
security default-keychain -s build.keychain

echo ">>> Unlocking keychain"
security unlock-keychain -p $KEYCHAIN_PASS build.keychain

echo ">>> Decrypting and registering certificate"
openssl aes-256-cbc -K $encrypted_0d213ffbe1d5_key -iv $encrypted_0d213ffbe1d5_iv -in mac_developerID.cer.enc -out mac_developerID.cer -d
security import mac_developerID.cer -k build.keychain -T /usr/bin/codesign

echo ">>> Signing application"
ant macosx-dist-sign

echo ">>> Locking keychain"
security lock-keychain build.keychain

echo ">>> Creating zip"
zip -r all_os_release/$(echo $TIMESTAMP)_macosx.zip macosx/work


echo "=============================="
echo "==== Preparing Linux x86  ===="
echo "=============================="
ant cross-build-linux-x64
zip -r all_os_release/$(echo $TIMESTAMP)_linux_x64.zip linux/work


echo "=============================="
echo "==== Preparing Linux ARM  ===="
echo "=============================="
ant cross-build-linux-aarch64
zip -r all_os_release/$(echo $TIMESTAMP)_linux_aarch64.zip linux/work


echo "=============================="
echo "====       Deploying      ===="
echo "=============================="
aws s3 cp all_os_release/$(echo $TIMESTAMP)_windows.zip s3://processing-build-open-source/windows/$(echo $TIMESTAMP)_windows.zip
aws s3 cp all_os_release/$(echo $TIMESTAMP)_macosx.zip s3://processing-build-open-source/macosx/$(echo $TIMESTAMP)_macosx.zip
aws s3 cp all_os_release/$(echo $TIMESTAMP)_linux_x64.zip s3://processing-build-open-source/linux/$(echo $TIMESTAMP)_linux_x64.zip
aws s3 cp all_os_release/$(echo $TIMESTAMP)_linux_aarch64.zip s3://processing-build-open-source/linux/$(echo $TIMESTAMP)_linux_aarch64.zip
echo $TIMESTAMP > LATEST.txt
aws s3 cp LATEST.txt s3://processing-build-open-source


echo "=============================="
echo "====         Done         ===="
echo "=============================="
