#!/usr/bin/env python3
"""Test install and removal hooks on a disposable filesystem."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

source = Path(__file__).resolve().parent / 'packages/steering-landscape'
with tempfile.TemporaryDirectory(prefix='landscape-package-test-') as tmp:
    root = Path(tmp)
    package = root / 'package'
    shutil.copytree(source, package)
    (package / 'app').mkdir()
    (package / 'app/SteeringLandscape.apk').write_bytes(b'new apk')
    (package / 'app/SteeringLandscape-approved-legacy.apk').write_bytes(b'legacy apk')
    target = root / 'system/app/SteeringLandscape.apk'
    target.parent.mkdir(parents=True)
    backup = root / 'backup'
    mounts = root / 'mounts'
    mounts.write_text('/dev/fake /system ext4 rw,nodev 0 0\n')
    commands = root / 'commands'
    commands.mkdir()
    getprop = commands / 'getprop'
    getprop.write_text("""#!/bin/sh
case "$1" in
ro.product.device) echo "${FAKE_PRODUCT:-wp_daudioplus_neev23se_us}" ;;
ro.build.fingerprint) echo 'ecohmc/wp_daudioplus_ecohmc_us/wp_daudioplus_ecohmc_us:4.4.2/KVT49L/eng.vmadmin.20260507.094910:user/dev-keys' ;;
sys.boot_completed) echo "${FAKE_BOOT:-0}" ;;
esac
""")
    getprop.chmod(0o755)
    common = package / 'scripts/common.sh'
    common.write_text(common.read_text().replace('/system/app/SteeringLandscape.apk', str(target))
                      .replace('/data/nemesis/backups/steering-landscape', str(backup))
                      .replace('/proc/mounts', str(mounts)))
    launcher_config = root / 'launcher/tiles.json'
    log = root / 'calls'
    fake_am = commands / 'am'
    fake_am.write_text('#!/bin/sh\necho "am $*" >> "$CALL_LOG"\nexit 0\n')
    fake_am.chmod(0o755)
    fake_pm = commands / 'pm'
    fake_pm.write_text('#!/bin/sh\necho package:/system/app/SteeringLandscape.apk\n')
    fake_pm.chmod(0o755)
    app_process = commands / 'app_process'
    app_process.write_text('#!/bin/sh\necho "$*" >> "$CALL_LOG"\nexit "${FAKE_TILE_RESULT:-0}"\n')
    app_process.chmod(0o755)
    for name in ('postinstall.sh', 'preuninstall.sh'):
        script = package / 'scripts' / name
        script.write_text(script.read_text().replace('/system/bin/am', str(fake_am))
                          .replace('/system/bin/pm', str(fake_pm))
                          .replace('/system/bin/app_process', str(app_process))
                          .replace('/data/local/gen5w-home/tiles.json', str(launcher_config)))
    env = os.environ.copy()
    env['PATH'] = str(commands) + os.pathsep + env['PATH']
    env['CALL_LOG'] = str(log)
    def run(name, success=True):
        result = subprocess.run(['sh', 'scripts/' + name + '.sh'], cwd=package, env=env,
                                capture_output=True, text=True)
        assert (result.returncode == 0) == success, (name, result.stdout, result.stderr)
    target.write_bytes(b'legacy apk')
    run('preinstall'); run('postinstall')
    assert target.read_bytes() == b'new apk'
    assert (backup / 'SteeringLandscape.previous.apk').read_bytes() == b'legacy apk'
    target.write_bytes(b'unrelated')
    run('preuninstall', False)
    assert target.read_bytes() == b'unrelated'
    target.write_bytes(b'new apk')
    run('preuninstall')
    assert target.read_bytes() == b'legacy apk'
    target.write_bytes(b'unrecognized')
    run('preinstall', False); run('postinstall', False)
    assert target.read_bytes() == b'unrecognized'
    target.unlink()
    run('preinstall'); run('postinstall'); run('preuninstall')
    assert not target.exists()
    env['FAKE_BOOT'] = '1'
    run('preinstall'); run('postinstall')
    assert 'InstallTiles' not in log.read_text()  # No launcher configuration.
    run('preuninstall')
    launcher_config.parent.mkdir()
    launcher_config.write_text('{}')
    run('preinstall'); run('postinstall')
    assert 'InstallTiles' in log.read_text() and 'steering_landscape' in log.read_text()
    assert 'am force-stop com.hkmc.system.app.homesetting' in log.read_text()
    assert 'am force-stop com.android.launcher3' in log.read_text()
    assert 'am start -a android.intent.action.MAIN -c android.intent.category.HOME -n com.android.launcher3/.view.Launcher' in log.read_text()
    run('preuninstall')
    env['FAKE_TILE_RESULT'] = '1'
    log.write_text('')
    run('preinstall'); run('postinstall')  # Tile failure must not abort installation.
    assert target.read_bytes() == b'new apk'
    assert 'am force-stop com.android.launcher3' not in log.read_text()
    run('preuninstall')
    env['FAKE_PRODUCT'] = 'other'
    run('preinstall', False); run('postinstall', False)
    assert not target.exists()
print('Hook tests passed: APK lifecycle, target checks and optional tile integration')
