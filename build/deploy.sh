mkdir all_os_release

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
zip -r all_os_release.zip all_os_release
