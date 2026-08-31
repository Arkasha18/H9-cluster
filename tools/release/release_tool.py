#!/usr/bin/env python3
"""Prepare and verify H9 Cluster production releases.

The tool deliberately keeps version selection separate from publication:
``prepare`` updates the release metadata on a human-reviewed branch, while
``verify`` fails closed unless the selected commit is ready to publish.
"""

from __future__ import annotations

import argparse
import base64
import binascii
import dataclasses
import datetime as dt
import hmac
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
from typing import Iterable
import zipfile
from zoneinfo import ZoneInfo


SEMVER_PATTERN = re.compile(r"^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$")
VERSION_CODE_PATTERN = re.compile(r"(?m)^(\s*versionCode\s*=\s*)([0-9]+)(\s*)$")
VERSION_NAME_PATTERN = re.compile(
    r'(?m)^(\s*versionName\s*=\s*")([0-9]+\.[0-9]+\.[0-9]+)("\s*)$'
)
CHANGELOG_HEADING_PATTERN = re.compile(r"(?m)^##\s+(.+?)\s*$")
FULL_SHA_PATTERN = re.compile(r"^[0-9a-f]{40}$")

ANDROID_MAX_VERSION_CODE = 2_100_000_000
EXPECTED_PACKAGE_NAME = "net.adminrunet.h9cluster"
EXPECTED_MIN_SDK = "28"
EXPECTED_TARGET_SDK = "28"
EXPECTED_ABIS = ("arm64-v8a",)
MAX_APK_SIZE_BYTES = 250 * 1024 * 1024
PINNED_BUILD_TOOLS_VERSION = "35.0.0"
DEFAULT_GITHUB_REPOSITORY = "Arkasha18/H9-cluster"
EXPECTED_RELEASE_CERT_SHA256 = (
    "e7c84ad0463e88361afc4510a16b18774d4ccc6c0934ed866c93a4902940d610"
)


class ReleaseError(RuntimeError):
    """A release contract was not satisfied."""


@dataclasses.dataclass(frozen=True, order=True)
class Version:
    major: int
    minor: int
    patch: int

    @classmethod
    def parse(cls, value: str) -> "Version":
        match = SEMVER_PATTERN.fullmatch(value)
        if match is None:
            raise ReleaseError(
                f"version must be numeric X.Y.Z without a leading v: {value!r}"
            )
        return cls(*(int(part) for part in match.groups()))

    def bumped(self, release_type: str) -> "Version":
        if release_type == "patch":
            return Version(self.major, self.minor, self.patch + 1)
        if release_type == "minor":
            return Version(self.major, self.minor + 1, 0)
        if release_type == "major":
            return Version(self.major + 1, 0, 0)
        raise ReleaseError(f"unsupported release type: {release_type}")

    def __str__(self) -> str:
        return f"{self.major}.{self.minor}.{self.patch}"


@dataclasses.dataclass(frozen=True)
class GradleMetadata:
    version: Version
    version_code: int


@dataclasses.dataclass(frozen=True)
class PublicBaseline:
    tag: str
    version: Version
    version_code: int


def fail(message: str) -> None:
    if os.environ.get("GITHUB_ACTIONS") == "true":
        print(f"::error::{message}", file=sys.stderr)
    else:
        print(f"error: {message}", file=sys.stderr)
    raise SystemExit(1)


def run(
    command: Iterable[str],
    *,
    cwd: Path,
    check: bool = True,
) -> subprocess.CompletedProcess[str]:
    completed = subprocess.run(
        list(command),
        cwd=cwd,
        check=False,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    )
    if check and completed.returncode != 0:
        detail = completed.stderr.strip() or completed.stdout.strip()
        raise ReleaseError(
            f"command failed ({' '.join(command)}): {detail or completed.returncode}"
        )
    return completed


def git(repo: Path, *arguments: str, check: bool = True) -> str:
    return run(("git", *arguments), cwd=repo, check=check).stdout.strip()


def read_gradle_metadata_text(text: str) -> GradleMetadata:
    code_matches = list(VERSION_CODE_PATTERN.finditer(text))
    name_matches = list(VERSION_NAME_PATTERN.finditer(text))
    if len(code_matches) != 1 or len(name_matches) != 1:
        raise ReleaseError(
            "app/build.gradle.kts must contain exactly one numeric versionCode "
            "and one numeric X.Y.Z versionName"
        )
    version_code = int(code_matches[0].group(2))
    if not 1 <= version_code <= ANDROID_MAX_VERSION_CODE:
        raise ReleaseError(f"invalid Android versionCode: {version_code}")
    return GradleMetadata(
        version=Version.parse(name_matches[0].group(2)),
        version_code=version_code,
    )


def read_gradle_metadata(repo: Path) -> GradleMetadata:
    return read_gradle_metadata_text(
        (repo / "app/build.gradle.kts").read_text(encoding="utf-8")
    )


def replace_gradle_metadata(
    text: str,
    *,
    version: Version,
    version_code: int,
) -> str:
    replaced, code_count = VERSION_CODE_PATTERN.subn(
        lambda match: f"{match.group(1)}{version_code}{match.group(3)}",
        text,
    )
    replaced, name_count = VERSION_NAME_PATTERN.subn(
        lambda match: f'{match.group(1)}{version}{match.group(3)}',
        replaced,
    )
    if code_count != 1 or name_count != 1:
        raise ReleaseError("could not update unique Gradle version fields")
    return replaced


def normalized_gradle_metadata(text: str) -> str:
    normalized, code_count = VERSION_CODE_PATTERN.subn(
        lambda match: f"{match.group(1)}<VERSION_CODE>{match.group(3)}",
        text,
    )
    normalized, name_count = VERSION_NAME_PATTERN.subn(
        lambda match: f'{match.group(1)}<VERSION_NAME>{match.group(3)}',
        normalized,
    )
    if code_count != 1 or name_count != 1:
        raise ReleaseError("could not normalize Gradle version fields")
    return normalized


def changelog_sections(text: str) -> list[tuple[str, int, int]]:
    matches = list(CHANGELOG_HEADING_PATTERN.finditer(text))
    return [
        (
            match.group(1).strip(),
            match.end(),
            matches[index + 1].start() if index + 1 < len(matches) else len(text),
        )
        for index, match in enumerate(matches)
    ]


def changelog_body(text: str, heading: str) -> str:
    matches = [
        section
        for section in changelog_sections(text)
        if section[0] == heading
        or (
            SEMVER_PATTERN.fullmatch(heading) is not None
            and section[0].startswith(f"{heading} — ")
        )
    ]
    if len(matches) != 1:
        raise ReleaseError(f"CHANGELOG.md must contain exactly one '## {heading}' section")
    _, start, end = matches[0]
    return text[start:end].strip()


def parse_release_date(value: str, *, label: str) -> dt.date:
    if re.fullmatch(r"[0-9]{4}-[0-9]{2}-[0-9]{2}", value) is None:
        raise ReleaseError(f"{label} must use strict YYYY-MM-DD format: {value}")
    try:
        return dt.date.fromisoformat(value)
    except ValueError as error:
        raise ReleaseError(f"invalid {label}: {value}") from error


def changelog_release_date(text: str, version: Version) -> dt.date:
    expected_prefix = f"{version} — "
    matching = [
        (index, section)
        for index, section in enumerate(changelog_sections(text))
        if section[0].startswith(expected_prefix)
    ]
    if len(matching) != 1:
        raise ReleaseError(
            f"CHANGELOG.md must contain exactly one dated heading for {version}"
        )
    index, section = matching[0]
    date_text = section[0][len(expected_prefix):]
    release_date = parse_release_date(date_text, label="CHANGELOG.md release date")
    sections = changelog_sections(text)
    if index == 0 or sections[index - 1][0] != "Unreleased":
        raise ReleaseError(
            f"CHANGELOG.md {version} section must immediately follow Unreleased"
        )
    return release_date


def prepare_changelog(text: str, *, version: Version, release_date: dt.date) -> str:
    sections = changelog_sections(text)
    unreleased = [section for section in sections if section[0] == "Unreleased"]
    if len(unreleased) != 1:
        raise ReleaseError("CHANGELOG.md must contain exactly one '## Unreleased' section")
    heading, body_start, body_end = unreleased[0]
    del heading
    body = text[body_start:body_end].strip()
    if not body:
        raise ReleaseError("CHANGELOG.md Unreleased section is empty")
    prefix = text[:body_start].rstrip()
    suffix = text[body_end:].lstrip("\n")
    return (
        f"{prefix}\n\n"
        f"## {version} — {release_date.isoformat()}\n\n"
        f"{body}\n\n"
        f"{suffix}"
    )


def stable_tags(repo: Path) -> dict[Version, str]:
    tags: dict[Version, str] = {}
    for tag in git(repo, "tag", "--list", "v*").splitlines():
        tag = tag.strip()
        if not tag.startswith("v"):
            continue
        try:
            version = Version.parse(tag[1:])
        except ReleaseError:
            continue
        tags[version] = tag
    if not tags:
        raise ReleaseError("repository has no stable vX.Y.Z tags")
    return tags


def tag_commit(repo: Path, tag: str) -> str:
    return git(repo, "rev-list", "-n", "1", tag)


def metadata_at_tag(repo: Path, tag: str) -> GradleMetadata:
    text = git(repo, "show", f"{tag}:app/build.gradle.kts")
    return read_gradle_metadata_text(text)


def calculate_version_code(
    *,
    current_code: int,
    release_date: dt.date,
    explicit_code: int | None,
) -> int:
    if explicit_code is not None:
        candidate = explicit_code
    else:
        date_prefix = int(release_date.strftime("%Y%m%d"))
        if current_code // 100 == date_prefix:
            sequence = current_code % 100 + 1
        else:
            sequence = 1
        if sequence > 99:
            raise ReleaseError("more than 99 release versionCodes requested for one day")
        candidate = date_prefix * 100 + sequence
    if candidate <= current_code:
        raise ReleaseError(
            f"new versionCode {candidate} must be greater than current {current_code}"
        )
    if candidate > ANDROID_MAX_VERSION_CODE:
        raise ReleaseError(
            f"versionCode {candidate} exceeds Android limit {ANDROID_MAX_VERSION_CODE}"
        )
    return candidate


def validate_version_code_date(version_code: int, release_date: dt.date) -> None:
    version_code_text = str(version_code)
    if re.fullmatch(r"20[0-9]{8}", version_code_text) is None:
        raise ReleaseError("versionCode must use YYYYMMDDNN format")
    try:
        code_date = dt.datetime.strptime(version_code_text[:8], "%Y%m%d").date()
    except ValueError as error:
        raise ReleaseError("versionCode contains an invalid calendar date") from error
    if code_date != release_date:
        raise ReleaseError(
            f"versionCode date {code_date} differs from release date {release_date}"
        )
    if int(version_code_text[-2:]) == 0:
        raise ReleaseError("versionCode daily sequence must be between 01 and 99")


def require_clean_worktree(repo: Path) -> None:
    if git(repo, "status", "--porcelain"):
        raise ReleaseError(
            "worktree must be clean before applying release metadata; commit the "
            "Unreleased changelog and candidate changes first"
        )


def current_branch(repo: Path) -> str:
    branch = git(repo, "symbolic-ref", "--quiet", "--short", "HEAD", check=False)
    if not branch:
        raise ReleaseError("release preparation requires a named branch")
    return branch


def atomic_write(path: Path, text: str) -> None:
    temporary = path.with_name(f".{path.name}.release-tmp")
    temporary.write_text(text, encoding="utf-8")
    temporary.replace(path)


def discover_public_baseline(
    repo: Path,
    *,
    github_repository: str,
    aapt: str | None,
    apksigner: str | None,
    zipalign: str | None,
) -> PublicBaseline:
    if re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", github_repository) is None:
        raise ReleaseError(f"invalid GitHub repository: {github_repository}")
    release_output = run(
        (
            "gh",
            "api",
            "-H",
            "Accept: application/vnd.github+json",
            "-H",
            "X-GitHub-Api-Version: 2026-03-10",
            f"repos/{github_repository}/releases/latest",
        ),
        cwd=repo,
    ).stdout
    try:
        release = json.loads(release_output)
    except json.JSONDecodeError as error:
        raise ReleaseError("GitHub latest Release response is not valid JSON") from error
    tag = release.get("tag_name")
    if release.get("draft") is not False or release.get("prerelease") is not False:
        raise ReleaseError("latest GitHub Release is not a published stable release")
    if not isinstance(tag, str) or not tag.startswith("v"):
        raise ReleaseError("latest GitHub Release tag must use vX.Y.Z")
    version = Version.parse(tag[1:])
    asset_name = f"H9_Cluster_v{version}_adminrunet_release.apk"
    matching_assets = [
        asset
        for asset in release.get("assets", [])
        if isinstance(asset, dict)
        and asset.get("name") == asset_name
        and asset.get("state") == "uploaded"
        and isinstance(asset.get("size"), int)
        and asset["size"] > 0
    ]
    if len(matching_assets) != 1:
        raise ReleaseError(
            "latest stable release must contain exactly one expected production APK"
        )

    with tempfile.TemporaryDirectory(prefix="h9-public-release-") as directory:
        download_dir = Path(directory)
        run(
            (
                "gh",
                "release",
                "download",
                tag,
                "--repo",
                github_repository,
                "--pattern",
                asset_name,
                "--dir",
                str(download_dir),
            ),
            cwd=repo,
        )
        result = verify_apk_contract(
            argparse.Namespace(
                apk=download_dir / asset_name,
                version=str(version),
                version_code=None,
                aapt=aapt,
                apksigner=apksigner,
                zipalign=zipalign,
                expect_unsigned=False,
                expected_cert_sha256=EXPECTED_RELEASE_CERT_SHA256,
                enforce_release_name=True,
            )
        )
    return PublicBaseline(
        tag=tag,
        version=version,
        version_code=int(result["version_code"]),
    )


def command_prepare(args: argparse.Namespace) -> None:
    repo = args.repo.resolve()
    current = read_gradle_metadata(repo)
    tags = stable_tags(repo)
    baseline = discover_public_baseline(
        repo,
        github_repository=args.github_repository,
        aapt=args.aapt,
        apksigner=args.apksigner,
        zipalign=args.zipalign,
    )
    if tags.get(baseline.version) != baseline.tag:
        raise ReleaseError(
            f"published baseline tag is missing locally: {baseline.tag}; fetch tags first"
        )
    baseline_source = metadata_at_tag(repo, baseline.tag)
    if baseline_source.version != baseline.version:
        raise ReleaseError(
            f"{baseline.tag} source versionName is {baseline_source.version}"
        )
    if baseline_source.version_code != baseline.version_code:
        raise ReleaseError(
            f"public APK versionCode {baseline.version_code} differs from "
            f"{baseline.tag} source {baseline_source.version_code}"
        )
    current_tag = tags.get(current.version)
    if current_tag is None:
        raise ReleaseError(
            f"current source version {current.version} has no matching stable tag"
        )
    current_tag_metadata = metadata_at_tag(repo, current_tag)
    if current != current_tag_metadata:
        raise ReleaseError(
            "current source version metadata differs from its stable tag before preparation"
        )
    if current.version > baseline.version:
        raise ReleaseError(
            f"current source version {current.version} is ahead of published {baseline.version}"
        )
    if args.release_type == "patch" and current != baseline_source:
        raise ReleaseError(
            f"patch source metadata must equal published baseline {baseline.tag}"
        )
    current_tag_ancestor = run(
        ("git", "merge-base", "--is-ancestor", current_tag, "HEAD"),
        cwd=repo,
        check=False,
    )
    if current_tag_ancestor.returncode != 0:
        raise ReleaseError(f"current metadata tag {current_tag} is not an ancestor of HEAD")

    next_version = baseline.version.bumped(args.release_type)
    release_date = (
        parse_release_date(args.date, label="release date")
        if args.date
        else dt.datetime.now(ZoneInfo("Europe/Moscow")).date()
    )
    version_code = calculate_version_code(
        current_code=baseline.version_code,
        release_date=release_date,
        explicit_code=args.version_code,
    )
    validate_version_code_date(version_code, release_date)

    changelog_path = repo / "CHANGELOG.md"
    gradle_path = repo / "app/build.gradle.kts"
    changelog = prepare_changelog(
        changelog_path.read_text(encoding="utf-8"),
        version=next_version,
        release_date=release_date,
    )
    gradle = replace_gradle_metadata(
        gradle_path.read_text(encoding="utf-8"),
        version=next_version,
        version_code=version_code,
    )

    expected_branch = (
        f"hotfix/{next_version}"
        if args.release_type == "patch"
        else f"release/{next_version}"
    )
    result = {
        "release_type": args.release_type,
        "previous_tag": baseline.tag,
        "previous_version_code": baseline.version_code,
        "version": str(next_version),
        "version_code": version_code,
        "release_date": release_date.isoformat(),
        "expected_preparation_branch": expected_branch,
        "applied": bool(args.apply),
    }

    if args.apply:
        require_clean_worktree(repo)
        branch = current_branch(repo)
        if branch != expected_branch:
            raise ReleaseError(
                f"prepare {args.release_type} on branch {expected_branch!r}, not {branch!r}"
            )
        atomic_write(gradle_path, gradle)
        atomic_write(changelog_path, changelog)

    print(json.dumps(result, ensure_ascii=False, indent=2))


def verify_hotfix_control_plane(
    repo: Path,
    previous_tag: str,
    main_ref: str,
) -> None:
    candidate_main_base = git(repo, "merge-base", "HEAD", main_ref)
    previous_main_base = git(repo, "merge-base", previous_tag, main_ref)
    if candidate_main_base != previous_main_base:
        raise ReleaseError(
            f"hotfix main fork point {candidate_main_base} must stay at "
            f"{previous_tag} main fork point {previous_main_base}"
        )
    merge_commits = git(repo, "rev-list", "--merges", f"{previous_tag}..HEAD")
    if merge_commits:
        raise ReleaseError("hotfix history must not contain merge commits from future main")

    changed = set(
        line
        for line in git(repo, "diff", "--name-only", f"{previous_tag}..HEAD").splitlines()
        if line
    )
    forbidden_exact = {
        "Dockerfile",
        "build.gradle.kts",
        "settings.gradle.kts",
        "gradle.properties",
        "gradlew",
        "gradlew.bat",
        "requirements-tools.txt",
        "tools/verify_demo_apk_secrets.sh",
    }
    forbidden_prefixes = (".github/", "gradle/", "tools/release/")
    forbidden = sorted(
        path
        for path in changed
        if path in forbidden_exact or path.startswith(forbidden_prefixes)
    )
    if forbidden:
        raise ReleaseError(
            "hotfix changes release/build control-plane files: " + ", ".join(forbidden)
        )

    if "app/build.gradle.kts" in changed:
        previous = git(repo, "show", f"{previous_tag}:app/build.gradle.kts")
        current = (repo / "app/build.gradle.kts").read_text(encoding="utf-8")
        if normalized_gradle_metadata(previous) != normalized_gradle_metadata(current):
            raise ReleaseError(
                "hotfix may change only versionName/versionCode in app/build.gradle.kts"
            )


def command_verify(args: argparse.Namespace) -> None:
    repo = args.repo.resolve()
    version = Version.parse(args.version)
    if not FULL_SHA_PATTERN.fullmatch(args.commit):
        raise ReleaseError("commit must be a full lowercase 40-character SHA")
    head = git(repo, "rev-parse", "HEAD")
    if head != args.commit:
        raise ReleaseError(f"checked out HEAD {head} does not match requested {args.commit}")

    expected_source = "main" if args.release_type != "patch" else f"hotfix/{version}"
    if args.source_ref != expected_source:
        raise ReleaseError(
            f"{args.release_type} release must use source ref {expected_source!r}"
        )

    if not args.previous_tag.startswith("v"):
        raise ReleaseError("previous published tag must have vX.Y.Z form")
    previous_version = Version.parse(args.previous_tag[1:])
    previous_tag = args.previous_tag
    tags = stable_tags(repo)
    if tags.get(previous_version) != previous_tag:
        raise ReleaseError(f"previous published tag is missing locally: {previous_tag}")
    expected_version = previous_version.bumped(args.release_type)
    if version != expected_version:
        raise ReleaseError(
            f"{args.release_type} after {previous_version} must be {expected_version}, not {version}"
        )

    existing_tag = tags.get(version)
    if existing_tag is not None and tag_commit(repo, existing_tag) != args.commit:
        raise ReleaseError(f"{existing_tag} already points to another commit")

    if args.release_type == "patch":
        ancestor = run(
            ("git", "merge-base", "--is-ancestor", previous_tag, args.commit),
            cwd=repo,
            check=False,
        )
        if ancestor.returncode != 0:
            raise ReleaseError(f"{previous_tag} is not an ancestor of hotfix commit")
    else:
        shared_history = run(
            ("git", "merge-base", previous_tag, args.commit),
            cwd=repo,
            check=False,
        )
        if shared_history.returncode != 0 or not shared_history.stdout.strip():
            raise ReleaseError(
                f"{previous_tag} and main release candidate have no shared history"
            )

    metadata = read_gradle_metadata(repo)
    if metadata.version != version:
        raise ReleaseError(
            f"Gradle versionName {metadata.version} does not match requested {version}"
        )
    previous_metadata = metadata_at_tag(repo, previous_tag)
    if previous_metadata.version != previous_version:
        raise ReleaseError(
            f"{previous_tag} source versionName {previous_metadata.version} "
            f"does not match tag {previous_version}"
        )
    if previous_metadata.version_code != args.previous_version_code:
        raise ReleaseError(
            f"public APK versionCode {args.previous_version_code} differs from "
            f"{previous_tag} source {previous_metadata.version_code}"
        )
    if metadata.version_code <= previous_metadata.version_code:
        raise ReleaseError(
            f"versionCode {metadata.version_code} must be greater than previous "
            f"{previous_metadata.version_code}"
        )

    changelog = (repo / "CHANGELOG.md").read_text(encoding="utf-8")
    if changelog_body(changelog, "Unreleased"):
        raise ReleaseError("CHANGELOG.md Unreleased must be empty after release preparation")
    notes = changelog_body(changelog, str(version))
    if not notes:
        raise ReleaseError(f"CHANGELOG.md section {version} is empty")
    release_date = changelog_release_date(changelog, version)
    validate_version_code_date(metadata.version_code, release_date)

    diff_check = run(
        ("git", "diff", "--check", f"{previous_tag}..{args.commit}"),
        cwd=repo,
        check=False,
    )
    if diff_check.returncode != 0:
        raise ReleaseError(f"git diff --check failed: {diff_check.stdout.strip()}")

    if args.release_type == "patch":
        verify_hotfix_control_plane(repo, previous_tag, args.main_ref)

    asset_name = f"H9_Cluster_v{version}_adminrunet_release.apk"
    result = {
        "asset_name": asset_name,
        "commit": args.commit,
        "previous_tag": previous_tag,
        "previous_version_code": args.previous_version_code,
        "release_type": args.release_type,
        "source_ref": args.source_ref,
        "tag": f"v{version}",
        "version": str(version),
        "version_code": metadata.version_code,
    }
    args.output_json.parent.mkdir(parents=True, exist_ok=True)
    args.output_json.write_text(
        json.dumps(result, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    args.output_notes.parent.mkdir(parents=True, exist_ok=True)
    args.output_notes.write_text(notes.rstrip() + "\n", encoding="utf-8")

    print(json.dumps(result, ensure_ascii=False, indent=2))
    print("\nChanged files since previous release:")
    print(git(repo, "diff", "--name-status", f"{previous_tag}..{args.commit}"))


def resolve_tool(value: str | None, name: str) -> str:
    candidates: list[Path | str | None] = [value, shutil.which(name)]
    for environment_name in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        sdk_root = os.environ.get(environment_name)
        if sdk_root:
            candidates.append(
                Path(sdk_root)
                / "build-tools"
                / PINNED_BUILD_TOOLS_VERSION
                / name
            )
    for candidate in candidates:
        if candidate and Path(candidate).is_file() and os.access(candidate, os.X_OK):
            return str(candidate)
    raise ReleaseError(
        f"required tool is not available: {name} from Android Build Tools "
        f"{PINNED_BUILD_TOOLS_VERSION}"
    )


def verify_apk_contract(args: argparse.Namespace) -> dict[str, object]:
    apk = args.apk.resolve()
    if not apk.is_file():
        raise ReleaseError(f"APK does not exist: {apk}")
    if apk.stat().st_size > MAX_APK_SIZE_BYTES:
        raise ReleaseError(f"APK exceeds {MAX_APK_SIZE_BYTES} bytes")

    aapt = resolve_tool(args.aapt, "aapt")
    apksigner = resolve_tool(args.apksigner, "apksigner")
    badging = run((aapt, "dump", "badging", str(apk)), cwd=apk.parent).stdout
    package_match = re.search(
        r"^package: name='([^']+)' versionCode='([0-9]+)' versionName='([^']+)'",
        badging,
        re.MULTILINE,
    )
    if package_match is None:
        raise ReleaseError("aapt did not return APK package metadata")
    package_name, version_code, version_name = package_match.groups()
    if package_name != EXPECTED_PACKAGE_NAME:
        raise ReleaseError(f"unexpected package name: {package_name}")
    if version_name != args.version:
        raise ReleaseError(f"APK versionName {version_name} != {args.version}")
    if args.version_code is not None and int(version_code) != args.version_code:
        raise ReleaseError(f"APK versionCode {version_code} != {args.version_code}")
    if f"sdkVersion:'{EXPECTED_MIN_SDK}'" not in badging:
        raise ReleaseError(f"APK minSdk is not {EXPECTED_MIN_SDK}")
    if f"targetSdkVersion:'{EXPECTED_TARGET_SDK}'" not in badging:
        raise ReleaseError(f"APK targetSdk is not {EXPECTED_TARGET_SDK}")
    if "application-debuggable" in badging:
        raise ReleaseError("production APK is debuggable")
    native_match = re.search(r"^native-code:\s+(.+)$", badging, re.MULTILINE)
    if native_match is None:
        raise ReleaseError("APK does not declare native ABIs")
    native_abis = tuple(re.findall(r"'([^']+)'", native_match.group(1)))
    if native_abis != EXPECTED_ABIS:
        raise ReleaseError(f"unexpected native ABIs: {native_abis}")

    if args.expect_unsigned:
        signature = run((apksigner, "verify", str(apk)), cwd=apk.parent, check=False)
        if signature.returncode == 0:
            raise ReleaseError("unsigned build unexpectedly already has a valid signature")
        cert_digest = None
    else:
        if not args.expected_cert_sha256:
            raise ReleaseError("signed APK verification requires expected certificate SHA-256")
        zipalign = resolve_tool(args.zipalign, "zipalign")
        run((zipalign, "-c", "-P", "16", "4", str(apk)), cwd=apk.parent)
        signature = run(
            (apksigner, "verify", "--Werr", "--verbose", "--print-certs", str(apk)),
            cwd=apk.parent,
        ).stdout
        signer_count_match = re.search(
            r"^Number of signers:\s*([0-9]+)\s*$",
            signature,
            re.MULTILINE,
        )
        if signer_count_match is None or int(signer_count_match.group(1)) != 1:
            raise ReleaseError("APK must have exactly one signer")
        cert_match = re.search(
            r"Signer #1 certificate SHA-256 digest:\s*([0-9a-fA-F]+)",
            signature,
        )
        if cert_match is None:
            raise ReleaseError("apksigner did not report signer certificate SHA-256")
        cert_digest = cert_match.group(1).lower()
        expected = args.expected_cert_sha256.replace(":", "").lower()
        if cert_digest != expected:
            raise ReleaseError("APK signer certificate does not match the pinned release certificate")
        expected_name = f"H9_Cluster_v{args.version}_adminrunet_release.apk"
        if args.enforce_release_name and apk.name != expected_name:
            raise ReleaseError(f"release APK must be named {expected_name}")

    result = {
        "apk": apk.name,
        "certificate_sha256": cert_digest,
        "package_name": package_name,
        "size_bytes": apk.stat().st_size,
        "version": version_name,
        "version_code": int(version_code),
    }
    return result


def command_verify_apk(args: argparse.Namespace) -> None:
    result = verify_apk_contract(args)
    if args.output_json is not None:
        args.output_json.parent.mkdir(parents=True, exist_ok=True)
        args.output_json.write_text(
            json.dumps(result, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
    print(json.dumps(result, ensure_ascii=False, indent=2))


def command_verify_tbox(args: argparse.Namespace) -> None:
    expected_value = os.environ.get("H9_TBOX_PASSWORD", "")
    if not expected_value:
        raise ReleaseError("H9_TBOX_PASSWORD is required for production verification")
    source = args.build_config.read_text(encoding="utf-8")

    def build_config_value(field: str) -> str:
        match = re.search(
            rf'public static final String {field} = "([A-Za-z0-9+/=]+)";',
            source,
        )
        if match is None:
            raise ReleaseError(f"generated BuildConfig is missing non-empty {field}")
        return match.group(1)

    mask_text = build_config_value("TBOX_SECRET_MASK")
    data_text = build_config_value("TBOX_SECRET_DATA")
    try:
        mask = base64.b64decode(mask_text, validate=True)
        data = base64.b64decode(data_text, validate=True)
    except (ValueError, binascii.Error) as error:
        raise ReleaseError("generated TBOX material is not valid Base64") from error
    expected = expected_value.encode("utf-8")
    if len(mask) != len(expected) or len(data) != len(expected):
        raise ReleaseError("generated TBOX material length differs from protected input")
    reconstructed = bytes(left ^ right for left, right in zip(mask, data))
    if not hmac.compare_digest(reconstructed, expected):
        raise ReleaseError("generated TBOX material differs from protected input")

    with zipfile.ZipFile(args.apk) as archive:
        dex_names = [name for name in archive.namelist() if re.fullmatch(r"classes[0-9]*\.dex", name)]
        if not dex_names:
            raise ReleaseError("production APK contains no DEX files")
        dex_payloads = [archive.read(name) for name in dex_names]
        if any(expected in payload for payload in dex_payloads):
            raise ReleaseError("plain TBOX password is present in production DEX")
        if not any(mask_text.encode("ascii") in payload for payload in dex_payloads):
            raise ReleaseError("generated TBOX mask is absent from production DEX")
        if not any(data_text.encode("ascii") in payload for payload in dex_payloads):
            raise ReleaseError("generated TBOX data is absent from production DEX")
    print("Production TBOX secret contract verified")


def parser() -> argparse.ArgumentParser:
    root = argparse.ArgumentParser(description=__doc__)
    subparsers = root.add_subparsers(dest="command", required=True)

    prepare = subparsers.add_parser("prepare", help="calculate or apply a release bump")
    prepare.add_argument("--repo", type=Path, default=Path.cwd())
    prepare.add_argument(
        "--release-type",
        choices=("patch", "minor", "major"),
        required=True,
    )
    prepare.add_argument("--date", help="release date in YYYY-MM-DD; defaults to Moscow date")
    prepare.add_argument("--version-code", type=int)
    prepare.add_argument("--github-repository", default=DEFAULT_GITHUB_REPOSITORY)
    prepare.add_argument("--aapt")
    prepare.add_argument("--apksigner")
    prepare.add_argument("--zipalign")
    prepare.add_argument("--apply", action="store_true")
    prepare.set_defaults(handler=command_prepare)

    verify = subparsers.add_parser("verify", help="verify source release metadata")
    verify.add_argument("--repo", type=Path, required=True)
    verify.add_argument("--version", required=True)
    verify.add_argument(
        "--release-type",
        choices=("patch", "minor", "major"),
        required=True,
    )
    verify.add_argument("--commit", required=True)
    verify.add_argument("--source-ref", required=True)
    verify.add_argument("--main-ref", default="refs/remotes/origin/main")
    verify.add_argument("--previous-tag", required=True)
    verify.add_argument("--previous-version-code", type=int, required=True)
    verify.add_argument("--output-json", type=Path, required=True)
    verify.add_argument("--output-notes", type=Path, required=True)
    verify.set_defaults(handler=command_verify)

    verify_apk = subparsers.add_parser("verify-apk", help="verify an unsigned or signed APK")
    verify_apk.add_argument("--apk", type=Path, required=True)
    verify_apk.add_argument("--version", required=True)
    verify_apk.add_argument("--version-code", type=int)
    verify_apk.add_argument("--aapt")
    verify_apk.add_argument("--apksigner")
    verify_apk.add_argument("--zipalign")
    signature_group = verify_apk.add_mutually_exclusive_group(required=True)
    signature_group.add_argument("--expect-unsigned", action="store_true")
    signature_group.add_argument("--expected-cert-sha256")
    verify_apk.add_argument("--enforce-release-name", action="store_true")
    verify_apk.add_argument("--output-json", type=Path)
    verify_apk.set_defaults(handler=command_verify_apk)

    verify_tbox = subparsers.add_parser(
        "verify-tbox",
        help="verify protected TBOX material in generated BuildConfig and APK",
    )
    verify_tbox.add_argument("--build-config", type=Path, required=True)
    verify_tbox.add_argument("--apk", type=Path, required=True)
    verify_tbox.set_defaults(handler=command_verify_tbox)
    return root


def main() -> None:
    args = parser().parse_args()
    try:
        args.handler(args)
    except (ReleaseError, OSError, ValueError) as error:
        fail(str(error))


if __name__ == "__main__":
    main()
