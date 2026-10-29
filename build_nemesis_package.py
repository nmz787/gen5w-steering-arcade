#!/usr/bin/env python3
"""Build a NEMESIS ZIP from explicit package files and canonical app sources."""
import argparse
import configparser
import hashlib
import os
from pathlib import Path
import re
import subprocess
import zipfile

PROJECT = Path(__file__).resolve().parent
PACKAGE = PROJECT / 'packages/steering-landscape'
PACKAGE_FILES = ('NEMESIS.data', 'README.md', 'bin/launch.sh', 'scripts/common.sh',
                 'scripts/preinstall.sh', 'scripts/postinstall.sh', 'scripts/preuninstall.sh',
                 'home-tiles/tiles.json', 'home-tiles/icons/landscape-day.png',
                 'home-tiles/icons/landscape-night.png', 'home-tiles/icons/landscape-day.svg',
                 'home-tiles/icons/landscape-night.svg')
SOURCE_FILES = ('AndroidManifest.xml', 'MainActivity.java', 'VehicleReader.java',
                'ArtSurface.java', 'KartWorld.java', 'build.sh')
USB_PACKAGE_PATH = 'packages/steering-landscape-1.0.0.zip'


def write_zip(output, entries):
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name, data in sorted(entries.items()):
            info = zipfile.ZipInfo(name, (2020, 1, 1, 0, 0, 0))
            info.create_system = 3
            info.external_attr = (0o100755 if name.endswith('.sh') else 0o100644) << 16
            info.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(info, data)
    print(output, hashlib.sha256(output.read_bytes()).hexdigest())


def signer(apk, tool):
    result = subprocess.run([str(tool), 'verify', '--min-sdk-version', '19', '--print-certs', str(apk)],
                            check=True, capture_output=True, text=True)
    match = re.search(r'Signer #1 certificate SHA-256 digest: ([0-9a-fA-F]{64})', result.stdout)
    if not match:
        raise ValueError('Cannot determine APK signer: ' + str(apk))
    return match.group(1).lower()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, default=PROJECT / 'dist/steering-landscape-1.0.0.zip')
    parser.add_argument('--usb-output', type=Path, default=PROJECT / 'dist/steering-landscape-1.0.0-usb.zip',
                        help='ZIP to unpack at the root of a USB drive')
    parser.add_argument('--approved-legacy', type=Path, help='Exact previous APK permitted for replacement')
    args = parser.parse_args()
    output = args.output.resolve()
    usb_output = args.usb_output.resolve()
    if output == usb_output or output.is_relative_to(PROJECT / 'packages') or usb_output.is_relative_to(PROJECT / 'packages'):
        raise SystemExit('Outputs must be distinct and outside the package source tree')
    usb_descriptor = PROJECT / 'packages/usb/NEMESIS.data'
    descriptor_bytes = usb_descriptor.read_bytes()
    descriptor = configparser.ConfigParser(interpolation=None)
    descriptor.read_string(descriptor_bytes.decode('utf-8'))
    if descriptor.get('nemesis', 'type', fallback='') != 'install' or descriptor.get('nemesis', 'path', fallback='') != USB_PACKAGE_PATH:
        raise SystemExit('USB descriptor must install ' + USB_PACKAGE_PATH)
    apk = PROJECT / 'build/SteeringLandscape.apk'
    if not apk.is_file():
        raise SystemExit('Build the APK first with ./build.sh')
    sdk = Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT') or Path.home() / 'Android/Sdk')
    apksigner = sdk / 'build-tools' / os.environ.get('ANDROID_BUILD_TOOLS', '36.0.0') / 'apksigner'
    new_signer = signer(apk, apksigner)
    legacy = args.approved_legacy or PACKAGE / 'app/SteeringLandscape-approved-legacy.apk'
    if args.approved_legacy and not legacy.is_file():
        raise SystemExit('The supplied approved legacy APK does not exist')
    if legacy.is_file() and signer(legacy, apksigner) != new_signer:
        raise SystemExit('Approved legacy signer differs; preserve the original signing key')
    launcher = Path(os.environ.get('GEN5W_HOME_LAUNCHER') or
                    PROJECT.parents[2] / 'hyundai_ioniq5_projects/gen5w-home-launcher')
    tile_tools = launcher / 'tools/nemesis-tiles'
    if not (tile_tools / 'build.sh').is_file() or not (tile_tools / 'InstallTiles.java').is_file():
        raise SystemExit('Gen5W home launcher tile helper not found; set GEN5W_HOME_LAUNCHER to its checkout')
    tile_jar = PROJECT / 'build/home-tiles/installer.jar'
    tile_jar.parent.mkdir(parents=True, exist_ok=True)
    build_env = os.environ.copy()
    build_env['ANDROID_HOME'] = str(sdk)
    build_env['BUILD_TOOLS_VERSION'] = os.environ.get('ANDROID_BUILD_TOOLS', '36.0.0')
    subprocess.run(['sh', str(tile_tools / 'build.sh'), str(tile_jar)], check=True, env=build_env)
    entries = {}
    for name in PACKAGE_FILES:
        path = PACKAGE / name
        if path.is_symlink() or not path.is_file() or not path.resolve().is_relative_to(PACKAGE):
            raise SystemExit('Missing or unsafe package input: ' + str(path))
        entries[name] = path.read_bytes()
    for name in SOURCE_FILES:
        path = PROJECT / name
        if path.is_symlink() or not path.is_file() or path.resolve().parent != PROJECT:
            raise SystemExit('Missing or unsafe source: ' + str(path))
        entries['source/' + name] = path.read_bytes()
    entries['app/SteeringLandscape.apk'] = apk.read_bytes()
    entries['home-tiles/installer.jar'] = tile_jar.read_bytes()
    entries['source/nemesis-tiles/InstallTiles.java'] = (tile_tools / 'InstallTiles.java').read_bytes()
    entries['source/nemesis-tiles/build.sh'] = (tile_tools / 'build.sh').read_bytes()
    if legacy.is_file():
        entries['app/SteeringLandscape-approved-legacy.apk'] = legacy.read_bytes()
    entries['SHA256SUMS'] = ''.join(hashlib.sha256(data).hexdigest() + '  ' + name + '\n'
                                   for name, data in sorted(entries.items())).encode()
    write_zip(output, entries)
    write_zip(usb_output, {'NEMESIS.data': descriptor_bytes, USB_PACKAGE_PATH: output.read_bytes()})


if __name__ == '__main__':
    main()
