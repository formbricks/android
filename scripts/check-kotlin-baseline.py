#!/usr/bin/env python3
"""Fail when the SDK we publish needs a newer Kotlin than the baseline we support.

An app's Kotlin compiler reads the metadata stamped into our classes and into the Kotlin
libraries listed in our POM. It refuses anything more than one minor version newer than itself,
so an SDK built with Kotlin 2.4 stops every app on Kotlin 2.2 or older from compiling. Nothing
in a normal build shows that, which is how a routine Dependabot bump nearly shipped it.

Usage: check-kotlin-baseline.py <pom.xml> <release.aar>

To raise the baseline on purpose, change the constants below and say so in the release notes.
"""
import re
import struct
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

# Highest Kotlin metadata version our classes may carry (Kotlin 2.1).
BASELINE_METADATA = (2, 1, 0)

# Highest major.minor of each Kotlin library the POM may declare. Pinning a library here is a
# claim that its metadata is no newer than the baseline; check that before raising a ceiling.
POM_CEILINGS = {
    "org.jetbrains.kotlin:kotlin-stdlib": (2, 1),
    "org.jetbrains.kotlinx:kotlinx-serialization-json": (1, 8),
}


def parse_version(text):
    return tuple(int(part) for part in re.findall(r"\d+", text)[:3])


def check_pom(pom_path):
    problems = []
    namespace = {"m": "http://maven.apache.org/POM/4.0.0"}
    for dep in ET.parse(pom_path).getroot().findall(".//m:dependency", namespace):
        group = dep.findtext("m:groupId", namespaces=namespace)
        artifact = dep.findtext("m:artifactId", namespaces=namespace)
        version = dep.findtext("m:version", namespaces=namespace) or ""
        key = f"{group}:{artifact}"
        if not group.startswith("org.jetbrains.kotlin"):
            continue
        ceiling = POM_CEILINGS.get(key)
        if ceiling is None:
            problems.append(f"POM declares {key}:{version}, which has no ceiling in POM_CEILINGS")
        elif parse_version(version)[:2] > ceiling:
            problems.append(
                f"POM declares {key}:{version}, above the {'.'.join(map(str, ceiling))}.x ceiling"
            )
    return problems


def module_metadata_versions(classes_jar):
    """The metadata version in each META-INF/*.kotlin_module header: a count, then the ints."""
    found = {}
    with zipfile.ZipFile(classes_jar) as jar:
        for name in jar.namelist():
            if name.startswith("META-INF/") and name.endswith(".kotlin_module"):
                data = jar.read(name)
                (count,) = struct.unpack(">i", data[:4])
                found[name] = struct.unpack(f">{count}i", data[4 : 4 + 4 * count])
    return found


def class_metadata_versions(classes_jar):
    """The kotlin.Metadata `mv` of every class, read with one javap run over the whole jar."""
    with zipfile.ZipFile(classes_jar) as jar:
        names = [n[:-6].replace("/", ".") for n in jar.namelist() if n.endswith(".class")]
    out = subprocess.run(
        ["javap", "-v", "-p", "-cp", str(classes_jar), *names],
        capture_output=True, text=True, check=True,
    ).stdout
    found, current = {}, None
    for line in out.splitlines():
        header = re.match(r"Classfile .*!/(.+)\.class", line)
        if header:
            current = header.group(1)
        mv = re.match(r"\s+mv=\[(\d+),(\d+),(\d+)\]", line)
        if mv and current:
            found[current] = tuple(int(x) for x in mv.groups())
    return found


def check_aar(aar_path):
    with tempfile.TemporaryDirectory() as tmp:
        with zipfile.ZipFile(aar_path) as aar:
            classes_jar = aar.extract("classes.jar", tmp)
        modules = module_metadata_versions(classes_jar)
        classes = class_metadata_versions(classes_jar)
    if not modules or not classes:
        return ["found no Kotlin metadata in the AAR, so the check cannot vouch for it"]
    problems = [
        f"{name} is stamped {'.'.join(map(str, v))}"
        for name, v in modules.items()
        if v > BASELINE_METADATA
    ]
    too_new = sorted(name for name, v in classes.items() if v > BASELINE_METADATA)
    if too_new:
        problems.append(
            f"{len(too_new)} of {len(classes)} classes are stamped above "
            f"{'.'.join(map(str, BASELINE_METADATA))}, e.g. {', '.join(too_new[:3])}"
        )
    return problems


def main():
    pom_path, aar_path = (Path(arg) for arg in sys.argv[1:3])
    problems = check_pom(pom_path) + check_aar(aar_path)
    baseline = ".".join(map(str, BASELINE_METADATA[:2]))
    if problems:
        print(f"FAIL: the published SDK would not compile in an app on Kotlin {baseline}:")
        for problem in problems:
            print(f"  - {problem}")
        sys.exit(1)
    print(f"OK: the published SDK stays within the Kotlin {baseline} baseline.")


if __name__ == "__main__":
    main()
