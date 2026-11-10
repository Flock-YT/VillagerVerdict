#!/usr/bin/env python3
"""Verify the installable Fabric artifact before publishing it."""

import argparse
import json
from zipfile import ZipFile


def verify(path, version):
    with ZipFile(path) as jar:
        if jar.testzip() is not None:
            raise ValueError("Corrupt release JAR")
        metadata = json.loads(jar.read("fabric.mod.json"))
        if (metadata.get("id") != "villagerverdict" or metadata.get("version") != version
                or metadata.get("environment") != "client"):
            raise ValueError("Unexpected Fabric release metadata")
        for environment in ("main", "client"):
            for entrypoint in metadata["entrypoints"][environment]:
                if entrypoint.replace(".", "/") + ".class" not in jar.namelist():
                    raise ValueError("Missing entrypoint: " + entrypoint)
        if any("gametest" in name.lower() or "/ClientIntegrationTest" in name
               for name in jar.namelist()):
            raise ValueError("Release JAR contains integration-test code")
    print("Verified " + str(path) + " (" + version + ")")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("jar")
    parser.add_argument("version")
    args = parser.parse_args()
    verify(args.jar, args.version)
