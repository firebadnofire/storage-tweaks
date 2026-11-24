import os
import pathlib
import sys
import unittest
from unittest.mock import patch

sys.path.insert(0, str(pathlib.Path(__file__).parents[1]))
import release


class FakeApi:
    def __init__(self, assets):
        self.assets = assets

    def repo(self, method, path):
        self.path = path
        return self.assets


class ReleaseGuardsTest(unittest.TestCase):
    def test_manual_recovery_requires_a_safe_tag(self):
        with patch.dict(os.environ, {'GITHUB_EVENT_NAME': 'workflow_dispatch', 'RELEASE_TAG': 'v0.0.1'}, clear=True):
            self.assertEqual(release.release_tag(), 'v0.0.1')
        with patch.dict(os.environ, {'GITHUB_EVENT_NAME': 'workflow_dispatch', 'RELEASE_TAG': '../v0.0.1'}, clear=True):
            with self.assertRaises(RuntimeError):
                release.release_tag()

    def test_asset_check_requires_complete_inventory_and_sizes(self):
        expected = {'mod.jar': b'jar', 'mod.jar.asc': b'signature'}
        api = FakeApi([
            {'name': 'mod.jar', 'type': 'attachment', 'size': 3},
            {'name': 'mod.jar.asc', 'type': 'attachment', 'size': 9},
        ])
        release.check_assets(api, {'id': 42}, expected)
        self.assertEqual(api.path, '/releases/42/assets?per_page=100&limit=100')
        api.assets[1]['size'] = 8
        with self.assertRaises(RuntimeError):
            release.check_assets(api, {'id': 42}, expected)

    @patch('release.subprocess.run')
    @patch('release.subprocess.check_output')
    @patch('release.github_request')
    def test_github_mirror_only_fast_forwards_main(self, request, check_output, run):
        check_output.return_value = 'ssh://git@pubcode-ssh.archuser.org:2222/firebadnofire/storage-tweaks\n'
        request.side_effect = [
            {'full_name': 'firebadnofire/storage-tweaks', 'private': False, 'fork': False,
             'default_branch': 'main'},
            {'commit': {'sha': 'a' * 40}},
            {'sha': 'b' * 40},
        ]
        with patch.dict(os.environ, {'GH_KEY': 'test-token'}, clear=False):
            release.sync_github('b' * 40)
        commands = [call.args[0] for call in run.call_args_list]
        self.assertIn(['git', 'merge-base', '--is-ancestor', 'a' * 40, 'b' * 40], commands)
        self.assertTrue(any(command[-1] == 'b' * 40 + ':refs/heads/main' for command in commands))


if __name__ == '__main__':
    unittest.main()
