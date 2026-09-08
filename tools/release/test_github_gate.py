from unittest import mock
import unittest

from tools.release.github_gate import (
    GateError,
    associated_pull_request,
    verify_android_ci,
    verify_pull_request_snapshot,
)


REPOSITORY = "Arkasha18/H9-cluster"
COMMIT = "a" * 40
MAIN_SHA = "b" * 40


class PullRequestSelectionTest(unittest.TestCase):
    def test_merged_pr_uses_graphql_for_exact_merge_commit(self):
        response = [[{
            "number": 72,
            "state": "closed",
            "draft": False,
            "merged_at": "2026-08-31T10:00:00Z",
            "merge_commit_sha": None,
            "base": {"ref": "main", "repo": {"full_name": REPOSITORY}},
        }]]
        with mock.patch(
            "tools.release.github_gate.rest_api",
            return_value=response,
        ):
            pull_request = associated_pull_request(
                REPOSITORY,
                commit=COMMIT,
                release_type="minor",
                source_ref="main",
            )
        self.assertEqual(72, pull_request["number"])

    def test_hotfix_pr_must_match_exact_head_and_repository(self):
        response = [[{
            "number": 73,
            "state": "open",
            "draft": False,
            "head": {
                "sha": COMMIT,
                "ref": "hotfix/9.5.3",
                "repo": {"full_name": REPOSITORY},
            },
            "base": {"ref": "main", "repo": {"full_name": REPOSITORY}},
        }]]
        with mock.patch(
            "tools.release.github_gate.rest_api",
            return_value=response,
        ):
            pull_request = associated_pull_request(
                REPOSITORY,
                commit=COMMIT,
                release_type="patch",
                source_ref="hotfix/9.5.3",
            )
        self.assertEqual(73, pull_request["number"])


class WorkflowRunSelectionTest(unittest.TestCase):
    @staticmethod
    def workflow_run(
        run_id,
        *,
        status="completed",
        conclusion="success",
        run_attempt=1,
        run_started_at=None,
        pull_requests=None,
    ):
        if run_started_at is None:
            run_started_at = f"2026-08-31T10:00:{run_id:02d}Z"
        if pull_requests is None:
            pull_requests = [{
                "number": 73,
                "head": {"sha": COMMIT, "ref": "hotfix/9.5.3"},
                "base": {"sha": MAIN_SHA, "ref": "main"},
            }]
        return {
            "id": run_id,
            "event": "pull_request",
            "head_sha": COMMIT,
            "head_branch": "hotfix/9.5.3",
            "path": ".github/workflows/android-ci.yml",
            "status": status,
            "conclusion": conclusion,
            "run_attempt": run_attempt,
            "run_started_at": run_started_at,
            "pull_requests": pull_requests,
        }

    def test_newer_queued_run_blocks_an_older_success(self):
        pages = [{
            "workflow_runs": [
                self.workflow_run(10),
                self.workflow_run(11, status="queued", conclusion=None),
            ]
        }]
        with mock.patch(
            "tools.release.github_gate.rest_api",
            return_value=pages,
        ):
            with self.assertRaises(GateError):
                verify_android_ci(
                    REPOSITORY,
                    commit=COMMIT,
                    release_type="patch",
                    source_ref="hotfix/9.5.3",
                    main_sha=MAIN_SHA,
                    pull_request_number=73,
                )

    def test_queued_rerun_with_old_id_blocks_newer_id_success(self):
        pages = [{
            "workflow_runs": [
                self.workflow_run(
                    10,
                    status="in_progress",
                    conclusion=None,
                    run_attempt=2,
                    run_started_at="2026-08-31T11:00:00Z",
                ),
                self.workflow_run(
                    11,
                    run_started_at="2026-08-31T10:00:00Z",
                ),
            ]
        }]
        with mock.patch(
            "tools.release.github_gate.rest_api",
            return_value=pages,
        ):
            with self.assertRaises(GateError):
                verify_android_ci(
                    REPOSITORY,
                    commit=COMMIT,
                    release_type="patch",
                    source_ref="hotfix/9.5.3",
                    main_sha=MAIN_SHA,
                    pull_request_number=73,
                )

    def test_queued_run_with_empty_pr_association_still_blocks(self):
        pages = [{
            "workflow_runs": [
                self.workflow_run(10),
                self.workflow_run(
                    11,
                    status="queued",
                    conclusion=None,
                    pull_requests=[],
                ),
            ]
        }]
        with mock.patch(
            "tools.release.github_gate.rest_api",
            return_value=pages,
        ):
            with self.assertRaises(GateError):
                verify_android_ci(
                    REPOSITORY,
                    commit=COMMIT,
                    release_type="patch",
                    source_ref="hotfix/9.5.3",
                    main_sha=MAIN_SHA,
                    pull_request_number=73,
                )

    def test_successful_pr_run_requires_exact_build_job(self):
        run_pages = [{"workflow_runs": [self.workflow_run(12)]}]
        job_pages = [{
            "jobs": [{
                "name": "Build and lint",
                "head_sha": COMMIT,
                "status": "completed",
                "conclusion": "success",
            }]
        }]
        artifact_pages = [{
            "artifacts": [{
                "id": 1200,
                "name": f"h9-ci-provenance-{COMMIT}-{MAIN_SHA}-attempt-1",
                "expired": False,
                "size_in_bytes": 128,
            }]
        }]
        with mock.patch(
            "tools.release.github_gate.rest_api",
            side_effect=(run_pages, job_pages, artifact_pages),
        ):
            run_id = verify_android_ci(
                REPOSITORY,
                commit=COMMIT,
                release_type="patch",
                source_ref="hotfix/9.5.3",
                main_sha=MAIN_SHA,
                pull_request_number=73,
            )
        self.assertEqual(12, run_id)

    def test_successful_hotfix_run_for_another_main_sha_is_rejected(self):
        run_pages = [{"workflow_runs": [self.workflow_run(13)]}]
        job_pages = [{
            "jobs": [{
                "name": "Build and lint",
                "head_sha": COMMIT,
                "status": "completed",
                "conclusion": "success",
            }]
        }]
        artifact_pages = [{
            "artifacts": [{
                "id": 1300,
                "name": f"h9-ci-provenance-{COMMIT}-{'c' * 40}-attempt-1",
                "expired": False,
                "size_in_bytes": 128,
            }]
        }]
        with mock.patch(
            "tools.release.github_gate.rest_api",
            side_effect=(run_pages, job_pages, artifact_pages),
        ):
            with self.assertRaises(GateError):
                verify_android_ci(
                    REPOSITORY,
                    commit=COMMIT,
                    release_type="patch",
                    source_ref="hotfix/9.5.3",
                    main_sha=MAIN_SHA,
                    pull_request_number=73,
                )


class GraphqlSnapshotTest(unittest.TestCase):
    @staticmethod
    def snapshot(*, nodes, page_info, review_decision=None):
        return {
            "data": {
                "repository": {
                    "pullRequest": {
                        "state": "MERGED",
                        "isDraft": False,
                        "reviewDecision": review_decision,
                        "headRefOid": COMMIT,
                        "headRefName": "feature/release",
                        "baseRefName": "main",
                        "mergeCommit": {"oid": COMMIT},
                        "reviewThreads": {
                            "nodes": nodes,
                            "pageInfo": page_info,
                        },
                    }
                }
            }
        }

    def verify(self):
        return verify_pull_request_snapshot(
            REPOSITORY,
            pull_request={"number": 72},
            commit=COMMIT,
            release_type="minor",
            source_ref="main",
        )

    def test_review_thread_without_boolean_resolution_fails_closed(self):
        response = self.snapshot(
            nodes=[{}],
            page_info={"hasNextPage": False, "endCursor": None},
        )
        with mock.patch("tools.release.github_gate.gh_json", return_value=response):
            with self.assertRaises(GateError):
                self.verify()

    def test_pagination_without_boolean_has_next_page_fails_closed(self):
        response = self.snapshot(
            nodes=[{"isResolved": True}],
            page_info={"endCursor": None},
        )
        with mock.patch("tools.release.github_gate.gh_json", return_value=response):
            with self.assertRaises(GateError):
                self.verify()

    def test_changes_requested_review_blocks_release(self):
        response = self.snapshot(
            nodes=[],
            page_info={"hasNextPage": False, "endCursor": None},
            review_decision="CHANGES_REQUESTED",
        )
        with mock.patch("tools.release.github_gate.gh_json", return_value=response):
            with self.assertRaises(GateError):
                self.verify()


if __name__ == "__main__":
    unittest.main()
