#!/usr/bin/env python3
"""Detect version changes using the same push/manual gate as ElytraPlus and WorldSafe."""

import argparse
import os
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parent.parent


def property_value(text, name):
    values = re.findall(r"^[ \t]*" + re.escape(name) + r"[ \t]*=([^\r\n]*)", text, re.MULTILINE)
    if len(values) != 1:
        raise ValueError("Expected exactly one " + name + "= entry in gradle.properties")
    return values[0].strip()


def validate_version(version):
    if not re.fullmatch(r"(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(?:-[0-9A-Za-z]+(?:[.-][0-9A-Za-z]+)*)?", version):
        raise ValueError("Expected a release version such as 1.0.0 or 1.1.0-beta.1")
    return version


def git(root, *arguments):
    return subprocess.run(["git", "-C", str(root), *arguments], check=True,
                          stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True).stdout


def detect(root, event, before, ref):
    if ref != "refs/heads/main":
        raise ValueError("Releases must run from the main branch")
    properties = (Path(root) / "gradle.properties").read_text(encoding="utf-8")
    version = validate_version(property_value(properties, "mod_version"))
    archive = property_value(properties, "archives_base_name")
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_-]*", archive):
        raise ValueError("Invalid archives_base_name")
    if event == "workflow_dispatch":
        previous, release = "<manual>", True
    elif event == "push":
        if not re.fullmatch(r"[0-9a-fA-F]{40}|[0-9a-fA-F]{64}", before):
            raise ValueError("Invalid previous commit")
        if set(before) == {"0"}:
            previous, release = "<none>", True
        else:
            # Missing files allow an initial release; invalid/missing commits fail closed.
            entries = git(root, "ls-tree", "--name-only", before, "--", "gradle.properties").splitlines()
            previous = (property_value(git(root, "show", before + ":gradle.properties"), "mod_version")
                        if "gradle.properties" in entries else "<none>")
            release = version != previous
    else:
        raise ValueError("Unsupported release event: " + event)
    return {"should_release": str(release).lower(), "version": version,
            "tag": "v" + version, "artifact": archive + "-" + version + ".jar"}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT)
    args = parser.parse_args()
    try:
        result = detect(args.root, os.environ.get("EVENT_NAME", ""),
                        os.environ.get("BEFORE_SHA", ""), os.environ.get("GITHUB_REF", ""))
        output = "".join(key + "=" + value + "\n" for key, value in result.items())
        print(output, end="")
        if os.environ.get("GITHUB_OUTPUT"):
            with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as file:
                file.write(output)
    except (OSError, ValueError, subprocess.CalledProcessError) as error:
        parser.exit(1, str(error) + "\n")


if __name__ == "__main__":
    main()
