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


if __name__ == '__main__':
    unittest.main()
