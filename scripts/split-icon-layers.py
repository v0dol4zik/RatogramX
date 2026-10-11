#!/usr/bin/env python3
"""Splits a Ratogram X launcher icon SVG into adaptive icon layers.

Source icons are exported with a single clip group: everything before the first
<mask> is the full-bleed background, the <mask> and the masked group after it
are the glyph.
"""

import copy
import sys
import xml.etree.ElementTree as ET

SVG_NS = 'http://www.w3.org/2000/svg'


def main():
    if len(sys.argv) != 4:
        print(f'Usage: {sys.argv[0]} icon.svg background.svg foreground.svg', file=sys.stderr)
        return 2
    source_path, background_path, foreground_path = sys.argv[1:]
    ET.register_namespace('', SVG_NS)
    root = ET.parse(source_path).getroot()
    group = root.find(f'{{{SVG_NS}}}g')
    if group is None:
        print(f'{source_path}: missing clip group', file=sys.stderr)
        return 1
    children = list(group)
    split_index = next((index for index, child in enumerate(children) if child.tag == f'{{{SVG_NS}}}mask'), None)
    if not split_index:
        print(f'{source_path}: expected background elements followed by a glyph <mask>', file=sys.stderr)
        return 1

    background = copy.deepcopy(root)
    foreground = copy.deepcopy(root)
    background_group = background.find(f'{{{SVG_NS}}}g')
    foreground_group = foreground.find(f'{{{SVG_NS}}}g')
    for child in list(background_group)[split_index:]:
        background_group.remove(child)
    for child in list(foreground_group)[:split_index]:
        foreground_group.remove(child)

    ET.ElementTree(background).write(background_path)
    ET.ElementTree(foreground).write(foreground_path)
    return 0


if __name__ == '__main__':
    sys.exit(main())
