import pathlib
import sys
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).parents[1]))
import artifact


class ArtifactNamesTest(unittest.TestCase):
    def test_release_names_are_version_scoped(self):
        self.assertEqual(artifact.names('0.0.1'), (
            'firebadnofire-storage-tweaks-0.0.1-mc1.12.2-forge.jar',
            'firebadnofire-storage-tweaks-0.0.1-mc1.12.2-forge-sources.jar'))

    def test_checksum_order_is_deterministic(self):
        self.assertEqual(artifact.checksums(pathlib.Path('.'), ()), '')


if __name__ == '__main__':
    unittest.main()
