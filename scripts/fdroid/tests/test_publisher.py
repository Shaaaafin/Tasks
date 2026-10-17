import io
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import build_repository as publisher


class RepositoryPublisherTests(unittest.TestCase):
    def archive(self, extra=None):
        content = {
            "source/fastlane/metadata/android/en-US/title.txt": b"Tasks",
            "source/fastlane/metadata/android/en-US/short_description.txt": b"Private tasks",
            "source/fastlane/metadata/android/en-US/full_description.txt": b"Task manager",
            "source/fastlane/metadata/android/en-US/images/icon.png": b"sample-icon",
            "source/fastlane/metadata/android/en-US/images/phoneScreenshots/1.png": b"sample-screen",
            "source/private-signing-key.p12": b"must-not-be-copied",
        }
        content.update(extra or {})
        buffer = io.BytesIO()
        with zipfile.ZipFile(buffer, "w", zipfile.ZIP_DEFLATED) as archive:
            for name, data in content.items():
                archive.writestr(name, data)
        return buffer.getvalue()

    def test_only_tagged_store_metadata_is_extracted(self):
        with tempfile.TemporaryDirectory() as folder, patch.object(publisher, "download", return_value=self.archive()):
            target = Path(folder) / "metadata"
            screenshots = publisher.copy_release_metadata("owner/repo", "a" * 40, target)
            self.assertEqual(["1.png"], [file.name for file in screenshots])
            self.assertFalse(list(Path(folder).rglob("*.p12")))
            self.assertEqual("Tasks", (target / "title.txt").read_text())

    def test_path_traversal_in_metadata_is_rejected(self):
        data = self.archive({"source/fastlane/metadata/android/en-US/../../outside.txt": b"outside"})
        with tempfile.TemporaryDirectory() as folder, patch.object(publisher, "download", return_value=data):
            with self.assertRaisesRegex(RuntimeError, "Unsafe metadata path"):
                publisher.copy_release_metadata("owner/repo", "a" * 40, Path(folder) / "metadata")
            self.assertFalse((Path(folder) / "outside.txt").exists())

    def test_executable_metadata_is_rejected(self):
        data = self.archive({"source/fastlane/metadata/android/en-US/installer.exe": b"executable"})
        with tempfile.TemporaryDirectory() as folder, patch.object(publisher, "download", return_value=data):
            with self.assertRaisesRegex(RuntimeError, "Unexpected metadata type"):
                publisher.copy_release_metadata("owner/repo", "a" * 40, Path(folder) / "metadata")

    def inspect(self, certificate="a" * 64, package="dev.quiettasks", debug=False, manifest=""):
        settings = {"apkCertificateSha256": "a" * 64, "applicationId": "dev.quiettasks"}
        signature = f"Signer #1 certificate SHA-256 digest: {certificate}\n"
        badging = f"package: name='{package}' versionCode='2' versionName='0.2.0'\n"
        if debug:
            badging += "application-debuggable\n"
        with patch.object(publisher, "checked_output", side_effect=[signature, badging, manifest]):
            return publisher.inspect_apk(Path("release.apk"), Path("tools"), settings)

    def test_expected_release_identity_is_retained(self):
        self.assertEqual({"versionCode": 2, "versionName": "0.2.0", "certificateSha256": "a" * 64}, self.inspect())

    def test_unexpected_signing_key_is_rejected(self):
        with self.assertRaisesRegex(RuntimeError, "signing certificate"):
            self.inspect(certificate="b" * 64)

    def test_unexpected_application_is_rejected(self):
        with self.assertRaisesRegex(RuntimeError, "application ID"):
            self.inspect(package="different.app")

    def test_debug_apk_is_rejected(self):
        with self.assertRaisesRegex(RuntimeError, "Debug APK"):
            self.inspect(debug=True)

    def test_test_only_apk_is_rejected(self):
        with self.assertRaisesRegex(RuntimeError, "test-only"):
            self.inspect(manifest="A: android:testOnly(0x01010272)=(type 0x12)0xffffffff\n")


if __name__ == "__main__":
    unittest.main()
