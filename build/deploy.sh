mkdir all_os_release

echo "=============================="
echo "====   Getting CLI Setup  ===="
echo "=============================="
pip install awscli --upgrade --user

echo "=============================="
echo "====   Preparing Windows  ===="
echo "=============================="
ant cross-build-windows
zip -r all_os_release/windows.zip windows/work

echo "=============================="
echo "====  Preparing MacOSX    ===="
echo "=============================="
ant cross-build-macosx
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
