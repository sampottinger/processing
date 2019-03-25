mkdir all_os_release


echo "=============================="
echo "====   Preparing Windows  ===="
echo "=============================="
ant cross-build-windows
zip -r all_os_release/windows.zip windows/work


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
zip -r all_os_release/macosx.zip macosx/work


echo "=============================="
echo "==== Preparing Linux x86  ===="
echo "=============================="
ant cross-build-linux-x64
zip -r all_os_release/linux_x64.zip linux/work


echo "=============================="
echo "==== Preparing Linux ARM  ===="
echo "=============================="
ant cross-build-linux-aarch64
zip -r all_os_release/linux_aarch64.zip linux/work


echo "=============================="
echo "====       Packing        ===="
echo "=============================="
TIMESTAMP=$(date -u "+%Y-%m-%d_%H-%M-%S")
zip -r all_os_release_$TIMESTAMP.zip all_os_release


echo "=============================="
echo "====       Deploying      ===="
echo "=============================="
aws s3 cp all_os_release_$TIMESTAMP.zip s3://processing-build-open-source
echo $TIMESTAMP > LATEST.txt
aws s3 cp LATEST.txt s3://processing-build-open-source
