import base64
import datetime as dt
import io
import json
from pathlib import Path
import subprocess
import tempfile
from types import SimpleNamespace
import unittest
from unittest import mock
import zipfile

from tools.release.release_tool import (
    GradleMetadata,
    PublicBaseline,
    ReleaseError,
    Version,
    calculate_version_code,
    changelog_body,
    changelog_release_date,
    command_prepare,
    command_verify,
    command_verify_apk,
    command_verify_tbox,
    parse_release_date,
    prepare_changelog,
    read_gradle_metadata_text,
    replace_gradle_metadata,
    verify_hotfix_control_plane,
)


class VersionTest(unittest.TestCase):
    def test_semantic_version_bumps_are_explicit(self):
        version = Version.parse("9.5.2")
        self.assertEqual(Version(9, 5, 3), version.bumped("patch"))
        self.assertEqual(Version(9, 6, 0), version.bumped("minor"))
        self.assertEqual(Version(10, 0, 0), version.bumped("major"))

    def test_prerelease_version_is_rejected(self):
        with self.assertRaises(ReleaseError):
            Version.parse("9.6.0-rc.1")


class VersionCodeTest(unittest.TestCase):
    def test_first_release_of_day_uses_sequence_one(self):
        self.assertEqual(
            2026083101,
            calculate_version_code(
                current_code=2026082801,
                release_date=dt.date(2026, 8, 31),
                explicit_code=None,
            ),
        )

    def test_second_release_of_day_increments_sequence(self):
        self.assertEqual(
            2026083102,
            calculate_version_code(
                current_code=2026083101,
                release_date=dt.date(2026, 8, 31),
                explicit_code=None,
            ),
        )

    def test_non_monotonic_explicit_code_is_rejected(self):
        with self.assertRaises(ReleaseError):
            calculate_version_code(
                current_code=2026083101,
                release_date=dt.date(2026, 8, 31),
                explicit_code=2026083001,
            )


class ChangelogTest(unittest.TestCase):
    SOURCE = """# Changes

## Unreleased

### Fixed

- one defect.

## 9.5.2 — 2026-08-28

- previous.
"""

    def test_prepare_moves_unreleased_body_to_version(self):
        prepared = prepare_changelog(
            self.SOURCE,
            version=Version.parse("9.5.3"),
            release_date=dt.date(2026, 8, 31),
        )
        self.assertEqual("", changelog_body(prepared, "Unreleased"))
        self.assertEqual(
            "### Fixed\n\n- one defect.",
            changelog_body(prepared, "9.5.3"),
        )
        self.assertEqual(
            dt.date(2026, 8, 31),
            changelog_release_date(prepared, Version.parse("9.5.3")),
        )

    def test_empty_unreleased_is_rejected(self):
        source = "# Changes\n\n## Unreleased\n\n## 9.5.2 — 2026-08-28\n"
        with self.assertRaises(ReleaseError):
            prepare_changelog(
                source,
                version=Version.parse("9.5.3"),
                release_date=dt.date(2026, 8, 31),
            )

    def test_compact_iso_date_is_rejected(self):
        with self.assertRaises(ReleaseError):
            parse_release_date("20260831", label="release date")


class GradleMetadataTest(unittest.TestCase):
    SOURCE = """android {
    defaultConfig {
        versionCode = 2026082801
        versionName = "9.5.2"
    }
}
"""

    def test_metadata_is_updated_without_touching_other_lines(self):
        updated = replace_gradle_metadata(
            self.SOURCE,
            version=Version.parse("9.6.0"),
            version_code=2026083101,
        )
        metadata = read_gradle_metadata_text(updated)
        self.assertEqual(Version(9, 6, 0), metadata.version)
        self.assertEqual(2026083101, metadata.version_code)
        self.assertIn("defaultConfig", updated)


class TboxVerificationTest(unittest.TestCase):
    def test_generated_material_matches_without_plaintext_in_dex(self):
        secret = b"release-only-value"
        mask = bytes((index * 17 + 3) % 256 for index in range(len(secret)))
        data = bytes(left ^ right for left, right in zip(secret, mask))
        mask_text = base64.b64encode(mask).decode()
        data_text = base64.b64encode(data).decode()
        source = (
            "public final class BuildConfig {\n"
            f'  public static final String TBOX_SECRET_MASK = "{mask_text}";\n'
            f'  public static final String TBOX_SECRET_DATA = "{data_text}";\n'
            "}\n"
        )
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            build_config = root / "BuildConfig.java"
            apk = root / "release.apk"
            build_config.write_text(source, encoding="utf-8")
            with zipfile.ZipFile(apk, "w") as archive:
                archive.writestr(
                    "classes.dex",
                    b"masked production dex"
                    + mask_text.encode()
                    + data_text.encode(),
                )
            with mock.patch.dict(
                "os.environ",
                {"H9_TBOX_PASSWORD": secret.decode()},
                clear=False,
            ):
                command_verify_tbox(
                    SimpleNamespace(build_config=build_config, apk=apk)
                )

    def test_masked_material_must_be_present_in_dex(self):
        secret = b"release-only-value"
        mask = bytes([3] * len(secret))
        data = bytes(left ^ right for left, right in zip(secret, mask))
        source = (
            "public final class BuildConfig {\n"
            f'  public static final String TBOX_SECRET_MASK = "{base64.b64encode(mask).decode()}";\n'
            f'  public static final String TBOX_SECRET_DATA = "{base64.b64encode(data).decode()}";\n'
            "}\n"
        )
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            build_config = root / "BuildConfig.java"
            apk = root / "release.apk"
            build_config.write_text(source, encoding="utf-8")
            with zipfile.ZipFile(apk, "w") as archive:
                archive.writestr("classes.dex", b"material deliberately absent")
            with mock.patch.dict(
                "os.environ",
                {"H9_TBOX_PASSWORD": secret.decode()},
                clear=False,
            ):
                with self.assertRaises(ReleaseError):
                    command_verify_tbox(
                        SimpleNamespace(build_config=build_config, apk=apk)
                    )

    def test_plaintext_in_dex_is_rejected(self):
        secret = b"must-not-leak"
        mask = bytes([7] * len(secret))
        data = bytes(left ^ right for left, right in zip(secret, mask))
        mask_text = base64.b64encode(mask).decode()
        data_text = base64.b64encode(data).decode()
        source = (
            "public final class BuildConfig {\n"
            f'  public static final String TBOX_SECRET_MASK = "{mask_text}";\n'
            f'  public static final String TBOX_SECRET_DATA = "{data_text}";\n'
            "}\n"
        )
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            build_config = root / "BuildConfig.java"
            apk = root / "release.apk"
            build_config.write_text(source, encoding="utf-8")
            with zipfile.ZipFile(apk, "w") as archive:
                archive.writestr(
                    "classes.dex",
                    b"prefix"
                    + secret
                    + mask_text.encode()
                    + data_text.encode()
                    + b"suffix",
                )
            with mock.patch.dict(
                "os.environ",
                {"H9_TBOX_PASSWORD": secret.decode()},
                clear=False,
            ):
                with self.assertRaises(ReleaseError):
                    command_verify_tbox(
                        SimpleNamespace(build_config=build_config, apk=apk)
                    )


class HotfixLineageTest(unittest.TestCase):
    @staticmethod
    def git(repo: Path, *arguments: str) -> str:
        return subprocess.run(
            ("git", *arguments),
            cwd=repo,
            check=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
        ).stdout.strip()

    def create_repository(self, root: Path) -> None:
        self.git(root, "init", "--initial-branch=main")
        self.git(root, "config", "user.name", "Release Test")
        self.git(root, "config", "user.email", "release-test@example.invalid")
        (root / "app").mkdir()
        (root / "app/build.gradle.kts").write_text(
            'versionCode = 2026082801\nversionName = "9.5.2"\n',
            encoding="utf-8",
        )
        (root / "README.md").write_text("baseline\n", encoding="utf-8")
        self.git(root, "add", ".")
        self.git(root, "commit", "-m", "baseline")
        self.git(root, "tag", "v9.5.2")

    def test_hotfix_must_fork_from_previous_tag(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            self.create_repository(repo)
            (repo / "README.md").write_text("future main\n", encoding="utf-8")
            self.git(repo, "commit", "-am", "future main")
            self.git(repo, "switch", "--create", "hotfix/9.5.3", "v9.5.2")
            (repo / "app/src").mkdir(parents=True)
            (repo / "app/src/fix.txt").write_text("fix\n", encoding="utf-8")
            self.git(repo, "add", ".")
            self.git(repo, "commit", "-m", "hotfix")

            verify_hotfix_control_plane(repo, "v9.5.2", "main")

    def test_hotfix_from_future_main_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            self.create_repository(repo)
            (repo / "README.md").write_text("future main\n", encoding="utf-8")
            self.git(repo, "commit", "-am", "future main")
            self.git(repo, "switch", "--create", "hotfix/9.5.3")
            (repo / "app/src").mkdir(parents=True)
            (repo / "app/src/fix.txt").write_text("fix\n", encoding="utf-8")
            self.git(repo, "add", ".")
            self.git(repo, "commit", "-m", "hotfix")

            with self.assertRaises(ReleaseError):
                verify_hotfix_control_plane(repo, "v9.5.2", "main")

    def test_sequential_hotfix_keeps_the_same_main_fork_point(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            self.create_repository(repo)
            (repo / "README.md").write_text("future main\n", encoding="utf-8")
            self.git(repo, "commit", "-am", "future main")
            self.git(repo, "switch", "--create", "hotfix/9.5.3", "v9.5.2")
            (repo / "app/src").mkdir(parents=True)
            (repo / "app/src/fix.txt").write_text("first fix\n", encoding="utf-8")
            self.git(repo, "add", ".")
            self.git(repo, "commit", "-m", "first hotfix")
            self.git(repo, "tag", "v9.5.3")
            (repo / "app/src/fix.txt").write_text("second fix\n", encoding="utf-8")
            self.git(repo, "commit", "-am", "second hotfix")

            verify_hotfix_control_plane(repo, "v9.5.3", "main")


class PrepareFromPublicBaselineTest(unittest.TestCase):
    @staticmethod
    def git(repo: Path, *arguments: str) -> None:
        subprocess.run(
            ("git", *arguments),
            cwd=repo,
            check=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
        )

    def test_minor_after_off_main_hotfix_uses_public_version_code(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            self.git(repo, "init", "--initial-branch=main")
            self.git(repo, "config", "user.name", "Release Test")
            self.git(repo, "config", "user.email", "release-test@example.invalid")
            (repo / "app").mkdir()
            (repo / "app/build.gradle.kts").write_text(
                'versionCode = 2026082801\nversionName = "9.5.2"\n',
                encoding="utf-8",
            )
            (repo / "CHANGELOG.md").write_text(
                "# Changelog\n\n## Unreleased\n\n"
                "## 9.5.2 — 2026-08-28\n\n- baseline.\n",
                encoding="utf-8",
            )
            self.git(repo, "add", ".")
            self.git(repo, "commit", "-m", "baseline")
            self.git(repo, "tag", "v9.5.2")

            self.git(repo, "switch", "--create", "hotfix/9.5.3")
            (repo / "app/build.gradle.kts").write_text(
                'versionCode = 2026083001\nversionName = "9.5.3"\n',
                encoding="utf-8",
            )
            self.git(repo, "commit", "-am", "published hotfix metadata")
            self.git(repo, "tag", "v9.5.3")
            self.git(repo, "switch", "main")
            (repo / "CHANGELOG.md").write_text(
                "# Changelog\n\n## Unreleased\n\n- new feature.\n\n"
                "## 9.5.2 — 2026-08-28\n\n- baseline.\n",
                encoding="utf-8",
            )
            self.git(repo, "commit", "-am", "new feature")

            args = SimpleNamespace(
                repo=repo,
                github_repository="Arkasha18/H9-cluster",
                aapt=None,
                apksigner=None,
                zipalign=None,
                release_type="minor",
                date="2026-09-01",
                version_code=None,
                apply=False,
            )
            baseline = PublicBaseline(
                tag="v9.5.3",
                version=Version.parse("9.5.3"),
                version_code=2026083001,
            )
            with mock.patch(
                "tools.release.release_tool.discover_public_baseline",
                return_value=baseline,
            ), mock.patch("sys.stdout", new_callable=io.StringIO) as output:
                command_prepare(args)

            result = json.loads(output.getvalue())
            self.assertEqual("9.6.0", result["version"])
            self.assertEqual(2026090101, result["version_code"])

    def test_prepare_rejects_current_metadata_that_differs_from_its_tag(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            (repo / "app").mkdir()
            (repo / "app/build.gradle.kts").write_text(
                'versionCode = 2026082801\nversionName = "9.5.2"\n',
                encoding="utf-8",
            )
            args = SimpleNamespace(
                repo=repo,
                github_repository="Arkasha18/H9-cluster",
                aapt=None,
                apksigner=None,
                zipalign=None,
                release_type="minor",
                date="2026-09-01",
                version_code=None,
                apply=False,
            )
            baseline = PublicBaseline(
                tag="v9.5.3",
                version=Version.parse("9.5.3"),
                version_code=2026083001,
            )
            tags = {
                Version.parse("9.5.2"): "v9.5.2",
                Version.parse("9.5.3"): "v9.5.3",
            }
            tag_metadata = (
                GradleMetadata(Version.parse("9.5.3"), 2026083001),
                GradleMetadata(Version.parse("9.5.1"), 2026082801),
            )
            with mock.patch(
                "tools.release.release_tool.discover_public_baseline",
                return_value=baseline,
            ), mock.patch(
                "tools.release.release_tool.stable_tags",
                return_value=tags,
            ), mock.patch(
                "tools.release.release_tool.metadata_at_tag",
                side_effect=tag_metadata,
            ):
                with self.assertRaises(ReleaseError):
                    command_prepare(args)

    def test_verify_minor_after_off_main_hotfix_accepts_shared_history(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            self.git(repo, "init", "--initial-branch=main")
            self.git(repo, "config", "user.name", "Release Test")
            self.git(repo, "config", "user.email", "release-test@example.invalid")
            (repo / "app").mkdir()
            (repo / "app/build.gradle.kts").write_text(
                'versionCode = 2026082801\nversionName = "9.5.2"\n',
                encoding="utf-8",
            )
            (repo / "CHANGELOG.md").write_text(
                "# Changelog\n\n## Unreleased\n\n"
                "## 9.5.2 — 2026-08-28\n\n- baseline.\n",
                encoding="utf-8",
            )
            self.git(repo, "add", ".")
            self.git(repo, "commit", "-m", "baseline")
            self.git(repo, "tag", "v9.5.2")

            self.git(repo, "switch", "--create", "hotfix/9.5.3")
            (repo / "app/build.gradle.kts").write_text(
                'versionCode = 2026083001\nversionName = "9.5.3"\n',
                encoding="utf-8",
            )
            (repo / "CHANGELOG.md").write_text(
                "# Changelog\n\n## Unreleased\n\n"
                "## 9.5.3 — 2026-08-30\n\n- hotfix.\n\n"
                "## 9.5.2 — 2026-08-28\n\n- baseline.\n",
                encoding="utf-8",
            )
            self.git(repo, "commit", "-am", "published hotfix")
            self.git(repo, "tag", "v9.5.3")

            self.git(repo, "switch", "main")
            (repo / "app/build.gradle.kts").write_text(
                'versionCode = 2026090101\nversionName = "9.6.0"\n',
                encoding="utf-8",
            )
            (repo / "CHANGELOG.md").write_text(
                "# Changelog\n\n## Unreleased\n\n"
                "## 9.6.0 — 2026-09-01\n\n- new feature.\n\n"
                "## 9.5.3 — 2026-08-30\n\n- hotfix.\n\n"
                "## 9.5.2 — 2026-08-28\n\n- baseline.\n",
                encoding="utf-8",
            )
            self.git(repo, "commit", "-am", "minor release candidate")
            commit = subprocess.run(
                ("git", "rev-parse", "HEAD"),
                cwd=repo,
                check=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
            ).stdout.strip()
            output_json = repo / "out/release.json"
            output_notes = repo / "out/notes.md"
            args = SimpleNamespace(
                repo=repo,
                version="9.6.0",
                release_type="minor",
                commit=commit,
                source_ref="main",
                main_ref="main",
                previous_tag="v9.5.3",
                previous_version_code=2026083001,
                output_json=output_json,
                output_notes=output_notes,
            )

            with mock.patch("sys.stdout", new_callable=io.StringIO):
                command_verify(args)

            result = json.loads(output_json.read_text(encoding="utf-8"))
            self.assertEqual("v9.5.3", result["previous_tag"])
            self.assertEqual("9.6.0", result["version"])
            self.assertEqual(commit, result["commit"])
            self.assertEqual("v9.5.3", result["previous_tag"])


class ApkVerificationTest(unittest.TestCase):
    BADGING = """package: name='net.adminrunet.h9cluster' versionCode='2026083101' versionName='9.6.0'
sdkVersion:'28'
targetSdkVersion:'28'
native-code: 'arm64-v8a'
"""

    @staticmethod
    def args(apk: Path, **overrides):
        values = {
            "apk": apk,
            "version": "9.6.0",
            "version_code": 2026083101,
            "aapt": "/tools/aapt",
            "apksigner": "/tools/apksigner",
            "zipalign": "/tools/zipalign",
            "expect_unsigned": True,
            "expected_cert_sha256": None,
            "enforce_release_name": False,
            "output_json": None,
        }
        values.update(overrides)
        return SimpleNamespace(**values)

    def test_unsigned_apk_contract_accepts_expected_metadata(self):
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / "candidate-unsigned.apk"
            apk.write_bytes(b"unsigned")

            def fake_run(command, *, cwd, check=True):
                del cwd, check
                if command[0] == "/tools/aapt":
                    return subprocess.CompletedProcess(command, 0, self.BADGING, "")
                return subprocess.CompletedProcess(command, 1, "", "unsigned")

            with mock.patch("tools.release.release_tool.run", side_effect=fake_run), mock.patch(
                "tools.release.release_tool.resolve_tool",
                side_effect=lambda value, name: value or name,
            ):
                command_verify_apk(self.args(apk))

    def test_signed_apk_with_another_certificate_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / "H9_Cluster_v9.6.0_adminrunet_release.apk"
            apk.write_bytes(b"signed")

            def fake_run(command, *, cwd, check=True):
                del cwd, check
                if command[0] == "/tools/aapt":
                    return subprocess.CompletedProcess(command, 0, self.BADGING, "")
                if command[0] == "/tools/apksigner":
                    output = (
                        "Number of signers: 1\n"
                        f"Signer #1 certificate SHA-256 digest: {'a' * 64}\n"
                    )
                    return subprocess.CompletedProcess(command, 0, output, "")
                return subprocess.CompletedProcess(command, 0, "", "")

            args = self.args(
                apk,
                expect_unsigned=False,
                expected_cert_sha256="b" * 64,
                enforce_release_name=True,
            )
            with mock.patch("tools.release.release_tool.run", side_effect=fake_run), mock.patch(
                "tools.release.release_tool.resolve_tool",
                side_effect=lambda value, name: value or name,
            ):
                with self.assertRaises(ReleaseError):
                    command_verify_apk(args)

    def test_ten_signers_does_not_match_single_signer_contract(self):
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / "H9_Cluster_v9.6.0_adminrunet_release.apk"
            apk.write_bytes(b"signed")

            def fake_run(command, *, cwd, check=True):
                del cwd, check
                if command[0] == "/tools/aapt":
                    return subprocess.CompletedProcess(command, 0, self.BADGING, "")
                if command[0] == "/tools/apksigner":
                    output = (
                        "Number of signers: 10\n"
                        f"Signer #1 certificate SHA-256 digest: {'a' * 64}\n"
                    )
                    return subprocess.CompletedProcess(command, 0, output, "")
                return subprocess.CompletedProcess(command, 0, "", "")

            args = self.args(
                apk,
                expect_unsigned=False,
                expected_cert_sha256="a" * 64,
                enforce_release_name=True,
            )
            with mock.patch("tools.release.release_tool.run", side_effect=fake_run), mock.patch(
                "tools.release.release_tool.resolve_tool",
                side_effect=lambda value, name: value or name,
            ):
                with self.assertRaises(ReleaseError):
                    command_verify_apk(args)


if __name__ == "__main__":
    unittest.main()
