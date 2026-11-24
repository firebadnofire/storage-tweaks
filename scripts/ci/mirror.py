"""Fast-forward the verified Forgejo main commit to GitHub."""
import subprocess

from release import sync_github


if __name__ == '__main__':
    sync_github(subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip())
