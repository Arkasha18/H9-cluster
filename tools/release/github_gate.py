#!/usr/bin/env python3
"""Fail-closed GitHub PR and CI gate for an H9 production release."""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
from typing import Any
from urllib.parse import quote, urlencode


API_VERSION = "2026-03-10"
WORKFLOW_PATH = ".github/workflows/android-ci.yml"
WORKFLOW_JOB_NAME = "Build and lint"
PROVENANCE_ARTIFACT_PREFIX = "h9-ci-provenance"
FULL_SHA_PATTERN = re.compile(r"^[0-9a-f]{40}$")
REPOSITORY_PATTERN = re.compile(r"^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")
HOTFIX_REF_PATTERN = re.compile(
    r"^hotfix/(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$"
)
RUN_TIMESTAMP_PATTERN = re.compile(r"^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$")


class GateError(RuntimeError):
    """GitHub release eligibility changed or could not be proven."""


def gh_json(*arguments: str) -> Any:
    completed = subprocess.run(
        ("gh", *arguments),
        check=False,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    )
    if completed.returncode != 0:
        detail = completed.stderr.strip() or completed.stdout.strip()
        raise GateError(f"gh command failed: {detail or completed.returncode}")
    try:
        return json.loads(completed.stdout)
    except json.JSONDecodeError as error:
        raise GateError("gh returned invalid JSON") from error


def rest_api(endpoint: str, *, paginate: bool = False) -> Any:
    arguments = [
        "api",
        "-H",
        "Accept: application/vnd.github+json",
        "-H",
        f"X-GitHub-Api-Version: {API_VERSION}",
    ]
    if paginate:
        arguments.extend(("--paginate", "--slurp"))
    arguments.append(endpoint)
    return gh_json(*arguments)


def flatten_list_pages(value: Any) -> list[dict[str, Any]]:
    if not isinstance(value, list):
        raise GateError("paginated GitHub list response has an unexpected shape")
    result: list[dict[str, Any]] = []
    for page in value:
        if not isinstance(page, list):
            raise GateError("paginated GitHub list page has an unexpected shape")
        if not all(isinstance(item, dict) for item in page):
            raise GateError("paginated GitHub list contains a non-object item")
        result.extend(page)
    return result


def flatten_object_pages(value: Any, key: str) -> list[dict[str, Any]]:
    if not isinstance(value, list):
        raise GateError("paginated GitHub object response has an unexpected shape")
    result: list[dict[str, Any]] = []
    for page in value:
        if not isinstance(page, dict) or not isinstance(page.get(key), list):
            raise GateError(f"paginated GitHub response is missing {key}")
        items = page[key]
        if not all(isinstance(item, dict) for item in items):
            raise GateError(f"paginated GitHub {key} contains a non-object item")
        result.extend(items)
    return result


def nested(value: dict[str, Any], *keys: str) -> Any:
    current: Any = value
    for key in keys:
        if not isinstance(current, dict):
            return None
        current = current.get(key)
    return current


def ref_sha(repository: str, branch: str) -> str:
    response = rest_api(
        f"repos/{repository}/git/ref/heads/{quote(branch, safe='')}"
    )
    sha = nested(response, "object", "sha") if isinstance(response, dict) else None
    if not isinstance(sha, str) or FULL_SHA_PATTERN.fullmatch(sha) is None:
        raise GateError(f"GitHub returned an invalid SHA for branch {branch}")
    return sha


def associated_pull_request(
    repository: str,
    *,
    commit: str,
    release_type: str,
    source_ref: str,
) -> dict[str, Any]:
    pages = rest_api(
        f"repos/{repository}/commits/{commit}/pulls?per_page=100",
        paginate=True,
    )
    pull_requests = flatten_list_pages(pages)
    if release_type == "patch":
        candidates = [
            pull_request
            for pull_request in pull_requests
            if pull_request.get("state") == "open"
            and pull_request.get("draft") is False
            and nested(pull_request, "head", "sha") == commit
            and nested(pull_request, "head", "ref") == source_ref
            and nested(pull_request, "head", "repo", "full_name") == repository
            and nested(pull_request, "base", "ref") == "main"
            and nested(pull_request, "base", "repo", "full_name") == repository
        ]
    else:
        candidates = [
            pull_request
            for pull_request in pull_requests
            if pull_request.get("state") == "closed"
            and pull_request.get("merged_at") is not None
            and nested(pull_request, "base", "ref") == "main"
            and nested(pull_request, "base", "repo", "full_name") == repository
        ]
    if len(candidates) != 1:
        raise GateError(
            f"candidate must have exactly one qualifying PR, found {len(candidates)}"
        )
    return candidates[0]


def verify_pull_request_snapshot(
    repository: str,
    *,
    pull_request: dict[str, Any],
    commit: str,
    release_type: str,
    source_ref: str,
) -> tuple[int, str | None]:
    number = pull_request.get("number")
    if not isinstance(number, int):
        raise GateError("qualifying PR has no numeric number")

    if release_type == "patch":
        detail = rest_api(f"repos/{repository}/pulls/{number}")
        if not isinstance(detail, dict):
            raise GateError(f"PR #{number} response has an unexpected shape")
        if detail.get("mergeable") is not True:
            raise GateError(f"hotfix PR #{number} is not currently mergeable into main")
        if detail.get("state") != "open" or detail.get("draft") is not False:
            raise GateError(f"hotfix PR #{number} is no longer open and ready")
        if nested(detail, "head", "sha") != commit:
            raise GateError(f"hotfix PR #{number} head changed")

    owner, name = repository.split("/", 1)
    query = (
        "query($owner:String!,$repository:String!,$number:Int!,$cursor:String){"
        "repository(owner:$owner,name:$repository){pullRequest(number:$number){"
        "state isDraft reviewDecision headRefOid headRefName baseRefName "
        "mergeCommit{oid} reviewThreads(first:100,after:$cursor){"
        "nodes{isResolved} pageInfo{hasNextPage endCursor}}}}}"
    )
    cursor: str | None = None
    review_decision: str | None = None
    for _ in range(100):
        arguments = [
            "api",
            "graphql",
            "-f",
            f"query={query}",
            "-F",
            f"owner={owner}",
            "-F",
            f"repository={name}",
            "-F",
            f"number={number}",
        ]
        if cursor is not None:
            arguments.extend(("-F", f"cursor={cursor}"))
        response = gh_json(*arguments)
        snapshot = nested(response, "data", "repository", "pullRequest")
        if not isinstance(snapshot, dict):
            raise GateError(f"GraphQL did not return PR #{number}")
        if snapshot.get("isDraft") is not False or snapshot.get("baseRefName") != "main":
            raise GateError(f"PR #{number} changed during release verification")
        if release_type == "patch":
            if snapshot.get("state") != "OPEN":
                raise GateError(f"hotfix PR #{number} is no longer open")
            if snapshot.get("headRefOid") != commit:
                raise GateError(f"hotfix PR #{number} head changed")
            if snapshot.get("headRefName") != source_ref:
                raise GateError(f"hotfix PR #{number} source branch changed")
        else:
            if snapshot.get("state") != "MERGED":
                raise GateError(f"release PR #{number} is not merged")
            if nested(snapshot, "mergeCommit", "oid") != commit:
                raise GateError(f"release PR #{number} merge commit differs from candidate")
        review_decision_value = snapshot.get("reviewDecision")
        if review_decision_value is not None and not isinstance(
            review_decision_value, str
        ):
            raise GateError(f"GraphQL returned an invalid review decision for PR #{number}")
        review_decision = review_decision_value
        if review_decision == "CHANGES_REQUESTED":
            raise GateError(f"PR #{number} has an active changes-requested review")
        threads = snapshot.get("reviewThreads")
        if not isinstance(threads, dict) or not isinstance(threads.get("nodes"), list):
            raise GateError(f"GraphQL review threads are missing for PR #{number}")
        if not all(
            isinstance(thread, dict)
            and isinstance(thread.get("isResolved"), bool)
            for thread in threads["nodes"]
        ):
            raise GateError(f"GraphQL returned an invalid review thread for PR #{number}")
        if any(thread["isResolved"] is False for thread in threads["nodes"]):
            raise GateError(f"PR #{number} has unresolved review threads")
        page_info = threads.get("pageInfo")
        if not isinstance(page_info, dict):
            raise GateError(f"GraphQL pagination is missing for PR #{number}")
        has_next_page = page_info.get("hasNextPage")
        if not isinstance(has_next_page, bool):
            raise GateError(f"GraphQL returned invalid pagination for PR #{number}")
        if has_next_page is False:
            return number, review_decision
        next_cursor = page_info.get("endCursor")
        if not isinstance(next_cursor, str) or not next_cursor:
            raise GateError(f"GraphQL returned an invalid cursor for PR #{number}")
        cursor = next_cursor
    raise GateError(f"PR #{number} has more than 10,000 review threads")


def verify_android_ci(
    repository: str,
    *,
    commit: str,
    release_type: str,
    source_ref: str,
    main_sha: str,
    pull_request_number: int,
) -> int:
    event = "pull_request" if release_type == "patch" else "push"
    query = urlencode(
        {
            "event": event,
            "branch": source_ref,
            "per_page": "100",
        }
    )
    pages = rest_api(
        f"repos/{repository}/actions/workflows/android-ci.yml/runs?{query}",
        paginate=True,
    )
    workflow_runs = flatten_object_pages(pages, "workflow_runs")

    def matches_candidate(run: dict[str, Any]) -> bool:
        if run.get("event") != event or run.get("head_sha") != commit:
            return False
        if run.get("head_branch") != source_ref:
            return False
        path = run.get("path")
        if not isinstance(path, str) or not path.startswith(WORKFLOW_PATH):
            return False
        return True

    candidates = [run for run in workflow_runs if matches_candidate(run)]
    if not candidates:
        raise GateError(f"candidate has no {event} run from {WORKFLOW_PATH}")
    if any(run.get("status") != "completed" for run in candidates):
        raise GateError(f"candidate has an unfinished {event} workflow run")
    for run in candidates:
        started_at = run.get("run_started_at")
        if not isinstance(started_at, str) or RUN_TIMESTAMP_PATTERN.fullmatch(
            started_at
        ) is None:
            raise GateError("candidate workflow run has an invalid start time")
        if not isinstance(run.get("id"), int):
            raise GateError("candidate workflow run has an invalid id")
    latest_started_at = max(run["run_started_at"] for run in candidates)
    latest_batch = [
        run for run in candidates if run["run_started_at"] == latest_started_at
    ]
    if any(run.get("conclusion") != "success" for run in latest_batch):
        raise GateError(f"latest candidate {event} workflow run is not successful")
    latest = max(latest_batch, key=lambda run: run["id"])

    if release_type == "patch":
        pull_requests = latest.get("pull_requests")
        if not isinstance(pull_requests, list):
            raise GateError("latest hotfix CI run has no PR association")
        association_matches = any(
            isinstance(item, dict)
            and item.get("number") == pull_request_number
            and nested(item, "head", "sha") == commit
            and nested(item, "head", "ref") == source_ref
            and nested(item, "base", "sha") == main_sha
            and nested(item, "base", "ref") == "main"
            for item in pull_requests
        )
        if not association_matches:
            raise GateError(
                "latest hotfix CI run is not tied to the exact PR head and main SHA"
            )
    run_id = latest.get("id")
    attempt = latest.get("run_attempt")
    if not isinstance(run_id, int) or not isinstance(attempt, int):
        raise GateError("latest workflow run has invalid identity")

    job_pages = rest_api(
        f"repos/{repository}/actions/runs/{run_id}/attempts/{attempt}/jobs?per_page=100",
        paginate=True,
    )
    jobs = flatten_object_pages(job_pages, "jobs")
    matching_jobs = [
        job
        for job in jobs
        if job.get("name") == WORKFLOW_JOB_NAME and job.get("head_sha") == commit
    ]
    if len(matching_jobs) != 1:
        raise GateError(
            f"latest workflow run must contain one exact {WORKFLOW_JOB_NAME} job"
        )
    job = matching_jobs[0]
    if job.get("status") != "completed" or job.get("conclusion") != "success":
        raise GateError(f"{WORKFLOW_JOB_NAME} is not successful for the candidate")

    if release_type == "patch":
        artifact_pages = rest_api(
            f"repos/{repository}/actions/runs/{run_id}/artifacts?per_page=100",
            paginate=True,
        )
        artifacts = flatten_object_pages(artifact_pages, "artifacts")
        expected_name = (
            f"{PROVENANCE_ARTIFACT_PREFIX}-{commit}-{main_sha}-attempt-{attempt}"
        )
        provenance = [
            artifact
            for artifact in artifacts
            if artifact.get("name") == expected_name
        ]
        if len(provenance) != 1:
            raise GateError(
                "successful hotfix CI has no unique immutable artifact for the "
                "current main SHA"
            )
        artifact = provenance[0]
        if artifact.get("expired") is not False:
            raise GateError("hotfix CI provenance artifact is expired")
        if not isinstance(artifact.get("id"), int) or not isinstance(
            artifact.get("size_in_bytes"), int
        ) or artifact["size_in_bytes"] <= 0:
            raise GateError("hotfix CI provenance artifact metadata is invalid")
    return run_id


def verify_gate(args: argparse.Namespace) -> dict[str, Any]:
    if REPOSITORY_PATTERN.fullmatch(args.repository) is None:
        raise GateError(f"invalid repository: {args.repository}")
    if FULL_SHA_PATTERN.fullmatch(args.commit) is None:
        raise GateError("commit must be a full lowercase 40-character SHA")
    if FULL_SHA_PATTERN.fullmatch(args.control_commit) is None:
        raise GateError("control commit must be a full lowercase 40-character SHA")
    expected_source = "main" if args.release_type != "patch" else args.source_ref
    if args.release_type == "patch":
        if HOTFIX_REF_PATTERN.fullmatch(args.source_ref) is None:
            raise GateError("patch source ref must be hotfix/X.Y.Z")
    elif args.source_ref != "main" or expected_source != "main":
        raise GateError("minor and major releases must use source ref main")

    main_sha = ref_sha(args.repository, "main")
    if main_sha != args.control_commit:
        raise GateError("release control plane is not the current main commit")
    source_sha = main_sha if args.source_ref == "main" else ref_sha(
        args.repository, args.source_ref
    )
    if source_sha != args.commit:
        raise GateError(f"{args.source_ref} no longer points to the candidate commit")
    pull_request = associated_pull_request(
        args.repository,
        commit=args.commit,
        release_type=args.release_type,
        source_ref=args.source_ref,
    )
    pull_request_number, review_decision = verify_pull_request_snapshot(
        args.repository,
        pull_request=pull_request,
        commit=args.commit,
        release_type=args.release_type,
        source_ref=args.source_ref,
    )
    workflow_run_id = verify_android_ci(
        args.repository,
        commit=args.commit,
        release_type=args.release_type,
        source_ref=args.source_ref,
        main_sha=main_sha,
        pull_request_number=pull_request_number,
    )
    final_pull_request = associated_pull_request(
        args.repository,
        commit=args.commit,
        release_type=args.release_type,
        source_ref=args.source_ref,
    )
    final_pull_request_number, review_decision = verify_pull_request_snapshot(
        args.repository,
        pull_request=final_pull_request,
        commit=args.commit,
        release_type=args.release_type,
        source_ref=args.source_ref,
    )
    if final_pull_request_number != pull_request_number:
        raise GateError("qualifying PR changed during GitHub gate verification")
    final_source_sha = ref_sha(args.repository, args.source_ref)
    final_main_sha = (
        final_source_sha
        if args.source_ref == "main"
        else ref_sha(args.repository, "main")
    )
    if final_main_sha != main_sha or final_source_sha != source_sha:
        raise GateError("main or release source moved during GitHub gate verification")
    return {
        "commit": args.commit,
        "control_commit": args.control_commit,
        "pull_request": pull_request_number,
        "review_decision": review_decision,
        "source_ref": args.source_ref,
        "workflow_run": workflow_run_id,
    }


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser(description=__doc__)
    result.add_argument("--repository", required=True)
    result.add_argument("--commit", required=True)
    result.add_argument("--control-commit", required=True)
    result.add_argument("--release-type", choices=("patch", "minor", "major"), required=True)
    result.add_argument("--source-ref", required=True)
    return result


def main() -> None:
    try:
        result = verify_gate(parser().parse_args())
    except (GateError, OSError, ValueError) as error:
        if os.environ.get("GITHUB_ACTIONS") == "true":
            print(f"::error::{error}", file=sys.stderr)
        else:
            print(f"error: {error}", file=sys.stderr)
        raise SystemExit(1) from error
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
