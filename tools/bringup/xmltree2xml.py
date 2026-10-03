#!/usr/bin/env python3
#
# SPDX-FileCopyrightText: 2026 The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
#
"""Turn `aapt2 dump xmltree` output back into XML (elements, attributes, text).

usage: aapt2 dump xmltree --file res/xml/x.xml some.apk | xmltree2xml.py > x.xml
Good enough for simple resource XML such as power_profile.xml (no namespaces).
"""
import re
import sys
from xml.sax.saxutils import escape, quoteattr


def main():
    out = ['<?xml version="1.0" encoding="utf-8"?>']
    stack = []  # (indent, name, has_children_or_text)
    pending = None  # element whose start tag is not closed yet: [indent, name, attrs, text]

    def flush_start(close_now):
        nonlocal pending
        if pending is None:
            return
        ind, name, attrs, text = pending
        a = "".join(f" {k}={quoteattr(v)}" for k, v in attrs)
        pad = "    " * len(stack)
        if close_now:
            if text is not None:
                out.append(f"{pad}<{name}{a}>{escape(text)}</{name}>")
            else:
                out.append(f"{pad}<{name}{a} />")
        else:
            out.append(f"{pad}<{name}{a}>")
            stack.append((ind, name))
        pending = None

    for line in sys.stdin:
        line = line.rstrip("\n")
        m = re.match(r"^(\s*)E: (\S+)", line)
        if m:
            ind = len(m.group(1))
            if pending is not None:
                # next element deeper -> pending has children
                flush_start(close_now=ind <= pending[0])
            while stack and stack[-1][0] >= ind:
                _, name = stack.pop()
                out.append("    " * len(stack) + f"</{name}>")
            pending = [ind, m.group(2), [], None]
            continue
        m = re.match(r'^\s*A: (?:[\w:]+:)?(\w[\w.-]*)(?:\([^)]*\))?="(.*)" \(Raw: ".*"\)$', line) or \
            re.match(r'^\s*A: (?:[\w:]+:)?(\w[\w.-]*)(?:\([^)]*\))?=(\S+)', line)
        if m and pending is not None:
            pending[2].append((m.group(1), m.group(2)))
            continue
        m = re.match(r"^\s*T: '(.*)'$", line)
        if m and pending is not None:
            pending[3] = m.group(1)
            continue
    flush_start(close_now=True)
    while stack:
        _, name = stack.pop()
        out.append("    " * len(stack) + f"</{name}>")
    print("\n".join(out))


if __name__ == "__main__":
    main()
